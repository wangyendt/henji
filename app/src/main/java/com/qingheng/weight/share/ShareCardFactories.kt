package com.qingheng.weight.share

import com.qingheng.weight.data.DailyMealSummary
import com.qingheng.weight.data.MealRecord
import com.qingheng.weight.ui.asDate
import com.qingheng.weight.ui.calorieRange
import com.qingheng.weight.ui.one
import java.time.format.DateTimeFormatter
import java.util.Locale

fun DailyMealSummary.toDailyMealsShareCard() = DailyMealsShareCard(
    dateTitle = date.format(shareDateFormatter),
    calorieRange = calorieRange(calorieLow, calorieHigh),
    entries = meals.map { entry ->
        MealShareEntry(
            mealTypeAndTime = "${entry.meal.mealType} · ${entry.meal.createdAt.asDate("HH:mm")}",
            foods = entry.meal.foodNames,
            calories = calorieRange(entry.meal.calorieLow, entry.meal.calorieHigh),
        )
    },
)

fun MealRecord.toMealShareCard() = MealShareCard(
    dateTitle = createdAt.asDate("yyyy年M月d日 HH:mm"),
    mealType = mealType,
    foods = foodNames,
    calorieRange = calorieRange(calorieLow, calorieHigh),
    protein = proteinGrams?.let { "${it.one()} g" },
    carbohydrates = carbsGrams?.let { "${it.one()} g" },
    fat = fatGrams?.let { "${it.one()} g" },
    imageUri = imageUri,
)

private val shareDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 E", Locale.CHINA)
