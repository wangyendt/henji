package com.qingheng.weight.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalFeatureAvailabilityApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.qingheng.weight.data.AppRepository
import com.qingheng.weight.data.DailyWellnessRecord
import com.qingheng.weight.data.HealthConnectMeasurement
import com.qingheng.weight.data.UserProfile
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

data class HealthPermissionState(
    val available: Boolean,
    val weightGranted: Boolean,
    val sleepGranted: Boolean,
    val stepsGranted: Boolean,
    val exerciseGranted: Boolean,
    val activeCaloriesGranted: Boolean,
    val backgroundAvailable: Boolean,
    val backgroundGranted: Boolean,
) {
    val coreGranted: Boolean get() = weightGranted
    val activityGranted: Boolean get() = stepsGranted || exerciseGranted || activeCaloriesGranted
    val anyHealthGranted: Boolean get() = weightGranted || sleepGranted || activityGranted
}

data class HealthSyncResult(
    val recordsRead: Int,
    val recordsChanged: Int,
    val wellnessDaysChanged: Int,
)

class HealthConnectSync(context: Context, private val repository: AppRepository) {
    private val appContext = context.applicationContext
    private val available: Boolean
        get() = HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient
        get() = HealthConnectClient.getOrCreate(appContext)

    private val weightPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(BodyWaterMassRecord::class),
        HealthPermission.getReadPermission(BoneMassRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
    )
    private val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
    private val activityPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    val corePermissions: Set<String> = weightPermissions + sleepPermission + activityPermissions

    fun permissionsToRequest(): Set<String> {
        if (!available) return emptySet()
        return if (backgroundReadAvailable()) {
            corePermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        } else corePermissions
    }

    suspend fun permissionState(): HealthPermissionState {
        if (!available) return HealthPermissionState(false, false, false, false, false, false, false, false)
        val granted = client.permissionController.getGrantedPermissions()
        val backgroundAvailable = backgroundReadAvailable()
        return HealthPermissionState(
            available = true,
            weightGranted = weightPermissions.all { it in granted },
            sleepGranted = sleepPermission in granted,
            stepsGranted = HealthPermission.getReadPermission(StepsRecord::class) in granted,
            exerciseGranted = HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted,
            activeCaloriesGranted = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted,
            backgroundAvailable = backgroundAvailable,
            backgroundGranted = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted,
        )
    }

    suspend fun sync(profile: UserProfile, days: Long = 30): HealthSyncResult {
        val permission = permissionState()
        check(permission.anyHealthGranted) { "衡迹还没有 Health Connect 读取权限" }
        val end = Instant.now().plusSeconds(60)
        val start = end.minus(Duration.ofDays(days.coerceIn(2, 30)))
        var recordsRead = 0
        var weightChanged = 0

        if (permission.weightGranted) {
            val weights = readAll<WeightRecord>(start, end, setOf(DataOrigin(FITDAYS_PACKAGE)))
            val bodyFat = readAll<BodyFatRecord>(start, end, setOf(DataOrigin(FITDAYS_PACKAGE)))
            val bodyWater = readAll<BodyWaterMassRecord>(start, end, setOf(DataOrigin(FITDAYS_PACKAGE)))
            val boneMass = readAll<BoneMassRecord>(start, end, setOf(DataOrigin(FITDAYS_PACKAGE)))
            val leanMass = readAll<LeanBodyMassRecord>(start, end, setOf(DataOrigin(FITDAYS_PACKAGE)))
            val basalMetabolicRate = readAll<BasalMetabolicRateRecord>(start, end, setOf(DataOrigin(FITDAYS_PACKAGE)))
            recordsRead += weights.size
            weights.forEach { weight ->
                val weightKg = weight.weight.inKilograms
                val fat = nearest(weight.time, bodyFat) { it.time }?.percentage?.value
                val waterKg = nearest(weight.time, bodyWater) { it.time }?.mass?.inKilograms
                val heightMetres = profile.heightCm / 100.0
                val measurement = HealthConnectMeasurement(
                    healthConnectId = weight.metadata.id,
                    measuredAt = weight.time.toEpochMilli(),
                    weightKg = weightKg,
                    bmi = if (heightMetres > 0) weightKg / (heightMetres * heightMetres) else null,
                    bodyFatPercent = fat,
                    bodyWaterPercent = waterKg?.let { it / weightKg * 100.0 },
                    bmrKcal = nearest(weight.time, basalMetabolicRate) { it.time }
                        ?.basalMetabolicRate?.inKilocaloriesPerDay?.roundToInt(),
                    fatFreeMassKg = nearest(weight.time, leanMass) { it.time }?.mass?.inKilograms,
                    boneMassKg = nearest(weight.time, boneMass) { it.time }?.mass?.inKilograms,
                )
                if (repository.saveHealthConnectMeasurement(measurement)) weightChanged++
            }
        }

        val wellness = readDailyWellness(start, end, permission)
        recordsRead += wellness.sourceRecordCount
        if (wellness.records.isNotEmpty()) repository.saveWellness(wellness.records)
        return HealthSyncResult(recordsRead, weightChanged, wellness.records.size)
    }

    private suspend fun readDailyWellness(
        start: Instant,
        end: Instant,
        permission: HealthPermissionState,
    ): WellnessReadResult {
        val zone = ZoneId.systemDefault()
        val firstDay = start.atZone(zone).toLocalDate()
        val lastDay = end.atZone(zone).toLocalDate()
        val sleep = if (permission.sleepGranted) readAll<SleepSessionRecord>(start.minus(Duration.ofHours(12)), end) else emptyList()
        val exercises = if (permission.exerciseGranted) readAll<ExerciseSessionRecord>(start, end) else emptyList()
        val records = mutableListOf<DailyWellnessRecord>()

        generateSequence(firstDay) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .forEach { date ->
                val dayStart = date.atStartOfDay(zone).toInstant()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
                val sleepWindowStart = date.minusDays(1).atTime(12, 0).atZone(zone).toInstant()
                val sleepWindowEnd = date.atTime(12, 0).atZone(zone).toInstant()
                val nightlySleep = sleep.filter { it.endTime > sleepWindowStart && it.endTime <= sleepWindowEnd }
                val dayExercises = exercises.filter { it.startTime < dayEnd && it.endTime > dayStart }
                val sleepMinutes = if (permission.sleepGranted) aggregateSleepMinutes(sleepWindowStart, sleepWindowEnd) else null
                val deepMinutes = nightlySleep.sumOfStages(SleepSessionRecord.STAGE_TYPE_DEEP)
                val remMinutes = nightlySleep.sumOfStages(SleepSessionRecord.STAGE_TYPE_REM)
                val steps = if (permission.stepsGranted) aggregateSteps(dayStart, dayEnd) else null
                val activeCalories = if (permission.activeCaloriesGranted) aggregateCalories(dayStart, dayEnd) else null
                val exerciseMinutes = if (permission.exerciseGranted) aggregateExerciseMinutes(dayStart, dayEnd) else null
                val exerciseNames = dayExercises.map { exerciseTypeName(it.exerciseType) }.distinct()
                if (sleepMinutes != null || steps != null || exerciseMinutes != null || activeCalories != null) {
                    records += DailyWellnessRecord(
                        dateEpochDay = date.toEpochDay(),
                        sleepMinutes = sleepMinutes,
                        deepSleepMinutes = deepMinutes,
                        remSleepMinutes = remMinutes,
                        steps = steps,
                        exerciseMinutes = exerciseMinutes,
                        activeCaloriesKcal = activeCalories,
                        exerciseTypes = exerciseNames.joinToString("、"),
                        sleepSource = nightlySleep.firstOrNull()?.metadata?.dataOrigin?.packageName,
                        activitySource = dayExercises.firstOrNull()?.metadata?.dataOrigin?.packageName,
                        syncedAt = System.currentTimeMillis(),
                    )
                }
            }
        return WellnessReadResult(records, sleep.size + exercises.size)
    }

    private suspend fun aggregateSteps(start: Instant, end: Instant): Long? = runCatching {
        client.aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end))
        )[StepsRecord.COUNT_TOTAL]
    }.getOrNull()

    private suspend fun aggregateSleepMinutes(start: Instant, end: Instant): Int? = runCatching {
        client.aggregate(
            AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), TimeRangeFilter.between(start, end))
        )[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.toIntOrNull()
    }.getOrNull()

    private suspend fun aggregateExerciseMinutes(start: Instant, end: Instant): Int? = runCatching {
        client.aggregate(
            AggregateRequest(setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL), TimeRangeFilter.between(start, end))
        )[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes()?.toIntOrNull()
    }.getOrNull()

    private suspend fun aggregateCalories(start: Instant, end: Instant): Int? = runCatching {
        client.aggregate(
            AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), TimeRangeFilter.between(start, end))
        )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.roundToInt()
    }.getOrNull()

    @OptIn(ExperimentalFeatureAvailabilityApi::class)
    private fun backgroundReadAvailable(): Boolean =
        available && client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    private suspend inline fun <reified T : Record> readAll(
        start: Instant,
        end: Instant,
        origins: Set<DataOrigin> = emptySet(),
    ): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = origins,
                    ascendingOrder = true,
                    pageSize = 500,
                    pageToken = pageToken,
                )
            )
            records += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return records
    }

    private fun <T> nearest(target: Instant, records: List<T>, time: (T) -> Instant): T? = records
        .map { it to abs(Duration.between(target, time(it)).toMillis()) }
        .filter { (_, difference) -> difference <= MATCH_WINDOW_MILLIS }
        .minByOrNull { (_, difference) -> difference }
        ?.first

    companion object {
        private const val FITDAYS_PACKAGE = "cn.fitdays.fitdays"
        private const val MATCH_WINDOW_MILLIS = 2 * 60_000L
    }
}

private data class WellnessReadResult(val records: List<DailyWellnessRecord>, val sourceRecordCount: Int)

private fun Long.toIntOrNull(): Int? = if (this > 0L) coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else null

private fun List<SleepSessionRecord>.sumOfStages(stageType: Int): Int? = sumOf { session ->
    session.stages.filter { it.stage == stageType }.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
}.toIntOrNull()

internal fun exerciseTypeName(type: Int): String = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "步行"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "跑步"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "骑行"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "游泳"
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "力量训练"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "徒步"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "瑜伽"
    ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "羽毛球"
    ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "篮球"
    ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS -> "乒乓球"
    else -> "运动"
}
