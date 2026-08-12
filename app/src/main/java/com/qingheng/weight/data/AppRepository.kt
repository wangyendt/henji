package com.qingheng.weight.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AppRepository(private val weights: WeightDao, private val meals: MealDao) {
    val weightRecords: Flow<List<WeightRecord>> = weights.observeAll()
    val mealRecords: Flow<List<MealRecord>> = meals.observeAll()

    suspend fun saveMeasurement(metrics: BodyMetrics, deviceName: String?, source: String = "bluetooth") {
        val now = System.currentTimeMillis()
        val recent = if (source == "bluetooth") {
            weights.recentBluetoothMeasurements(metrics.weightKg, now - 10 * 60_000L)
        } else emptyList()
        // A stable ICOMON scale repeats the same A2 frame with a changing sequence byte. Keep one
        // record for that weighing and upgrade it when the later impedance/A3 result arrives.
        val existing = recent.firstOrNull()
        if (existing?.isEstimated == false && metrics.isEstimated) {
            if (recent.size > 1) weights.delete(recent.drop(1))
            return
        }
        if (recent.size > 1) weights.delete(recent.drop(1))
        weights.insert(
            WeightRecord(
                id = existing?.id ?: UUID.randomUUID().toString(), measuredAt = existing?.measuredAt ?: now,
                source = source, deviceName = deviceName, weightKg = metrics.weightKg,
                impedanceOhm = metrics.impedanceOhm, bmi = metrics.bmi,
                bodyFatPercent = metrics.bodyFatPercent, bodyWaterPercent = metrics.bodyWaterPercent,
                skeletalMusclePercent = metrics.skeletalMusclePercent, bmrKcal = metrics.bmrKcal,
                fatFreeMassKg = metrics.fatFreeMassKg, subcutaneousFatPercent = metrics.subcutaneousFatPercent,
                visceralFat = metrics.visceralFat, muscleMassKg = metrics.muscleMassKg,
                boneMassKg = metrics.boneMassKg, proteinPercent = metrics.proteinPercent,
                bodyAge = metrics.bodyAge, isEstimated = metrics.isEstimated,
                rawPacketHex = metrics.rawPacketHex,
            )
        )
    }

    suspend fun saveManualWeight(weightKg: Double, profile: UserProfile) {
        saveMeasurement(BodyCompositionCalculator.estimate(weightKg, null, profile).copy(isStable = true), null, "manual")
    }

    suspend fun importFitdaysHistory(parsed: FitdaysParseResult): FitdaysImportSummary {
        var existingCount = 0
        parsed.records.chunked(500).forEach { chunk ->
            existingCount += weights.existingIds(chunk.map(WeightRecord::id)).size
            weights.insert(chunk)
        }
        return FitdaysImportSummary(
            total = parsed.records.size,
            added = parsed.records.size - existingCount,
            updated = existingCount,
            skipped = parsed.skipped,
        )
    }

    suspend fun saveMeal(record: MealRecord) = meals.insert(record)
    suspend fun deleteWeight(record: WeightRecord) = weights.delete(record)
    suspend fun deleteMeal(record: MealRecord) = meals.delete(record)
}
