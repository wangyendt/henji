package com.qingheng.weight.health

import com.qingheng.weight.data.DailyWellnessRecord
import com.qingheng.weight.data.MealRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration

class DailyBriefingTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 8, 12)

    @Test
    fun `builds meal activity and sleep summary for selected day`() {
        val mealTime = date.atTime(12, 30).atZone(zone).toInstant().toEpochMilli()
        val meal = MealRecord(
            id = "meal", createdAt = mealTime, mealType = "午餐", imageUri = null,
            foodNames = "鸡蛋、米饭", calorieLow = 500, calorieHigh = 700,
            advice = "", rawAnalysis = null,
        )
        val wellness = DailyWellnessRecord(
            dateEpochDay = date.toEpochDay(), sleepMinutes = 430, steps = 8_200,
            exerciseMinutes = 35, activeCaloriesKcal = 420, exerciseTypes = "步行",
            syncedAt = 1L,
        )

        val result = buildDailyBriefing(date, listOf(meal), wellness, nowMillis = 2L, zone = zone)

        assertEquals(1, result.mealCount)
        assertEquals(500, result.calorieLow)
        assertEquals(700, result.calorieHigh)
        assertEquals("鸡蛋、米饭", result.foodNames)
        assertEquals(430, result.sleepMinutes)
        assertEquals(8_200L, result.steps)
        assertTrue(result.advice.contains("活动量不错"))
    }

    @Test
    fun `missing inputs remain missing and produce actionable copy`() {
        val result = buildDailyBriefing(date, emptyList(), null, zone = zone)

        assertEquals(null, result.sleepMinutes)
        assertEquals(null, result.steps)
        assertEquals(null, result.calorieLow)
        assertTrue(result.advice.contains("还没有同步"))
        assertTrue(result.advice.contains("没有饮食记录"))
    }

    @Test
    fun `next briefing is today before ten and tomorrow after ten`() {
        val before = ZonedDateTime.of(2026, 8, 13, 9, 30, 0, 0, zone)
        val after = ZonedDateTime.of(2026, 8, 13, 10, 30, 0, 0, zone)

        assertEquals(Duration.ofMinutes(30).toMillis(), nextBriefingDelayMillis(before))
        assertEquals(Duration.ofHours(23).plusMinutes(30).toMillis(), nextBriefingDelayMillis(after))
    }
}
