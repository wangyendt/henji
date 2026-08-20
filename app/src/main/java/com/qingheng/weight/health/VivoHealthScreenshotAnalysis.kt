package com.qingheng.weight.health

import com.qingheng.weight.data.WorkoutRecord
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

data class HealthScreenshotWorkout(
    val workoutType: String,
    val workoutCategory: String,
    val startAt: Long,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val caloriesKcal: Double?,
    val averageHeartRateBpm: Double?,
    val maximumHeartRateBpm: Double?,
    val averagePaceSecondsPerKm: Double?,
    val averagePaceSecondsPer100Meters: Double?,
    val averageCadencePerMinute: Double?,
    val steps: Long?,
    val averageStrideCentimeters: Double?,
    val elevationGainMeters: Double?,
    val poolLengthMeters: Double?,
    val lengths: Int?,
    val strokes: Int?,
    val averageSwolf: Double?,
    val averageStrokeRatePerMinute: Double?,
    val mainStroke: String?,
    val confidence: Double,
)

data class HealthScreenshotAnalysis(
    val sourceApp: String,
    val workouts: List<HealthScreenshotWorkout>,
    val warnings: List<String>,
    val rawJson: String,
) {
    fun toRecords(updatedAt: Long = System.currentTimeMillis()): List<WorkoutRecord> =
        workouts.map { workout ->
            WorkoutRecord(
                id = workoutRecordId(workout.startAt, workout.workoutType),
                updatedAt = updatedAt,
                source = SOURCE,
                workoutType = workout.workoutType,
                workoutCategory = workout.workoutCategory,
                startAt = workout.startAt,
                durationSeconds = workout.durationSeconds,
                distanceMeters = workout.distanceMeters,
                caloriesKcal = workout.caloriesKcal,
                averageHeartRateBpm = workout.averageHeartRateBpm,
                maximumHeartRateBpm = workout.maximumHeartRateBpm,
                averagePaceSecondsPerKm = workout.averagePaceSecondsPerKm,
                averagePaceSecondsPer100Meters = workout.averagePaceSecondsPer100Meters,
                averageCadencePerMinute = workout.averageCadencePerMinute,
                steps = workout.steps,
                averageStrideCentimeters = workout.averageStrideCentimeters,
                elevationGainMeters = workout.elevationGainMeters,
                poolLengthMeters = workout.poolLengthMeters,
                lengths = workout.lengths,
                strokes = workout.strokes,
                averageSwolf = workout.averageSwolf,
                averageStrokeRatePerMinute = workout.averageStrokeRatePerMinute,
                mainStroke = workout.mainStroke,
                confidence = workout.confidence,
                rawAnalysis = rawJson,
            )
        }

    companion object {
        const val SOURCE = "vivo_health_share"
    }
}

fun workoutRecordId(startAt: Long, workoutType: String): String {
    val normalizedType = workoutType.trim().replace(Regex("[\\s/]+"), "-").take(80)
    return "vivo-workout-$startAt-$normalizedType"
}

object HealthScreenshotParser {
    private val supportedCategories = setOf("walking", "running", "swimming")

    fun parse(modelOutput: String): HealthScreenshotAnalysis {
        val json = try {
            JSONObject(extractJsonObject(modelOutput))
        } catch (error: JSONException) {
            throw IllegalArgumentException("模型返回的内容不是有效 JSON", error)
        }
        if (json.optString("screenType") != "运动详情") {
            throw IllegalArgumentException("请选择 vivo 健康中的单次运动详情分享图；睡眠和每日活动不支持导入")
        }
        val array = json.optJSONArray("workouts")
            ?: throw IllegalArgumentException("结果缺少 workouts")
        val workouts = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("workouts[$index] 必须是对象")
                val type = item.optString("type").trim()
                if (type.isEmpty()) throw IllegalArgumentException("workouts[$index] 缺少运动类型")
                val category = item.optString("category").trim()
                if (category !in supportedCategories) {
                    throw IllegalArgumentException("暂不支持运动类型：$type")
                }
                val workout = HealthScreenshotWorkout(
                    workoutType = type,
                    workoutCategory = category,
                    startAt = item.requiredTimestamp("startAt"),
                    durationSeconds = item.optionalDouble("durationMinutes", 0.0, 20_000.0)
                        ?.times(60.0)?.roundToInt(),
                    distanceMeters = item.optionalDouble("distanceMeters", 0.0, 2_000_000.0),
                    caloriesKcal = item.optionalDouble("caloriesKcal", 0.0, 50_000.0),
                    averageHeartRateBpm = item.optionalDouble("averageHeartRateBpm", 20.0, 250.0),
                    maximumHeartRateBpm = item.optionalDouble("maximumHeartRateBpm", 20.0, 300.0),
                    averagePaceSecondsPerKm = item.optionalDouble("averagePaceSecondsPerKm", 0.0, 20_000.0),
                    averagePaceSecondsPer100Meters = item.optionalDouble("averagePaceSecondsPer100Meters", 0.0, 10_000.0),
                    averageCadencePerMinute = item.optionalDouble("averageCadencePerMinute", 0.0, 400.0),
                    steps = item.optionalLong("steps", 0, 1_000_000),
                    averageStrideCentimeters = item.optionalDouble("averageStrideCentimeters", 0.0, 500.0),
                    elevationGainMeters = item.optionalDouble("elevationGainMeters", -1_000.0, 20_000.0),
                    poolLengthMeters = item.optionalDouble("poolLengthMeters", 0.0, 200.0),
                    lengths = item.optionalInt("lengths", 0, 100_000),
                    strokes = item.optionalInt("strokes", 0, 1_000_000),
                    averageSwolf = item.optionalDouble("averageSwolf", 0.0, 1_000.0),
                    averageStrokeRatePerMinute = item.optionalDouble("averageStrokeRatePerMinute", 0.0, 500.0),
                    mainStroke = item.optionalString("mainStroke"),
                    confidence = item.optionalDouble("confidence", 0.0, 1.0)
                        ?: throw IllegalArgumentException("workouts[$index] 缺少 confidence"),
                )
                if (workout.durationSeconds == null && workout.distanceMeters == null && workout.caloriesKcal == null) {
                    throw IllegalArgumentException("workouts[$index] 没有可保存的运动指标")
                }
                add(workout)
            }
        }
        if (workouts.isEmpty()) throw IllegalArgumentException("截图中没有识别到单次运动记录")
        val warnings = buildList {
            val warningArray = json.optJSONArray("warnings") ?: JSONArray()
            for (index in 0 until warningArray.length()) {
                warningArray.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
        return HealthScreenshotAnalysis(
            sourceApp = json.optString("sourceApp", "vivo健康"),
            workouts = workouts.distinctBy { it.startAt to it.workoutType },
            warnings = warnings,
            rawJson = json.toString(),
        )
    }

    private fun JSONObject.requiredTimestamp(name: String): Long {
        val value = optionalString(name) ?: throw IllegalArgumentException("$name 不能为空")
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching {
                LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
            }
            .getOrElse { throw IllegalArgumentException("$name 不是完整 ISO-8601 时间") }
    }

    private fun JSONObject.optionalString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name).trim().takeIf(String::isNotEmpty)

    private fun JSONObject.optionalInt(name: String, min: Int, max: Int): Int? =
        optionalLong(name, min.toLong(), max.toLong())?.toInt()

    private fun JSONObject.optionalLong(name: String, min: Long, max: Long): Long? {
        if (!has(name) || isNull(name)) return null
        val value = runCatching { getLong(name) }
            .getOrElse { throw IllegalArgumentException("$name 不是整数") }
        if (value !in min..max) throw IllegalArgumentException("$name 超出合理范围")
        return value
    }

    private fun JSONObject.optionalDouble(name: String, min: Double, max: Double): Double? {
        if (!has(name) || isNull(name)) return null
        val value = runCatching { getDouble(name) }
            .getOrElse { throw IllegalArgumentException("$name 不是数值") }
        if (!value.isFinite() || value !in min..max) throw IllegalArgumentException("$name 超出合理范围")
        return value
    }

    private fun extractJsonObject(value: String): String {
        val start = value.indexOf('{')
        val end = value.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalArgumentException("模型返回中未找到 JSON 对象")
        return value.substring(start, end + 1)
    }
}

object VivoHealthScreenshotPrompt {
    fun prompt(today: LocalDate): String = """
你是 vivo 健康“单次运动详情分享图”的结构化录入器。当前日期是 $today，时区是 Asia/Shanghai（UTC+08:00）。

只接受单次运动详情分享图，支持户外/室内跑步、户外步行/健走和泳池/开放水域游泳。睡眠页、每日活动页、运动列表、周/月统计和健康首页都不是支持的输入；遇到这些页面时将 screenType 设为“未知”且 workouts 返回空数组。

规则：
1. 只提取图片肉眼明确显示的实际值；地图比例、目标、图表刻度、区间边界和未标数值的曲线不得当作记录。
2. “今天/今日”按 $today 解析，“昨天”按当前日期减一天解析。startAt 必须是带 +08:00 的完整 ISO-8601 时间。
3. category 只能是 walking、running、swimming，并与中文 type 对应。
4. 时长输出分钟；距离统一换算成米；热量为 kcal；心率为 bpm；跑步/步行配速为秒/公里；游泳配速为秒/100米。
5. 跑步或步行可提取步数、步频、步幅、累计爬升；游泳可提取泳池长度、趟数、划水次数、SWOLF、划频和主要泳姿。
6. 看不到的字段填 null，不允许由距离和时间反推配速，也不允许由其他指标估算热量或心率。
7. confidence 是单条运动识别把握，范围 0 到 1；模糊或遮挡时降低置信度并写入 warnings。
8. 只输出 JSON，不要 Markdown、代码围栏或额外说明。
""".trimIndent()

    const val SCHEMA = """
{
  "type": "object",
  "properties": {
    "sourceApp": {"type": "string", "enum": ["vivo健康"]},
    "screenType": {"type": "string", "enum": ["运动详情", "未知"]},
    "workouts": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "type": {"type": "string"},
          "category": {"type": "string", "enum": ["walking", "running", "swimming"]},
          "startAt": {"type": "string"},
          "durationMinutes": {"type": ["number", "null"], "minimum": 0},
          "distanceMeters": {"type": ["number", "null"], "minimum": 0},
          "caloriesKcal": {"type": ["number", "null"], "minimum": 0},
          "averageHeartRateBpm": {"type": ["number", "null"], "minimum": 0},
          "maximumHeartRateBpm": {"type": ["number", "null"], "minimum": 0},
          "averagePaceSecondsPerKm": {"type": ["number", "null"], "minimum": 0},
          "averagePaceSecondsPer100Meters": {"type": ["number", "null"], "minimum": 0},
          "averageCadencePerMinute": {"type": ["number", "null"], "minimum": 0},
          "steps": {"type": ["integer", "null"], "minimum": 0},
          "averageStrideCentimeters": {"type": ["number", "null"], "minimum": 0},
          "elevationGainMeters": {"type": ["number", "null"]},
          "poolLengthMeters": {"type": ["number", "null"], "minimum": 0},
          "lengths": {"type": ["integer", "null"], "minimum": 0},
          "strokes": {"type": ["integer", "null"], "minimum": 0},
          "averageSwolf": {"type": ["number", "null"], "minimum": 0},
          "averageStrokeRatePerMinute": {"type": ["number", "null"], "minimum": 0},
          "mainStroke": {"type": ["string", "null"]},
          "confidence": {"type": "number", "minimum": 0, "maximum": 1}
        },
        "required": ["type", "category", "startAt", "durationMinutes", "distanceMeters", "caloriesKcal", "averageHeartRateBpm", "maximumHeartRateBpm", "averagePaceSecondsPerKm", "averagePaceSecondsPer100Meters", "averageCadencePerMinute", "steps", "averageStrideCentimeters", "elevationGainMeters", "poolLengthMeters", "lengths", "strokes", "averageSwolf", "averageStrokeRatePerMinute", "mainStroke", "confidence"],
        "additionalProperties": false
      }
    },
    "warnings": {"type": "array", "items": {"type": "string"}}
  },
  "required": ["sourceApp", "screenType", "workouts", "warnings"],
  "additionalProperties": false
}
"""
}
