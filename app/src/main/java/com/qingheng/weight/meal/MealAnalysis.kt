package com.qingheng.weight.meal

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** A lower/upper estimate. Both values are inclusive and use the unit named by the property. */
data class EstimateRange(
    val min: Double,
    val max: Double,
)

data class RecognizedFood(
    val name: String,
    val displayName: String,
    val category: String,
    val estimatedGrams: EstimateRange,
    val caloriesKcal: EstimateRange,
    val confidence: Double,
)

/** Structured result returned after analysing one food photo. */
data class MealAnalysis(
    val dishes: List<String>,
    val foods: List<RecognizedFood>,
    val caloriesKcal: EstimateRange,
    val proteinGrams: EstimateRange,
    val carbohydrateGrams: EstimateRange,
    val fatGrams: EstimateRange,
    val advice: String,
) {
    fun toJsonString(): String = JSONObject().apply {
        put("dishes", JSONArray(dishes))
        put("foods", JSONArray().apply {
            foods.forEach { food ->
                put(JSONObject().apply {
                    put("name", food.name)
                    put("displayName", food.displayName)
                    put("category", food.category)
                    put("estimatedGrams", food.estimatedGrams.toJson())
                    put("caloriesKcal", food.caloriesKcal.toJson())
                    put("confidence", food.confidence)
                })
            }
        })
        put("caloriesKcal", caloriesKcal.toJson())
        put("proteinGrams", proteinGrams.toJson())
        put("carbohydrateGrams", carbohydrateGrams.toJson())
        put("fatGrams", fatGrams.toJson())
        put("advice", advice)
    }.toString()

    private fun EstimateRange.toJson() = JSONObject().put("min", min).put("max", max)
}

/** Parses the model text separately from Android/network code, so it is usable in pure JVM tests. */
object MealAnalysisParser {
    @JvmStatic
    fun parse(modelOutput: String): MealAnalysis {
        val jsonText = extractJsonObject(modelOutput)
        val root = try {
            JSONObject(jsonText)
        } catch (error: JSONException) {
            throw MealAnalysisParseException("模型返回的内容不是有效 JSON", error)
        }

        val dishes = parseDishes(root)
        val macros = root.optJSONObject("macros")
        return MealAnalysis(
            dishes = dishes,
            foods = parseFoods(root),
            caloriesKcal = parseRange(root, null, "caloriesKcal", "calories", "totalCalories"),
            proteinGrams = parseRange(root, macros, "proteinGrams", "protein"),
            carbohydrateGrams = parseRange(root, macros, "carbohydrateGrams", "carbohydrates", "carbs"),
            fatGrams = parseRange(root, macros, "fatGrams", "fat"),
            advice = requiredText(root, "advice", "suggestion"),
        )
    }

    private fun parseFoods(root: JSONObject): List<RecognizedFood> {
        val array = root.optJSONArray("foods")
            ?: throw MealAnalysisParseException("模型结果缺少结构化食物列表 foods")
        val foods = buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index)
                    ?: throw MealAnalysisParseException("foods[$index] 必须是对象")
                val confidence = number(value.opt("confidence"))
                    ?: throw MealAnalysisParseException("foods[$index] 缺少 confidence")
                if (!confidence.isFinite() || confidence !in 0.0..1.0) {
                    throw MealAnalysisParseException("foods[$index].confidence 必须在 0 到 1 之间")
                }
                add(
                    RecognizedFood(
                        name = requiredText(value, "name"),
                        displayName = requiredText(value, "displayName"),
                        category = requiredText(value, "category"),
                        estimatedGrams = parseRange(value, null, "estimatedGrams"),
                        caloriesKcal = parseRange(value, null, "caloriesKcal"),
                        confidence = confidence,
                    ),
                )
            }
        }
        if (foods.isEmpty()) throw MealAnalysisParseException("模型结果中的结构化食物列表为空")
        return foods.distinctBy { it.name.trim().lowercase() }
    }

    private fun parseDishes(root: JSONObject): List<String> {
        val array = root.optJSONArray("dishes") ?: root.optJSONArray("foods")
            ?: throw MealAnalysisParseException("模型结果缺少菜品列表 dishes")
        val dishes = buildList {
            for (index in 0 until array.length()) {
                when (val value = array.opt(index)) {
                    is String -> value.trim().takeIf(String::isNotEmpty)?.let(::add)
                    is JSONObject -> listOf("name", "dish", "food")
                        .firstNotNullOfOrNull { key -> value.optString(key).trim().takeIf(String::isNotEmpty) }
                        ?.let(::add)
                }
            }
        }.distinct()
        if (dishes.isEmpty()) throw MealAnalysisParseException("模型结果中的菜品列表为空")
        return dishes
    }

    private fun parseRange(
        root: JSONObject,
        nested: JSONObject?,
        vararg keys: String,
    ): EstimateRange {
        val value = keys.firstNotNullOfOrNull { key ->
            root.opt(key).takeUnless { it === JSONObject.NULL }
                ?: nested?.opt(key)?.takeUnless { it === JSONObject.NULL }
        } ?: throw MealAnalysisParseException("模型结果缺少数值区间 ${keys.first()}")

        val (rawMin, rawMax) = when (value) {
            is JSONObject -> {
                val lower = number(value.opt("min")) ?: number(value.opt("low"))
                val upper = number(value.opt("max")) ?: number(value.opt("high"))
                if (lower == null || upper == null) {
                    throw MealAnalysisParseException("${keys.first()} 必须包含 min 和 max")
                }
                lower to upper
            }
            else -> {
                val values = numbers(value)
                when {
                    values.size >= 2 -> values[0] to values[1]
                    values.size == 1 -> values[0] to values[0]
                    else -> throw MealAnalysisParseException("${keys.first()} 不是有效数值区间")
                }
            }
        }
        if (!rawMin.isFinite() || !rawMax.isFinite() || rawMin < 0 || rawMax < 0) {
            throw MealAnalysisParseException("${keys.first()} 必须是非负有限数值")
        }
        return EstimateRange(min(rawMin, rawMax), max(rawMin, rawMax))
    }

    private fun requiredText(root: JSONObject, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> root.optString(key).trim().takeIf(String::isNotEmpty) }
            ?: throw MealAnalysisParseException("模型结果缺少饮食建议 advice")

    private fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> NUMBER.find(value)?.value?.toDoubleOrNull()
        else -> null
    }

    private fun numbers(value: Any?): List<Double> = when (value) {
        is Number -> listOf(value.toDouble())
        is String -> NUMBER.findAll(value).mapNotNull { it.value.toDoubleOrNull() }.toList()
        is JSONArray -> (0 until value.length()).mapNotNull { number(value.opt(it)) }
        else -> emptyList()
    }

    /**
     * Accepts strict JSON, a markdown fenced JSON block, or JSON preceded/followed by short prose.
     * The scanner understands quoted braces and escaped quotes instead of using a greedy regex.
     */
    private fun extractJsonObject(output: String): String {
        val text = output.trim().removePrefix("\uFEFF")
        var start = -1
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in text.indices) {
            val char = text[index]
            if (start < 0) {
                if (char == '{') {
                    start = index
                    depth = 1
                }
                continue
            }
            if (quoted) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quoted = false
                }
                continue
            }
            when (char) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        throw MealAnalysisParseException("模型返回中未找到完整 JSON 对象")
    }

    private val NUMBER = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?")
}

class MealAnalysisParseException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
