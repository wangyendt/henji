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

@Entity(
    tableName = "daily_wellness_records",
    indices = [
        Index(value = ["source", "dateEpochDay"], unique = true),
        Index("dateEpochDay"),
    ],
)
data class DailyWellnessRecord(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    val updatedAt: Long,
    val source: String,
    val screenType: String,
    val sleepStartAt: Long? = null,
    val sleepEndAt: Long? = null,
    val sleepMinutes: Int? = null,
    val deepSleepMinutes: Int? = null,
    val lightSleepMinutes: Int? = null,
    val remSleepMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val sleepScore: Double? = null,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCaloriesKcal: Double? = null,
    val exerciseMinutes: Int? = null,
    val exerciseCaloriesKcal: Double? = null,
    val restingHeartRateBpm: Double? = null,
    val averageHeartRateBpm: Double? = null,
    val workoutsJson: String = "[]",
    val confidence: Double,
    val rawAnalysis: String? = null,
)

@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["entityType", "entityId"], unique = true), Index("createdAt")],
)
data class SyncOutboxEvent(
    @PrimaryKey val eventId: String,
    val entityType: String,
    val entityId: String,
    val dedupeKey: String? = null,
    val operation: String,
    val schemaVersion: Int = 1,
    val occurredAt: Long,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
)

@Entity(
    tableName = "sync_tombstones",
    primaryKeys = ["entityType", "entityId"],
    indices = [Index(value = ["entityType", "dedupeKey"])],
)
data class SyncTombstone(
    val entityType: String,
    val entityId: String,
    val dedupeKey: String? = null,
    val deletedAt: Long,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey val id: Int = 1,
    val deviceId: String,
    val pullCursor: Long = 0,
    val initialized: Boolean = false,
    val lastSyncedAt: Long? = null,
    val lastError: String? = null,
)

@Entity(tableName = "sync_deferred_events")
data class DeferredSyncEvent(
    @PrimaryKey val cursor: Long,
    val eventId: String,
    val entityType: String,
    val entityId: String,
    val dedupeKey: String? = null,
    val operation: String,
    val schemaVersion: Int,
    val occurredAt: Long,
    val payloadJson: String,
)

data class FoodMealFrequency(
    val canonicalName: String,
    val category: String,
    val mealCount: Int,
    val estimatedGrams: Double,
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
