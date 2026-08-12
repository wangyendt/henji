package com.qingheng.weight.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppRepositoryTest {
    @Test fun coalescesRepeatedStableFramesAndUpgradesBodyComposition() = runTest {
        val weights = FakeWeightDao()
        val repository = AppRepository(weights, FakeMealDao())
        repeat(5) { sequence ->
            repository.saveMeasurement(
                BodyMetrics(149.6, isStable = true, isEstimated = true, rawPacketHex = "A2-$sequence"),
                "icomon",
            )
        }

        assertEquals(1, weights.records.size)
        val id = weights.records.single().id

        repository.saveMeasurement(
            BodyMetrics(149.6, impedanceOhm = 512.0, isStable = true, isEstimated = false, rawPacketHex = "A3"),
            "icomon",
        )

        assertEquals(1, weights.records.size)
        assertEquals(id, weights.records.single().id)
        assertEquals(512.0, weights.records.single().impedanceOhm!!, 0.001)
        assertFalse(weights.records.single().isEstimated)
    }
}

private class FakeWeightDao : WeightDao {
    val records = mutableListOf<WeightRecord>()
    private val flow = MutableStateFlow<List<WeightRecord>>(emptyList())

    override fun observeAll(): Flow<List<WeightRecord>> = flow
    override suspend fun latest(): WeightRecord? = records.maxByOrNull(WeightRecord::measuredAt)
    override suspend fun recentBluetoothMeasurements(weightKg: Double, after: Long): List<WeightRecord> =
        records.filter { it.source == "bluetooth" && it.measuredAt >= after && kotlin.math.abs(it.weightKg - weightKg) < 0.05 }
            .sortedByDescending(WeightRecord::measuredAt)

    override suspend fun insert(record: WeightRecord) {
        records.removeAll { it.id == record.id }
        records += record
        flow.value = records.sortedByDescending(WeightRecord::measuredAt)
    }
    override suspend fun insert(records: List<WeightRecord>) = records.forEach { insert(it) }
    override suspend fun existingIds(ids: List<String>): List<String> = records.map(WeightRecord::id).filter { it in ids }

    override suspend fun delete(record: WeightRecord) { delete(listOf(record)) }
    override suspend fun delete(records: List<WeightRecord>) {
        val ids = records.mapTo(hashSetOf(), WeightRecord::id)
        this.records.removeAll { it.id in ids }
        flow.value = this.records.sortedByDescending(WeightRecord::measuredAt)
    }
}

private class FakeMealDao : MealDao {
    override fun observeAll(): Flow<List<MealRecord>> = MutableStateFlow(emptyList())
    override suspend fun insert(record: MealRecord) = Unit
    override suspend fun delete(record: MealRecord) = Unit
}
