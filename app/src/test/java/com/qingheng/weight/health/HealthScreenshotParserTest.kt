package com.qingheng.weight.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.OffsetDateTime

class HealthScreenshotParserTest {
    @Test
    fun `parses a shared walking workout without inventing swimming fields`() {
        val output = """
            {
              "sourceApp":"vivo健康",
              "screenType":"运动详情",
              "workouts":[{
                "type":"户外步行",
                "category":"walking",
                "startAt":"2026-08-20T18:09:32+08:00",
                "durationMinutes":45.85,
                "distanceMeters":3000,
                "caloriesKcal":283,
                "averageHeartRateBpm":114,
                "maximumHeartRateBpm":130,
                "averagePaceSecondsPerKm":913,
                "averagePaceSecondsPer100Meters":null,
                "averageCadencePerMinute":97,
                "steps":4435,
                "averageStrideCentimeters":67,
                "elevationGainMeters":7,
                "poolLengthMeters":null,
                "lengths":null,
                "strokes":null,
                "averageSwolf":null,
                "averageStrokeRatePerMinute":null,
                "mainStroke":null,
                "confidence":0.99
              }],
              "warnings":[]
            }
        """.trimIndent()

        val analysis = HealthScreenshotParser.parse(output)
        val item = analysis.workouts.single()
        assertEquals("walking", item.workoutCategory)
        assertEquals(2_751, item.durationSeconds)
        assertEquals(3_000.0, item.distanceMeters!!, 0.001)
        assertEquals(
            OffsetDateTime.parse("2026-08-20T18:09:32+08:00").toInstant().toEpochMilli(),
            item.startAt,
        )
        assertNull(item.poolLengthMeters)
        val record = analysis.toRecords(updatedAt = 123L).single()
        assertEquals("vivo-workout-${item.startAt}-户外步行", record.id)
        assertEquals(123L, record.updatedAt)
    }

    @Test
    fun `rejects sleep screenshot output`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            HealthScreenshotParser.parse(
                """{"sourceApp":"vivo健康","screenType":"未知","workouts":[],"warnings":["睡眠页"]}""",
            )
        }
        assertEquals(true, error.message!!.contains("睡眠"))
    }
}
