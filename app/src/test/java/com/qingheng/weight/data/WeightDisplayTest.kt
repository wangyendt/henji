package com.qingheng.weight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightDisplayTest {
    @Test
    fun `earliest weight uses measurement time instead of input order`() {
        val records = listOf(record("latest", 300, 72.0), record("earliest", 100, 70.5), record("middle", 200, 71.0))

        assertEquals(70.5, earliestWeightKg(records)!!, 0.001)
        assertNull(earliestWeightKg(emptyList()))
    }

    @Test
    fun `hidden weights are differences from the earliest measurement`() {
        assertEquals(72.0, displayedWeightKg(72.0, false, 70.5)!!, 0.001)
        assertEquals(1.5, displayedWeightKg(72.0, true, 70.5)!!, 0.001)
        assertEquals(0.0, displayedWeightKg(70.5, true, 70.5)!!, 0.001)
        assertNull(displayedWeightKg(72.0, true, null))
    }

    @Test
    fun `relative goal edits convert back to an absolute stored weight`() {
        assertEquals(65.0, absoluteWeightKg(65.0, false, 70.5)!!, 0.001)
        assertEquals(65.0, absoluteWeightKg(-5.5, true, 70.5)!!, 0.001)
        assertNull(absoluteWeightKg(-5.5, true, null))
    }

    private fun record(id: String, measuredAt: Long, weightKg: Double) = WeightRecord(
        id = id,
        measuredAt = measuredAt,
        source = "test",
        deviceName = null,
        weightKg = weightKg,
    )
}
