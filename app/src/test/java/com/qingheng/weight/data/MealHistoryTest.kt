package com.qingheng.weight.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MealHistoryTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `groups multiple meals and sums calories for one day`() {
        val lunch = meal("lunch", "2026-08-13T12:10:00", 500, 650, "午餐")
        val dinner = meal("dinner", "2026-08-13T19:20:00", 600, 780, "晚餐")
        val foods = listOf(
            food("lunch-egg", lunch.id, "鸡蛋"),
            food("lunch-pickle", lunch.id, "酸菜"),
            food("dinner-egg", dinner.id, "鸡蛋"),
            food("dinner-rice", dinner.id, "米饭"),
        )

        val day = groupMealsByDay(listOf(dinner, lunch), foods, zone).single()

        assertEquals(2, day.meals.size)
        assertEquals(listOf("lunch", "dinner"), day.meals.map { it.meal.id })
        assertEquals(1100, day.calorieLow)
        assertEquals(1430, day.calorieHigh)
        assertEquals(listOf("鸡蛋", "酸菜", "米饭"), day.foodNames)
    }

    private fun meal(id: String, dateTime: String, low: Int, high: Int, type: String) = MealRecord(
        id = id,
        createdAt = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli(),
        mealType = type,
        imageUri = null,
        foodNames = type,
        calorieLow = low,
        calorieHigh = high,
        advice = "",
    )

    private fun food(id: String, mealId: String, name: String) = MealFoodItem(
        id = id,
        mealId = mealId,
        canonicalName = name,
        displayName = name,
        category = "其他",
        estimatedGramsLow = 10.0,
        estimatedGramsHigh = 20.0,
        calorieLow = 10.0,
        calorieHigh = 20.0,
        confidence = 0.9,
    )
}
