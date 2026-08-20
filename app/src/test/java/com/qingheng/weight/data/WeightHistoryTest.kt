package com.qingheng.weight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WeightHistoryTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `daily weight uses the last measurement before two pm`() {
        val morning = record("morning", "2026-08-10T08:00", 70.0, bodyFat = 20.0)
        val earlyAfternoon = record("early-afternoon", "2026-08-10T13:59", 70.4)
        val evening = record("evening", "2026-08-10T21:00", 70.8)

        val day = groupWeightRecordsByDay(listOf(morning, earlyAfternoon, evening), zone).single()

        assertEquals("early-afternoon", day.latest.id)
        assertEquals(70.4, day.latest.weightKg, 0.001)
        assertEquals(listOf("early-afternoon", "morning"), day.records.map { it.id })
    }

    @Test
    fun `first measurement is displayed even when it is after two pm`() {
        val first = record("first", "2026-08-10T21:00", 72.0)
        val later = record("later", "2026-08-11T08:00", 71.0)

        val days = groupWeightRecordsByDay(listOf(later, first), zone)

        assertEquals(2, days.size)
        assertEquals("first", days.last().latest.id)
    }

    @Test
    fun `later day with only measurements at or after two pm is not displayed`() {
        val first = record("first", "2026-08-09T21:00", 72.0)
        val cutoff = record("cutoff", "2026-08-10T14:00", 70.4)
        val evening = record("evening", "2026-08-10T21:00", 70.8)

        val days = groupWeightRecordsByDay(listOf(first, cutoff, evening), zone)

        assertEquals(1, days.size)
        assertEquals("first", days.single().latest.id)
    }

    @Test
    fun `body metric uses the last non-null value without mixing into weight`() {
        val morning = record("morning", "2026-08-10T08:00", 70.0, bodyFat = 20.0)
        val noon = record("noon", "2026-08-10T12:00", 70.3, bodyFat = 19.8)
        val evening = record("evening", "2026-08-10T21:00", 70.8)
        val records = listOf(morning, noon, evening)

        assertEquals(70.3, dailyMetricPoints(records, TrendMetric.WEIGHT, zone).single().value, 0.001)
        assertEquals(19.8, dailyMetricPoints(records, TrendMetric.BODY_FAT, zone).single().value, 0.001)
    }

    @Test
    fun `normalization uses the full daily minimum and maximum`() {
        val records = listOf(
            record("a", "2026-08-10T08:00", 60.0),
            record("b", "2026-08-11T08:00", 70.0),
            record("c", "2026-08-12T08:00", 65.0),
        )
        val range = normalizedWeightRange(groupWeightRecordsByDay(records, zone))!!

        assertEquals(0f, range.level(60.0), 0.001f)
        assertEquals(0.5f, range.level(65.0), 0.001f)
        assertEquals(1f, range.level(70.0), 0.001f)
        assertNull(normalizedWeightRange(emptyList()))
    }

    @Test
    fun `dashboard summary uses daily values for earliest thirty day and seven day changes`() {
        val records = listOf(
            record("oldest", "2026-06-01T08:00", 100.0),
            record("thirty", "2026-07-15T08:00", 96.0),
            record("week", "2026-08-06T08:00", 92.0),
            record("current", "2026-08-13T08:00", 90.0),
        )

        val summary = dashboardWeightSummary(groupWeightRecordsByDay(records, zone), 80.0)!!

        assertEquals(-10.0, summary.earliestChangeKg, 0.001)
        assertEquals(-6.0, summary.thirtyDayChangeKg, 0.001)
        assertEquals(-2.0, summary.sevenDayChangeKg, 0.001)
        assertNull(summary.previousDayChangeKg)
        assertEquals(50.0, summary.weightLossProgressPercentage, 0.001)
        assertEquals(10.0, summary.remainingToGoalKg, 0.001)
    }

    @Test
    fun `dashboard compares current value with the previous calendar day`() {
        val records = listOf(
            record("older", "2026-08-10T08:00", 92.0),
            record("yesterday", "2026-08-12T08:00", 91.0),
            record("current", "2026-08-13T08:00", 90.4),
        )

        val summary = dashboardWeightSummary(groupWeightRecordsByDay(records, zone), 80.0)!!

        assertEquals(-0.6, summary.previousDayChangeKg!!, 0.001)
    }

    @Test
    fun `weight loss progress is complete at or below goal and zero above initial`() {
        val reached = dashboardWeightSummary(groupWeightRecordsByDay(listOf(
            record("oldest", "2026-08-01T08:00", 100.0),
            record("current", "2026-08-13T08:00", 79.0),
        ), zone), 80.0)!!
        val regressed = dashboardWeightSummary(groupWeightRecordsByDay(listOf(
            record("oldest", "2026-08-01T08:00", 100.0),
            record("current", "2026-08-13T08:00", 105.0),
        ), zone), 80.0)!!

        assertEquals(100.0, reached.weightLossProgressPercentage, 0.001)
        assertEquals(0.0, regressed.weightLossProgressPercentage, 0.001)
    }

    @Test
    fun `weight loss progress handles initial weight equal to goal`() {
        val records = listOf(
            record("oldest", "2026-08-01T08:00", 90.0),
            record("current", "2026-08-13T08:00", 95.0),
        )

        val summary = dashboardWeightSummary(groupWeightRecordsByDay(records, zone), 90.0)!!

        assertEquals(0.0, summary.weightLossProgressPercentage, 0.001)
        assertEquals(5.0, summary.remainingToGoalKg, 0.001)
    }

    @Test
    fun `weight unit converts both directions`() {
        assertEquals(150.0, WeightUnit.JIN.fromKilograms(75.0), 0.001)
        assertEquals(75.0, WeightUnit.JIN.toKilograms(150.0), 0.001)
        assertEquals(WeightUnit.JIN, WeightUnit.KILOGRAM.other())
    }

    @Test
    fun `privacy mode marks BMI and body fat as hidden metrics`() {
        assertEquals(true, TrendMetric.BMI.hideInPrivacyMode)
        assertEquals(true, TrendMetric.BODY_FAT.hideInPrivacyMode)
        assertEquals(false, TrendMetric.BODY_WATER.hideInPrivacyMode)
        assertEquals(false, TrendMetric.WEIGHT.hideInPrivacyMode)
    }

    private fun record(
        id: String,
        time: String,
        weight: Double,
        bodyFat: Double? = null,
    ) = WeightRecord(
        id = id,
        measuredAt = LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli(),
        source = "test",
        deviceName = "Fitdays",
        weightKg = weight,
        bodyFatPercent = bodyFat,
    )
}
