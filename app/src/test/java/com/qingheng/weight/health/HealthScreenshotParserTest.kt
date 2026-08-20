package com.qingheng.weight.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class HealthScreenshotParserTest {
    @Test
    fun `parses sleep activity and workout without inventing missing fields`() {
        val result = HealthScreenshotParser.parse(
            """
            ```json
            {
              "sourceApp": "vivo健康",
              "screenType": "综合",
              "observations": [{
                "date": "2026-08-20",
                "sleepStart": "2026-08-19T23:42:00+08:00",
                "sleepEnd": "2026-08-20T07:18:00+08:00",
                "sleepMinutes": 456,
                "deepSleepMinutes": 92,
                "lightSleepMinutes": 245,
                "remSleepMinutes": 98,
                "awakeMinutes": 21,
                "sleepScore": 84,
                "steps": 6842,
                "distanceKm": 4.81,
                "activeCaloriesKcal": 328,
                "exerciseMinutes": 36,
                "workouts": [{"type":"户外步行","durationMinutes":31,"distanceKm":2.6}],
                "confidence": 0.96
              }],
              "warnings": []
            }
            ```
            """.trimIndent(),
        )

        val item = result.observations.single()
        assertEquals(LocalDate.of(2026, 8, 20), item.date)
        assertEquals(456, item.sleepMinutes)
        assertEquals(6842L, item.steps)
        assertEquals(4.81, item.distanceKm!!, 0.001)
        assertEquals(
            OffsetDateTime.parse("2026-08-19T23:42:00+08:00").toInstant().toEpochMilli(),
            item.sleepStartAt,
        )
        assertTrue(item.workoutsJson.contains("户外步行"))
        assertEquals(null, item.restingHeartRateBpm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects observation containing no visible metric`() {
        HealthScreenshotParser.parse(
            """
            {"sourceApp":"vivo健康","screenType":"未知","observations":[
              {"date":"2026-08-20","workouts":[],"confidence":0.5}
            ],"warnings":["没有明确数据"]}
            """.trimIndent(),
        )
    }
}
