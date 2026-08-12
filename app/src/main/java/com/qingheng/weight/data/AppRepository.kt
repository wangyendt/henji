package com.qingheng.weight.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AppRepository(private val weights: WeightDao, private val meals: MealDao) {
    val weightRecords: Flow<List<WeightRecord>> = weights.observeAll()
    val mealRecords: Flow<List<MealRecord>> = meals.observeAll()

    private suspend fun saveManualMeasurement(metrics: BodyMetrics) {
        weights.insert(
            WeightRecord(
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
        )
    }

    suspend fun saveManualWeight(weightKg: Double, profile: UserProfile) {
        saveManualMeasurement(BodyCompositionCalculator.estimate(weightKg, null, profile))
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
