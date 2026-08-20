package com.qingheng.weight.data

import androidx.room.withTransaction
import com.qingheng.weight.sync.SyncPayloadCodec
import com.qingheng.weight.sync.SyncIdentity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AppRepository(
    private val weights: WeightDao,
    private val meals: MealDao,
    private val sync: SyncDao? = null,
    private val database: AppDatabase? = null,
    private val workouts: WorkoutDao? = null,
) {
    val weightRecords: Flow<List<WeightRecord>> = weights.observeAll()
    val mealRecords: Flow<List<MealRecord>> = meals.observeAll()
    val mealFoodItems: Flow<List<MealFoodItem>> = meals.observeAllFoodItems()
    val foodFrequencies: Flow<List<FoodMealFrequency>> = meals.observeFoodFrequencies()
    val workoutRecords: Flow<List<WorkoutRecord>> = workouts?.observeAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    private suspend fun saveManualMeasurement(metrics: BodyMetrics) {
        val record = WeightRecord(
                id = UUID.randomUUID().toString(), measuredAt = System.currentTimeMillis(),
                source = "manual", deviceName = null, weightKg = metrics.weightKg,
                impedanceOhm = metrics.impedanceOhm, bmi = metrics.bmi,
                bodyFatPercent = metrics.bodyFatPercent, bodyWaterPercent = metrics.bodyWaterPercent,
                skeletalMusclePercent = metrics.skeletalMusclePercent, bmrKcal = metrics.bmrKcal,
                fatFreeMassKg = metrics.fatFreeMassKg, subcutaneousFatPercent = metrics.subcutaneousFatPercent,
                visceralFat = metrics.visceralFat, muscleMassKg = metrics.muscleMassKg,
                boneMassKg = metrics.boneMassKg, proteinPercent = metrics.proteinPercent,
                bodyAge = metrics.bodyAge, isEstimated = metrics.isEstimated,
                rawPacketHex = metrics.rawPacketHex,
            )
        inTransaction {
            weights.insert(record)
            enqueue(record)
        }
    }

    suspend fun saveManualWeight(weightKg: Double, profile: UserProfile) {
        saveManualMeasurement(BodyCompositionCalculator.estimate(weightKg, null, profile))
    }

    suspend fun importFitdaysHistory(parsed: FitdaysParseResult): FitdaysImportSummary {
        var existingCount = 0
        var acceptedCount = 0
        parsed.records.chunked(500).forEach { chunk ->
            inTransaction {
                val accepted = if (sync == null) chunk else chunk.filter {
                    sync.tombstone(SyncPayloadCodec.WEIGHT, it.id) == null &&
                        sync.tombstoneByDedupeKey(SyncPayloadCodec.WEIGHT, SyncIdentity.weight(it)) == null
                }
                existingCount += weights.existingIds(accepted.map(WeightRecord::id)).size
                weights.insert(accepted)
                accepted.forEach { enqueue(it) }
                acceptedCount += accepted.size
            }
        }
        return FitdaysImportSummary(
            total = parsed.records.size,
            added = acceptedCount - existingCount,
            updated = existingCount,
            skipped = parsed.skipped + (parsed.records.size - acceptedCount),
        )
    }

    suspend fun saveHealthConnectMeasurement(measurement: HealthConnectMeasurement): Boolean {
        val deterministicId = "health-connect-${measurement.healthConnectId}"
        return inTransaction {
            val measurementDedupeKey = SyncIdentity.weight(measurement.measuredAt, measurement.weightKg)
            if (sync?.tombstone(SyncPayloadCodec.WEIGHT, deterministicId) != null ||
                sync?.tombstoneByDedupeKey(SyncPayloadCodec.WEIGHT, measurementDedupeKey) != null
            ) return@inTransaction false
            val existing = weights.findById(deterministicId) ?: weights.findNearest(
                weightKg = measurement.weightKg,
                measuredAt = measurement.measuredAt,
                from = measurement.measuredAt - 2 * 60_000L,
                to = measurement.measuredAt + 2 * 60_000L,
            )
            if (existing != null && (
                    sync?.tombstone(SyncPayloadCodec.WEIGHT, existing.id) != null ||
                        sync?.tombstoneByDedupeKey(SyncPayloadCodec.WEIGHT, SyncIdentity.weight(existing)) != null
                )
            ) {
                return@inTransaction false
            }
            val record = WeightRecord(
                id = existing?.id ?: deterministicId,
                measuredAt = measurement.measuredAt,
                source = if (existing?.source?.startsWith("fitdays_import") == true) "fitdays_import+health_connect" else "health_connect_fitdays",
                deviceName = "Fitdays · Health Connect",
                weightKg = measurement.weightKg,
                impedanceOhm = existing?.impedanceOhm,
                bmi = measurement.bmi ?: existing?.bmi,
                bodyFatPercent = measurement.bodyFatPercent ?: existing?.bodyFatPercent,
                bodyWaterPercent = measurement.bodyWaterPercent ?: existing?.bodyWaterPercent,
                skeletalMusclePercent = existing?.skeletalMusclePercent,
                bmrKcal = measurement.bmrKcal ?: existing?.bmrKcal,
                fatFreeMassKg = measurement.fatFreeMassKg ?: existing?.fatFreeMassKg,
                subcutaneousFatPercent = existing?.subcutaneousFatPercent,
                visceralFat = existing?.visceralFat,
                muscleMassKg = existing?.muscleMassKg,
                boneMassKg = measurement.boneMassKg ?: existing?.boneMassKg,
                proteinPercent = existing?.proteinPercent,
                bodyAge = existing?.bodyAge,
                isEstimated = measurement.bodyFatPercent == null && existing?.bodyFatPercent == null,
                rawPacketHex = existing?.rawPacketHex,
            )
            if (record == existing) return@inTransaction false
            weights.insert(record)
            enqueue(record)
            true
        }
    }

    suspend fun saveMeal(record: MealRecord, foodItems: List<MealFoodItem>) = inTransaction {
        meals.insert(record, foodItems)
        enqueue(record, foodItems)
    }

    suspend fun saveWorkoutRecords(records: List<WorkoutRecord>): WorkoutImportSummary {
        val dao = requireNotNull(workouts) { "运动记录存储尚未初始化" }
        var added = 0
        var updated = 0
        inTransaction {
            records.forEach { incoming ->
                if (sync?.tombstone(SyncPayloadCodec.WORKOUT, incoming.id) != null) return@forEach
                val existing = dao.findById(incoming.id)
                val merged = existing?.merge(incoming) ?: incoming
                if (existing == null) added++ else if (existing != merged) updated++
                if (existing != merged) {
                    dao.insert(merged)
                    enqueue(merged)
                }
            }
        }
        return WorkoutImportSummary(added, updated, records.size - added - updated)
    }

    suspend fun deleteWeight(record: WeightRecord) = inTransaction {
        weights.delete(record)
        tombstoneAndEnqueue(SyncPayloadCodec.WEIGHT, record.id, SyncIdentity.weight(record))
    }

    suspend fun deleteMeal(record: MealRecord) = inTransaction {
        meals.delete(record)
        tombstoneAndEnqueue(SyncPayloadCodec.MEAL, record.id)
    }

    suspend fun deleteMeals(records: List<MealRecord>) = inTransaction {
        meals.delete(records)
        records.forEach { tombstoneAndEnqueue(SyncPayloadCodec.MEAL, it.id) }
    }

    suspend fun deleteWorkout(record: WorkoutRecord) = inTransaction {
        workouts?.delete(record)
        tombstoneAndEnqueue(SyncPayloadCodec.WORKOUT, record.id, SyncIdentity.workout(record))
    }

    private suspend fun enqueue(record: WeightRecord) {
        sync?.enqueue(
            SyncOutboxEvent(
                eventId = UUID.randomUUID().toString(),
                entityType = SyncPayloadCodec.WEIGHT,
                entityId = record.id,
                dedupeKey = SyncIdentity.weight(record),
                operation = "upsert",
                occurredAt = System.currentTimeMillis(),
                payloadJson = SyncPayloadCodec.encode(record),
            ),
        )
    }

    private suspend fun enqueue(record: MealRecord, foodItems: List<MealFoodItem>) {
        sync?.enqueue(
            SyncOutboxEvent(
                eventId = UUID.randomUUID().toString(),
                entityType = SyncPayloadCodec.MEAL,
                entityId = record.id,
                operation = "upsert",
                occurredAt = System.currentTimeMillis(),
                payloadJson = SyncPayloadCodec.encode(record, foodItems),
            ),
        )
    }

    private suspend fun enqueue(record: WorkoutRecord) {
        sync?.enqueue(
            SyncOutboxEvent(
                eventId = UUID.randomUUID().toString(),
                entityType = SyncPayloadCodec.WORKOUT,
                entityId = record.id,
                dedupeKey = SyncIdentity.workout(record),
                operation = "upsert",
                occurredAt = System.currentTimeMillis(),
                payloadJson = SyncPayloadCodec.encode(record),
            ),
        )
    }

    private suspend fun tombstoneAndEnqueue(entityType: String, entityId: String, dedupeKey: String? = null) {
        val dao = sync ?: return
        val deletedAt = System.currentTimeMillis()
        dao.putTombstone(SyncTombstone(entityType, entityId, dedupeKey, deletedAt))
        dao.enqueue(
            SyncOutboxEvent(
                eventId = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                dedupeKey = dedupeKey,
                operation = "delete",
                occurredAt = deletedAt,
                payloadJson = "{}",
            ),
        )
    }

    private suspend fun <T> inTransaction(block: suspend () -> T): T =
        database?.withTransaction { block() } ?: block()
}

private fun WorkoutRecord.merge(incoming: WorkoutRecord): WorkoutRecord = copy(
    updatedAt = maxOf(updatedAt, incoming.updatedAt),
    workoutType = incoming.workoutType,
    workoutCategory = incoming.workoutCategory,
    durationSeconds = incoming.durationSeconds ?: durationSeconds,
    distanceMeters = incoming.distanceMeters ?: distanceMeters,
    caloriesKcal = incoming.caloriesKcal ?: caloriesKcal,
    averageHeartRateBpm = incoming.averageHeartRateBpm ?: averageHeartRateBpm,
    maximumHeartRateBpm = incoming.maximumHeartRateBpm ?: maximumHeartRateBpm,
    averagePaceSecondsPerKm = incoming.averagePaceSecondsPerKm ?: averagePaceSecondsPerKm,
    averagePaceSecondsPer100Meters = incoming.averagePaceSecondsPer100Meters ?: averagePaceSecondsPer100Meters,
    averageCadencePerMinute = incoming.averageCadencePerMinute ?: averageCadencePerMinute,
    steps = incoming.steps ?: steps,
    averageStrideCentimeters = incoming.averageStrideCentimeters ?: averageStrideCentimeters,
    elevationGainMeters = incoming.elevationGainMeters ?: elevationGainMeters,
    poolLengthMeters = incoming.poolLengthMeters ?: poolLengthMeters,
    lengths = incoming.lengths ?: lengths,
    strokes = incoming.strokes ?: strokes,
    averageSwolf = incoming.averageSwolf ?: averageSwolf,
    averageStrokeRatePerMinute = incoming.averageStrokeRatePerMinute ?: averageStrokeRatePerMinute,
    mainStroke = incoming.mainStroke ?: mainStroke,
    confidence = maxOf(confidence, incoming.confidence),
    rawAnalysis = incoming.rawAnalysis ?: rawAnalysis,
)

data class WorkoutImportSummary(
    val added: Int,
    val updated: Int,
    val unchanged: Int,
)

data class HealthConnectMeasurement(
    val healthConnectId: String,
    val measuredAt: Long,
    val weightKg: Double,
    val bmi: Double?,
    val bodyFatPercent: Double?,
    val bodyWaterPercent: Double?,
    val bmrKcal: Int?,
    val fatFreeMassKg: Double?,
    val boneMassKg: Double?,
)
