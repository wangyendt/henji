package com.qingheng.weight.sync

import com.qingheng.weight.data.MealFoodItem
import com.qingheng.weight.data.MealRecord
import com.qingheng.weight.data.WeightRecord
import org.json.JSONArray
import org.json.JSONObject

object SyncPayloadCodec {
    const val WEIGHT = "weight_record"
    const val MEAL = "meal_record"
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

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.nullableDouble(name: String): Double? =
        if (!has(name) || isNull(name)) null else getDouble(name)

    private fun JSONObject.nullableInt(name: String): Int? =
        if (!has(name) || isNull(name)) null else getInt(name)
}
