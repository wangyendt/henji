package com.qingheng.weight.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class MealWithFoods(
    val meal: MealRecord,
    val foods: List<MealFoodItem>,
)

data class DailyMealSummary(
    val date: LocalDate,
    val meals: List<MealWithFoods>,
    val calorieLow: Int,
    val calorieHigh: Int,
    val foodNames: List<String>,
)

fun groupMealsByDay(
    meals: List<MealRecord>,
    foodItems: List<MealFoodItem>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<DailyMealSummary> {
    val foodsByMeal = foodItems.groupBy(MealFoodItem::mealId)
    return meals
        .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zoneId).toLocalDate() }
        .map { (date, records) ->
            val detailed = records.sortedBy(MealRecord::createdAt).map { meal ->
                MealWithFoods(meal, foodsByMeal[meal.id].orEmpty())
            }
            DailyMealSummary(
                date = date,
                meals = detailed,
                calorieLow = records.sumOf(MealRecord::calorieLow),
                calorieHigh = records.sumOf(MealRecord::calorieHigh),
                foodNames = detailed.flatMap { entry ->
                    entry.foods.map(MealFoodItem::canonicalName)
                        .ifEmpty { listOf(entry.meal.foodNames) }
                }.distinct(),
            )
        }
        .sortedByDescending(DailyMealSummary::date)
}
