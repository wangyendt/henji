package com.qingheng.weight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MetricTrendLineTest {
    @Test
    fun `two points produce a first order trend`() {
        val trend = robustLinearTrend(listOf(
            point("2026-08-01", 80.0),
            point("2026-08-11", 78.0),
        ))!!

        assertEquals(-0.2, trend.slopePerDay, 0.0001)
        assertEquals(80.0, trend.startValue, 0.0001)
        assertEquals(78.0, trend.endValue, 0.0001)
        assertEquals(-2.0, trend.change, 0.0001)
    }

    @Test
    fun `median slope resists a single extreme measurement`() {
        val trend = robustLinearTrend(listOf(
            point("2026-08-01", 80.0),
            point("2026-08-02", 110.0),
            point("2026-08-03", 82.0),
        ))!!

        assertEquals(1.0, trend.slopePerDay, 0.0001)
        assertEquals(80.0, trend.startValue, 0.0001)
        assertEquals(82.0, trend.endValue, 0.0001)
    }

    @Test
    fun `trend uses elapsed calendar days instead of point indexes`() {
        val trend = robustLinearTrend(listOf(
            point("2026-08-01", 80.0),
            point("2026-08-11", 79.0),
            point("2026-08-21", 78.0),
        ))!!

        assertEquals(-0.1, trend.slopePerDay, 0.0001)
        assertEquals(-2.0, trend.change, 0.0001)
    }

    @Test
    fun `one point does not pretend to have a trend`() {
        assertNull(robustLinearTrend(listOf(point("2026-08-01", 80.0))))
        assertNull(robustLinearTrend(emptyList()))
    }

    private fun point(date: String, value: Double) = DailyMetricPoint(
        date = LocalDate.parse(date),
        value = value,
        measuredAt = 0L,
    )
}
