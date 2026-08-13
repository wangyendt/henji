package com.qingheng.weight.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectRepositoryTest {
    @Test fun mergesAutomaticMetricsIntoFullImportWithoutLosingFitdaysOnlyFields() = runTest {
        val dao = FakeWeightDao()
        val repository = AppRepository(dao, FakeMealDao(), FakeWellnessDao())
        val measuredAt = 1_723_430_100_000L
        val imported = WeightRecord(
            id = "fitdays-file-id",
            measuredAt = measuredAt,
            source = "fitdays_import",
            deviceName = "Fitdays",
            weightKg = 80.0,
            bodyFatPercent = 25.0,
            skeletalMusclePercent = 31.0,
            subcutaneousFatPercent = 20.0,
            visceralFat = 11.0,
            proteinPercent = 16.0,
            bodyAge = 38,
            isEstimated = false,
        )
        dao.insert(imported)

        val changed = repository.saveHealthConnectMeasurement(
            HealthConnectMeasurement(
                healthConnectId = "hc-id",
                measuredAt = measuredAt + 5_000,
                weightKg = 80.0,
                bmi = 24.7,
                bodyFatPercent = 24.8,
                bodyWaterPercent = 52.0,
                bmrKcal = 1_650,
                fatFreeMassKg = 60.2,
                boneMassKg = 3.2,
            )
        )

        assertTrue(changed)
        assertEquals(1, dao.records.size)
        val merged = dao.records.single()
        assertEquals("fitdays-file-id", merged.id)
        assertEquals("fitdays_import+health_connect", merged.source)
        assertEquals(24.8, merged.bodyFatPercent!!, 0.001)
        assertEquals(31.0, merged.skeletalMusclePercent!!, 0.001)
        assertEquals(20.0, merged.subcutaneousFatPercent!!, 0.001)
        assertEquals(11.0, merged.visceralFat!!, 0.001)
        assertEquals(16.0, merged.proteinPercent!!, 0.001)
        assertEquals(38, merged.bodyAge)
    }

    @Test fun repeatedHealthConnectReadIsIdempotent() = runTest {
        val dao = FakeWeightDao()
        val repository = AppRepository(dao, FakeMealDao(), FakeWellnessDao())
        val measurement = HealthConnectMeasurement(
            healthConnectId = "record-1",
            measuredAt = 1_723_430_100_000L,
            weightKg = 70.0,
            bmi = 22.9,
            bodyFatPercent = 18.0,
            bodyWaterPercent = 55.0,
            bmrKcal = 1_550,
            fatFreeMassKg = 57.4,
            boneMassKg = 3.0,
        )

        assertTrue(repository.saveHealthConnectMeasurement(measurement))
        assertFalse(repository.saveHealthConnectMeasurement(measurement))
        assertEquals(1, dao.records.size)
        assertEquals("health-connect-record-1", dao.records.single().id)
    }
}

private class FakeWeightDao : WeightDao {
    val records = mutableListOf<WeightRecord>()
    private val flow = MutableStateFlow<List<WeightRecord>>(emptyList())

    override fun observeAll(): Flow<List<WeightRecord>> = flow
    override suspend fun latest(): WeightRecord? = records.maxByOrNull(WeightRecord::measuredAt)
    override suspend fun findById(id: String): WeightRecord? = records.firstOrNull { it.id == id }
    override suspend fun findNearest(weightKg: Double, measuredAt: Long, from: Long, to: Long): WeightRecord? =
        records.filter {
            it.source != "manual" && it.measuredAt in from..to && kotlin.math.abs(it.weightKg - weightKg) < 0.05
        }.minByOrNull { kotlin.math.abs(it.measuredAt - measuredAt) }

    override suspend fun insert(record: WeightRecord) {
        records.removeAll { it.id == record.id }
        records += record
        flow.value = records.sortedByDescending(WeightRecord::measuredAt)
    }
    override suspend fun insert(records: List<WeightRecord>) = records.forEach { insert(it) }
    override suspend fun existingIds(ids: List<String>): List<String> = records.map(WeightRecord::id).filter { it in ids }
    override suspend fun delete(record: WeightRecord) { records.removeAll { it.id == record.id } }
}

private class FakeMealDao : MealDao {
    override fun observeAll(): Flow<List<MealRecord>> = MutableStateFlow(emptyList())
    override fun observeAllFoodItems(): Flow<List<MealFoodItem>> = MutableStateFlow(emptyList())
    override fun observeFoodFrequencies(): Flow<List<FoodMealFrequency>> = MutableStateFlow(emptyList())
    override suspend fun insert(record: MealRecord) = Unit
    override suspend fun insertFoodItems(items: List<MealFoodItem>) = Unit
    override suspend fun deleteFoodItems(mealId: String) = Unit
    override suspend fun delete(record: MealRecord) = Unit
    override suspend fun delete(records: List<MealRecord>) = Unit
}

private class FakeWellnessDao : WellnessDao {
    override fun observeAll(): Flow<List<DailyWellnessRecord>> = MutableStateFlow(emptyList())
    override suspend fun insert(records: List<DailyWellnessRecord>) = Unit
    override suspend fun find(dateEpochDay: Long): DailyWellnessRecord? = null
    override fun observeBriefings(): Flow<List<DailyBriefingRecord>> = MutableStateFlow(emptyList())
    override suspend fun insertBriefing(record: DailyBriefingRecord) = Unit
    override suspend fun findBriefing(dateEpochDay: Long): DailyBriefingRecord? = null
}
