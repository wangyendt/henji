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

    suspend fun saveHealthConnectMeasurement(measurement: HealthConnectMeasurement): Boolean {
        val deterministicId = "health-connect-${measurement.healthConnectId}"
        val existing = weights.findById(deterministicId) ?: weights.findNearest(
            weightKg = measurement.weightKg,
            measuredAt = measurement.measuredAt,
            from = measurement.measuredAt - 2 * 60_000L,
            to = measurement.measuredAt + 2 * 60_000L,
        )
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
        if (record == existing) return false
        weights.insert(record)
        return true
    }

    suspend fun saveMeal(record: MealRecord) = meals.insert(record)
    suspend fun deleteWeight(record: WeightRecord) = weights.delete(record)
    suspend fun deleteMeal(record: MealRecord) = meals.delete(record)
}

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
