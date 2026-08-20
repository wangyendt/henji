package com.qingheng.weight.sync

import com.qingheng.weight.data.MealFoodItem
import com.qingheng.weight.data.MealRecord
import com.qingheng.weight.data.WeightRecord
import com.qingheng.weight.data.WorkoutRecord
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncPayloadCodecTest {
    @Test
    fun `weight round trip keeps every body composition field`() {
        val original = WeightRecord(
            id = "weight-1",
            measuredAt = 1_786_617_600_000,
            source = "fitdays_import",
            deviceName = "Fitdays",
            weightKg = 80.2,
            impedanceOhm = 503.0,
            bmi = 24.2,
            bodyFatPercent = 22.6,
            bodyWaterPercent = 53.1,
            skeletalMusclePercent = 46.5,
            bmrKcal = 1_680,
            fatFreeMassKg = 62.1,
            subcutaneousFatPercent = 18.4,
            visceralFat = 9.0,
            muscleMassKg = 58.3,
            boneMassKg = 3.1,
            proteinPercent = 17.2,
            bodyAge = 35,
            isEstimated = false,
            rawPacketHex = "aabbcc",
        )

        assertEquals(original, SyncPayloadCodec.decodeWeight(original.id, SyncPayloadCodec.encode(original)))
    }

    @Test
    fun `meal payload includes structured foods but excludes photo`() {
        val meal = MealRecord(
            id = "meal-1",
            createdAt = 1_786_617_600_000,
            mealType = "午餐",
            imageUri = "content://private/photo",
            foodNames = "鸡蛋、米饭",
            calorieLow = 500,
            calorieHigh = 700,
            proteinGrams = 20.0,
            carbsGrams = 75.0,
            fatGrams = 18.0,
            advice = "增加蔬菜",
            confidence = 0.8,
            rawAnalysis = "{\"future\":true}",
        )
        val foods = listOf(
            MealFoodItem("food-1", meal.id, "鸡蛋", "煎蛋", "蛋类", 50.0, 60.0, 90.0, 110.0, 0.9),
        )

        val encoded = SyncPayloadCodec.encode(meal, foods)
        val json = JSONObject(encoded)
        assertFalse(json.has("imageUri"))
        val (restored, restoredFoods) = SyncPayloadCodec.decodeMeal(meal.id, encoded)
        assertEquals(null, restored.imageUri)
        assertEquals(meal.copy(imageUri = null), restored)
        assertEquals(foods, restoredFoods)
    }

    @Test
    fun `workout round trip keeps running and swimming fields`() {
        val original = WorkoutRecord(
            id = "vivo-workout-1787220572000-泳池游泳",
            updatedAt = 1_787_158_800_000,
            source = "vivo_health_share",
            workoutType = "泳池游泳",
            workoutCategory = "swimming",
            startAt = 1_787_220_572_000,
            durationSeconds = 4_471,
            distanceMeters = 1_500.0,
            caloriesKcal = 512.0,
            averageHeartRateBpm = 121.0,
            maximumHeartRateBpm = 146.0,
            averagePaceSecondsPer100Meters = 279.0,
            poolLengthMeters = 50.0,
            lengths = 30,
            strokes = 721,
            averageSwolf = 58.0,
            averageStrokeRatePerMinute = 24.0,
            mainStroke = "自由泳",
            confidence = 0.96,
            rawAnalysis = "{\"sourceApp\":\"vivo健康\"}",
        )

        assertEquals(original, SyncPayloadCodec.decodeWorkout(original.id, SyncPayloadCodec.encode(original)))
    }
}
