package com.qingheng.weight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WeightHistoryTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `daily weight uses the last measurement`() {
        val morning = record("morning", "2026-08-10T08:00", 70.0, bodyFat = 20.0)
        val evening = record("evening", "2026-08-10T21:00", 70.8)

        val day = groupWeightRecordsByDay(listOf(morning, evening), zone).single()

        assertEquals("evening", day.latest.id)
        assertEquals(70.8, day.latest.weightKg, 0.001)
        assertEquals(listOf("evening", "morning"), day.records.map { it.id })
    }

    @Test
    fun `body metric uses the last non-null value without mixing into weight`() {
        val morning = record("morning", "2026-08-10T08:00", 70.0, bodyFat = 20.0)
        val noon = record("noon", "2026-08-10T12:00", 70.3, bodyFat = 19.8)
        val evening = record("evening", "2026-08-10T21:00", 70.8)
        val records = listOf(morning, noon, evening)

        assertEquals(70.8, dailyMetricPoints(records, TrendMetric.WEIGHT, zone).single().value, 0.001)
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
