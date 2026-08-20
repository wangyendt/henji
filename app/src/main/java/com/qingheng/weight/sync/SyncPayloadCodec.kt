package com.qingheng.weight.sync

import com.qingheng.weight.data.MealFoodItem
import com.qingheng.weight.data.MealRecord
import com.qingheng.weight.data.WeightRecord
import com.qingheng.weight.data.WorkoutRecord
import org.json.JSONArray
import org.json.JSONObject

object SyncPayloadCodec {
    const val WEIGHT = "weight_record"
    const val MEAL = "meal_record"
    const val WORKOUT = "workout_record"
    const val LEGACY_WELLNESS = "daily_wellness_record"
    const val SCHEMA_VERSION = 1

    fun encode(record: WeightRecord): String = JSONObject().apply {
        put("measuredAt", record.measuredAt)
        put("source", record.source)
        putNullable("deviceName", record.deviceName)
        put("weightKg", record.weightKg)
        putNullable("impedanceOhm", record.impedanceOhm)
        putNullable("bmi", record.bmi)
        putNullable("bodyFatPercent", record.bodyFatPercent)
        putNullable("bodyWaterPercent", record.bodyWaterPercent)
        putNullable("skeletalMusclePercent", record.skeletalMusclePercent)
        putNullable("bmrKcal", record.bmrKcal)
        putNullable("fatFreeMassKg", record.fatFreeMassKg)
        putNullable("subcutaneousFatPercent", record.subcutaneousFatPercent)
        putNullable("visceralFat", record.visceralFat)
        putNullable("muscleMassKg", record.muscleMassKg)
        putNullable("boneMassKg", record.boneMassKg)
        putNullable("proteinPercent", record.proteinPercent)
        putNullable("bodyAge", record.bodyAge)
        put("isEstimated", record.isEstimated)
        putNullable("rawPacketHex", record.rawPacketHex)
    }.toString()

    fun decodeWeight(entityId: String, payloadJson: String): WeightRecord {
        val json = JSONObject(payloadJson)
        return WeightRecord(
            id = entityId,
            measuredAt = json.getLong("measuredAt"),
            source = json.getString("source"),
            deviceName = json.nullableString("deviceName"),
            weightKg = json.getDouble("weightKg"),
            impedanceOhm = json.nullableDouble("impedanceOhm"),
            bmi = json.nullableDouble("bmi"),
            bodyFatPercent = json.nullableDouble("bodyFatPercent"),
            bodyWaterPercent = json.nullableDouble("bodyWaterPercent"),
            skeletalMusclePercent = json.nullableDouble("skeletalMusclePercent"),
            bmrKcal = json.nullableInt("bmrKcal"),
            fatFreeMassKg = json.nullableDouble("fatFreeMassKg"),
            subcutaneousFatPercent = json.nullableDouble("subcutaneousFatPercent"),
            visceralFat = json.nullableDouble("visceralFat"),
            muscleMassKg = json.nullableDouble("muscleMassKg"),
            boneMassKg = json.nullableDouble("boneMassKg"),
            proteinPercent = json.nullableDouble("proteinPercent"),
            bodyAge = json.nullableInt("bodyAge"),
            isEstimated = json.optBoolean("isEstimated", true),
            rawPacketHex = json.nullableString("rawPacketHex"),
        )
    }

    fun encode(record: MealRecord, foods: List<MealFoodItem>): String = JSONObject().apply {
        put("createdAt", record.createdAt)
        put("mealType", record.mealType)
        put("foodNames", record.foodNames)
        put("calorieLow", record.calorieLow)
        put("calorieHigh", record.calorieHigh)
        putNullable("proteinGrams", record.proteinGrams)
        putNullable("carbsGrams", record.carbsGrams)
        putNullable("fatGrams", record.fatGrams)
        put("advice", record.advice)
        putNullable("confidence", record.confidence)
        putNullable("rawAnalysis", record.rawAnalysis)
        put("foods", JSONArray().apply {
            foods.forEach { food ->
                put(JSONObject().apply {
                    put("id", food.id)
                    put("canonicalName", food.canonicalName)
                    put("displayName", food.displayName)
                    put("category", food.category)
                    put("estimatedGramsLow", food.estimatedGramsLow)
                    put("estimatedGramsHigh", food.estimatedGramsHigh)
                    put("calorieLow", food.calorieLow)
                    put("calorieHigh", food.calorieHigh)
                    put("confidence", food.confidence)
                })
            }
        })
    }.toString()

    fun decodeMeal(
        entityId: String,
        payloadJson: String,
        localImageUri: String? = null,
    ): Pair<MealRecord, List<MealFoodItem>> {
        val json = JSONObject(payloadJson)
        val record = MealRecord(
            id = entityId,
            createdAt = json.getLong("createdAt"),
            mealType = json.getString("mealType"),
            imageUri = localImageUri,
            foodNames = json.getString("foodNames"),
            calorieLow = json.getInt("calorieLow"),
            calorieHigh = json.getInt("calorieHigh"),
            proteinGrams = json.nullableDouble("proteinGrams"),
            carbsGrams = json.nullableDouble("carbsGrams"),
            fatGrams = json.nullableDouble("fatGrams"),
            advice = json.optString("advice"),
            confidence = json.nullableDouble("confidence"),
            rawAnalysis = json.nullableString("rawAnalysis"),
        )
        val foods = buildList {
            val array = json.optJSONArray("foods") ?: JSONArray()
            for (index in 0 until array.length()) {
                val food = array.getJSONObject(index)
                add(
                    MealFoodItem(
                        id = food.getString("id"),
                        mealId = entityId,
                        canonicalName = food.getString("canonicalName"),
                        displayName = food.getString("displayName"),
                        category = food.getString("category"),
                        estimatedGramsLow = food.getDouble("estimatedGramsLow"),
                        estimatedGramsHigh = food.getDouble("estimatedGramsHigh"),
                        calorieLow = food.getDouble("calorieLow"),
                        calorieHigh = food.getDouble("calorieHigh"),
                        confidence = food.getDouble("confidence"),
                    ),
                )
            }
        }
        return record to foods
    }

    fun encode(record: WorkoutRecord): String = JSONObject().apply {
        put("updatedAt", record.updatedAt)
        put("source", record.source)
        put("workoutType", record.workoutType)
        put("workoutCategory", record.workoutCategory)
        put("startAt", record.startAt)
        putNullable("durationSeconds", record.durationSeconds)
        putNullable("distanceMeters", record.distanceMeters)
        putNullable("caloriesKcal", record.caloriesKcal)
        putNullable("averageHeartRateBpm", record.averageHeartRateBpm)
        putNullable("maximumHeartRateBpm", record.maximumHeartRateBpm)
        putNullable("averagePaceSecondsPerKm", record.averagePaceSecondsPerKm)
        putNullable("averagePaceSecondsPer100Meters", record.averagePaceSecondsPer100Meters)
        putNullable("averageCadencePerMinute", record.averageCadencePerMinute)
        putNullable("steps", record.steps)
        putNullable("averageStrideCentimeters", record.averageStrideCentimeters)
        putNullable("elevationGainMeters", record.elevationGainMeters)
        putNullable("poolLengthMeters", record.poolLengthMeters)
        putNullable("lengths", record.lengths)
        putNullable("strokes", record.strokes)
        putNullable("averageSwolf", record.averageSwolf)
        putNullable("averageStrokeRatePerMinute", record.averageStrokeRatePerMinute)
        putNullable("mainStroke", record.mainStroke)
        put("confidence", record.confidence)
        putNullable("rawAnalysis", record.rawAnalysis)
    }.toString()

    fun decodeWorkout(entityId: String, payloadJson: String): WorkoutRecord {
        val json = JSONObject(payloadJson)
        return WorkoutRecord(
            id = entityId,
            updatedAt = json.getLong("updatedAt"),
            source = json.optString("source", "vivo_health_share"),
            workoutType = json.getString("workoutType"),
            workoutCategory = json.getString("workoutCategory"),
            startAt = json.getLong("startAt"),
            durationSeconds = json.nullableInt("durationSeconds"),
            distanceMeters = json.nullableDouble("distanceMeters"),
            caloriesKcal = json.nullableDouble("caloriesKcal"),
            averageHeartRateBpm = json.nullableDouble("averageHeartRateBpm"),
            maximumHeartRateBpm = json.nullableDouble("maximumHeartRateBpm"),
            averagePaceSecondsPerKm = json.nullableDouble("averagePaceSecondsPerKm"),
            averagePaceSecondsPer100Meters = json.nullableDouble("averagePaceSecondsPer100Meters"),
            averageCadencePerMinute = json.nullableDouble("averageCadencePerMinute"),
            steps = json.nullableLong("steps"),
            averageStrideCentimeters = json.nullableDouble("averageStrideCentimeters"),
            elevationGainMeters = json.nullableDouble("elevationGainMeters"),
            poolLengthMeters = json.nullableDouble("poolLengthMeters"),
            lengths = json.nullableInt("lengths"),
            strokes = json.nullableInt("strokes"),
            averageSwolf = json.nullableDouble("averageSwolf"),
            averageStrokeRatePerMinute = json.nullableDouble("averageStrokeRatePerMinute"),
            mainStroke = json.nullableString("mainStroke"),
            confidence = json.optDouble("confidence", 0.5),
            rawAnalysis = json.nullableString("rawAnalysis"),
        )
    }

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.nullableDouble(name: String): Double? =
        if (!has(name) || isNull(name)) null else getDouble(name)

    private fun JSONObject.nullableInt(name: String): Int? =
        if (!has(name) || isNull(name)) null else getInt(name)

    private fun JSONObject.nullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)
}
