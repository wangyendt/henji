package com.qingheng.weight.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalFeatureAvailabilityApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.qingheng.weight.data.AppRepository
import com.qingheng.weight.data.HealthConnectMeasurement
import com.qingheng.weight.data.UserProfile
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

data class HealthPermissionState(
    val available: Boolean,
    val coreGranted: Boolean,
    val backgroundAvailable: Boolean,
    val backgroundGranted: Boolean,
)

data class HealthSyncResult(val recordsRead: Int, val recordsChanged: Int)

class HealthConnectSync(context: Context, private val repository: AppRepository) {
    private val appContext = context.applicationContext
    private val available: Boolean
        get() = HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient
        get() = HealthConnectClient.getOrCreate(appContext)

    val corePermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(BodyWaterMassRecord::class),
        HealthPermission.getReadPermission(BoneMassRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
    )

    fun permissionsToRequest(): Set<String> {
        if (!available) return emptySet()
        return if (backgroundReadAvailable()) {
            corePermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        } else corePermissions
    }

    suspend fun permissionState(): HealthPermissionState {
        if (!available) return HealthPermissionState(false, false, false, false)
        val granted = client.permissionController.getGrantedPermissions()
        val backgroundAvailable = backgroundReadAvailable()
        return HealthPermissionState(
            available = true,
            coreGranted = granted.containsAll(corePermissions),
            backgroundAvailable = backgroundAvailable,
            backgroundGranted = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted,
        )
    }

    suspend fun sync(profile: UserProfile): HealthSyncResult {
        val permission = permissionState()
        check(permission.coreGranted) { "衡迹还没有 Health Connect 读取权限" }
        val end = Instant.now().plusSeconds(60)
        val start = end.minus(Duration.ofDays(30))
        val weights = readAll<WeightRecord>(start, end)
        val bodyFat = readAll<BodyFatRecord>(start, end)
        val bodyWater = readAll<BodyWaterMassRecord>(start, end)
        val boneMass = readAll<BoneMassRecord>(start, end)
        val leanMass = readAll<LeanBodyMassRecord>(start, end)
        val basalMetabolicRate = readAll<BasalMetabolicRateRecord>(start, end)
        var changed = 0
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
            if (repository.saveHealthConnectMeasurement(measurement)) changed++
        }
        return HealthSyncResult(weights.size, changed)
    }

    @OptIn(ExperimentalFeatureAvailabilityApi::class)
    private fun backgroundReadAvailable(): Boolean =
        available && client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    private suspend inline fun <reified T : Record> readAll(start: Instant, end: Instant): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = setOf(DataOrigin(FITDAYS_PACKAGE)),
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
