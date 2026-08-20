package com.qingheng.weight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class EnergyBalanceTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 8, 20, 18, 0, 0, 0, zone)

    @Test
    fun `today combines bmr recorded meals and explicit workout calories`() {
        val summary = energyBalanceSummary(
            weights = listOf(weight("w", "2026-08-20T09:00:00+08:00", 80.0, 1600)),
            meals = listOf(meal("m", "2026-08-20T12:00:00+08:00", 1500, 1700)),
            workouts = listOf(workout("x", "2026-08-20T17:00:00+08:00", 300.0)),
            profile = UserProfile(),
            now = now,
        )

        assertEquals(1600, summary.today.bmrKcal)
        assertEquals(1920, summary.today.livingKcal)
        assertEquals(300, summary.today.workoutKcal)
        assertEquals(-720, summary.today.balanceLowKcal)
        assertEquals(-520, summary.today.balanceHighKcal)
        assertEquals(-720.0 / 7700.0, summary.today.weightChangeLowKg!!, 0.000001)
    }

    @Test
    fun `week estimate reports coverage and excludes days without meal records`() {
        val summary = energyBalanceSummary(
            weights = listOf(
                weight("w1", "2026-08-18T09:00:00+08:00", 80.0, 1600),
                weight("w2", "2026-08-20T09:00:00+08:00", 79.5, 1600),
            ),
            meals = listOf(meal("m", "2026-08-20T12:00:00+08:00", 1500, 1700)),
            workouts = listOf(workout("x", "2026-08-19T17:00:00+08:00", 300.0)),
            profile = UserProfile(),
            now = now,
        )

        assertEquals(1, summary.week.recordedMealDays)
        assertEquals(4, summary.week.elapsedDays)
        assertEquals(300, summary.week.workoutKcal)
        assertEquals(-0.5, summary.week.measuredWeightChangeKg!!, 0.000001)
    }

    @Test
    fun `missing meals does not claim zero intake or predicted change`() {
        val summary = energyBalanceSummary(
            weights = listOf(weight("w", "2026-08-20T09:00:00+08:00", 80.0, 1600)),
            meals = emptyList(),
            workouts = emptyList(),
            profile = UserProfile(),
            now = now,
        )

        assertEquals(0, summary.week.recordedMealDays)
        assertNull(summary.week.balanceLowKcal)
        assertNull(summary.week.weightChangeLowKg)
    }

    private fun millis(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()

    private fun weight(id: String, at: String, kg: Double, bmr: Int) = WeightRecord(
        id = id, measuredAt = millis(at), source = "test", deviceName = null,
        weightKg = kg, bmrKcal = bmr,
    )

    private fun meal(id: String, at: String, low: Int, high: Int) = MealRecord(
        id = id, createdAt = millis(at), mealType = "午餐", imageUri = null,
        foodNames = "测试餐", calorieLow = low, calorieHigh = high, advice = "",
    )

    private fun workout(id: String, at: String, calories: Double) = WorkoutRecord(
        id = id, updatedAt = millis(at), source = "test", workoutType = "户外步行",
        workoutCategory = "walking", startAt = millis(at), caloriesKcal = calories,
        confidence = 1.0,
    )
}
