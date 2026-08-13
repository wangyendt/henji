package com.qingheng.weight.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "weight_records")
data class WeightRecord(
    @PrimaryKey val id: String,
    val measuredAt: Long,
    val source: String,
    val deviceName: String?,
    val weightKg: Double,
    val impedanceOhm: Double? = null,
    val bmi: Double? = null,
    val bodyFatPercent: Double? = null,
    val bodyWaterPercent: Double? = null,
    val skeletalMusclePercent: Double? = null,
    val bmrKcal: Int? = null,
    val fatFreeMassKg: Double? = null,
    val subcutaneousFatPercent: Double? = null,
    val visceralFat: Double? = null,
    val muscleMassKg: Double? = null,
    val boneMassKg: Double? = null,
    val proteinPercent: Double? = null,
    val bodyAge: Int? = null,
    val isEstimated: Boolean = true,
    val rawPacketHex: String? = null,
)

@Entity(tableName = "meal_records")
data class MealRecord(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val mealType: String,
    val imageUri: String?,
    val foodNames: String,
    val calorieLow: Int,
    val calorieHigh: Int,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val advice: String,
    val confidence: Double? = null,
    val rawAnalysis: String? = null,
)

@Entity(
    tableName = "meal_food_items",
    foreignKeys = [
        ForeignKey(
            entity = MealRecord::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mealId"), Index("canonicalName")],
)
data class MealFoodItem(
    @PrimaryKey val id: String,
    val mealId: String,
    val canonicalName: String,
    val displayName: String,
    val category: String,
    val estimatedGramsLow: Double,
    val estimatedGramsHigh: Double,
    val calorieLow: Double,
    val calorieHigh: Double,
    val confidence: Double,
)

data class FoodMealFrequency(
    val canonicalName: String,
    val category: String,
    val mealCount: Int,
    val estimatedGrams: Double,
)

@Entity(tableName = "daily_wellness")
data class DailyWellnessRecord(
    @PrimaryKey val dateEpochDay: Long,
    val sleepMinutes: Int? = null,
    val deepSleepMinutes: Int? = null,
    val remSleepMinutes: Int? = null,
    val steps: Long? = null,
    val exerciseMinutes: Int? = null,
    val activeCaloriesKcal: Int? = null,
    val exerciseTypes: String = "",
    val sleepSource: String? = null,
    val activitySource: String? = null,
    val syncedAt: Long,
)

@Entity(tableName = "daily_briefings")
data class DailyBriefingRecord(
    @PrimaryKey val dateEpochDay: Long,
    val generatedAt: Long,
    val mealCount: Int,
    val calorieLow: Int?,
    val calorieHigh: Int?,
    val foodNames: String,
    val sleepMinutes: Int?,
    val steps: Long?,
    val exerciseMinutes: Int?,
    val activeCaloriesKcal: Int?,
    val advice: String,
)

data class UserProfile(
    val heightCm: Int = 170,
    val birthYear: Int = 1990,
    val sex: Sex = Sex.MALE,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goalWeightKg: Double = 65.0,
) {
    val age: Int get() = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - birthYear).coerceIn(10, 100)
}

enum class Sex { MALE, FEMALE }
enum class ActivityLevel(val factor: Double, val title: String) {
    LOW(1.2, "较少运动"), MODERATE(1.45, "适度运动"), HIGH(1.7, "经常运动")
}

data class BodyMetrics(
    val weightKg: Double,
    val impedanceOhm: Double? = null,
    val bmi: Double? = null,
    val bodyFatPercent: Double? = null,
    val bodyWaterPercent: Double? = null,
    val skeletalMusclePercent: Double? = null,
    val bmrKcal: Int? = null,
    val fatFreeMassKg: Double? = null,
    val subcutaneousFatPercent: Double? = null,
    val visceralFat: Double? = null,
    val muscleMassKg: Double? = null,
    val boneMassKg: Double? = null,
    val proteinPercent: Double? = null,
    val bodyAge: Int? = null,
    val isStable: Boolean = false,
    val isEstimated: Boolean = true,
    val rawPacketHex: String? = null,
)
