package com.qingheng.weight.health

import com.qingheng.weight.data.DailyWellnessRecord
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

data class HealthScreenshotObservation(
    val date: LocalDate,
    val sleepStartAt: Long?,
    val sleepEndAt: Long?,
    val sleepMinutes: Int?,
    val deepSleepMinutes: Int?,
    val lightSleepMinutes: Int?,
    val remSleepMinutes: Int?,
    val awakeMinutes: Int?,
    val sleepScore: Double?,
    val steps: Long?,
    val distanceKm: Double?,
    val activeCaloriesKcal: Double?,
    val exerciseMinutes: Int?,
    val exerciseCaloriesKcal: Double?,
    val restingHeartRateBpm: Double?,
    val averageHeartRateBpm: Double?,
    val workoutsJson: String,
    val confidence: Double,
)

data class HealthScreenshotAnalysis(
    val sourceApp: String,
    val screenType: String,
    val observations: List<HealthScreenshotObservation>,
    val warnings: List<String>,
    val rawJson: String,
) {
    fun toRecords(updatedAt: Long = System.currentTimeMillis()): List<DailyWellnessRecord> =
        observations.map { observation ->
            DailyWellnessRecord(
                id = "vivo-health-${observation.date.toEpochDay()}",
                dateEpochDay = observation.date.toEpochDay(),
                updatedAt = updatedAt,
                source = "vivo_health_screenshot",
                screenType = screenType,
                sleepStartAt = observation.sleepStartAt,
                sleepEndAt = observation.sleepEndAt,
                sleepMinutes = observation.sleepMinutes,
                deepSleepMinutes = observation.deepSleepMinutes,
                lightSleepMinutes = observation.lightSleepMinutes,
                remSleepMinutes = observation.remSleepMinutes,
                awakeMinutes = observation.awakeMinutes,
                sleepScore = observation.sleepScore,
                steps = observation.steps,
                distanceMeters = observation.distanceKm?.times(1_000.0),
                activeCaloriesKcal = observation.activeCaloriesKcal,
                exerciseMinutes = observation.exerciseMinutes,
                exerciseCaloriesKcal = observation.exerciseCaloriesKcal,
                restingHeartRateBpm = observation.restingHeartRateBpm,
                averageHeartRateBpm = observation.averageHeartRateBpm,
                workoutsJson = observation.workoutsJson,
                confidence = observation.confidence,
                rawAnalysis = rawJson,
            )
        }
}

object HealthScreenshotParser {
    fun parse(modelOutput: String): HealthScreenshotAnalysis {
        val json = try {
            JSONObject(extractJsonObject(modelOutput))
        } catch (error: JSONException) {
            throw IllegalArgumentException("模型返回的内容不是有效 JSON", error)
        }
        val array = json.optJSONArray("observations")
            ?: throw IllegalArgumentException("结果缺少 observations")
        val observations = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("observations[$index] 必须是对象")
                val date = runCatching { LocalDate.parse(item.getString("date")) }
                    .getOrElse { throw IllegalArgumentException("observations[$index].date 不是 YYYY-MM-DD") }
                val workouts = item.optJSONArray("workouts") ?: JSONArray()
                val observation = HealthScreenshotObservation(
                    date = date,
                    sleepStartAt = item.optionalTimestamp("sleepStart"),
                    sleepEndAt = item.optionalTimestamp("sleepEnd"),
                    sleepMinutes = item.optionalInt("sleepMinutes", 0, 2_000),
                    deepSleepMinutes = item.optionalInt("deepSleepMinutes", 0, 2_000),
                    lightSleepMinutes = item.optionalInt("lightSleepMinutes", 0, 2_000),
                    remSleepMinutes = item.optionalInt("remSleepMinutes", 0, 2_000),
                    awakeMinutes = item.optionalInt("awakeMinutes", 0, 2_000),
                    sleepScore = item.optionalDouble("sleepScore", 0.0, 100.0),
                    steps = item.optionalLong("steps", 0, 500_000),
                    distanceKm = item.optionalDouble("distanceKm", 0.0, 1_000.0),
                    activeCaloriesKcal = item.optionalDouble("activeCaloriesKcal", 0.0, 20_000.0),
                    exerciseMinutes = item.optionalInt("exerciseMinutes", 0, 2_000),
                    exerciseCaloriesKcal = item.optionalDouble("exerciseCaloriesKcal", 0.0, 20_000.0),
                    restingHeartRateBpm = item.optionalDouble("restingHeartRateBpm", 20.0, 250.0),
                    averageHeartRateBpm = item.optionalDouble("averageHeartRateBpm", 20.0, 250.0),
                    workoutsJson = workouts.toString(),
                    confidence = item.optionalDouble("confidence", 0.0, 1.0)
                        ?: throw IllegalArgumentException("observations[$index] 缺少 confidence"),
                )
                if (!observation.hasMetrics()) {
                    throw IllegalArgumentException("observations[$index] 没有可保存的健康指标")
                }
                add(observation)
            }
        }
        if (observations.isEmpty()) throw IllegalArgumentException("截图中没有识别到可保存的健康数据")
        val warnings = buildList {
            val warningArray = json.optJSONArray("warnings") ?: JSONArray()
            for (index in 0 until warningArray.length()) {
                warningArray.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
        return HealthScreenshotAnalysis(
            sourceApp = json.optString("sourceApp", "vivo健康"),
            screenType = json.optString("screenType", "未知"),
            observations = observations.distinctBy(HealthScreenshotObservation::date),
            warnings = warnings,
            rawJson = json.toString(),
        )
    }

    private fun HealthScreenshotObservation.hasMetrics(): Boolean =
        sleepMinutes != null || sleepStartAt != null || sleepEndAt != null || sleepScore != null ||
            steps != null || distanceKm != null || activeCaloriesKcal != null || exerciseMinutes != null ||
            exerciseCaloriesKcal != null || restingHeartRateBpm != null || averageHeartRateBpm != null ||
            workoutsJson != "[]"

    private fun JSONObject.optionalTimestamp(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        val value = getString(name).trim()
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching {
                LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
            }
            .getOrElse { throw IllegalArgumentException("$name 不是完整 ISO-8601 时间") }
    }

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
你是 vivo 健康截图的结构化数据录入器。当前日期是 $today，时区是 Asia/Shanghai（UTC+08:00）。

请识别截图中肉眼明确显示的睡眠、日活动和运动数据，返回严格符合 JSON Schema 的结果。

规则：
1. 只提取截图明确展示的实际数据；目标值、建议值、图表刻度和未标数值的曲线不得当作实际记录。
2. “今天/今日”按 $today 解析，“昨天”按当前日期减一天解析。睡眠记录的 date 使用醒来所在的日期。
3. 如果明显是当天首页且没有单独显示日期，可以使用 $today；其他无法确定日期的内容不要录入。
4. sleepStart 和 sleepEnd 必须是带 +08:00 的完整 ISO-8601 时间。跨午夜时正确设置前一天和当天日期。
5. 时长统一为分钟；距离统一为公里；热量统一为 kcal；心率统一为 bpm。
6. 不要根据总睡眠反推出睡眠阶段，不要根据步数估算距离或热量；看不到的可空字段填 null。
7. workouts 保留每一条肉眼可见的运动；type 使用简短中文名称，startAt 使用完整 ISO-8601 时间。
8. confidence 表示该日期数据整体识别把握，范围 0 到 1。模糊或遮挡时降低置信度并在 warnings 说明。
9. 一张周/月截图可以返回多个 observations，但每个日期只出现一次。
10. 只输出 JSON，不要 Markdown、代码围栏或额外说明。
""".trimIndent()

    const val SCHEMA = """
{
  "type": "object",
  "properties": {
    "sourceApp": {"type": "string", "enum": ["vivo健康"]},
    "screenType": {"type": "string", "enum": ["睡眠", "日活动", "运动详情", "综合", "未知"]},
    "observations": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "properties": {
          "date": {"type": "string"},
          "sleepStart": {"type": ["string", "null"]},
          "sleepEnd": {"type": ["string", "null"]},
          "sleepMinutes": {"type": ["integer", "null"], "minimum": 0},
          "deepSleepMinutes": {"type": ["integer", "null"], "minimum": 0},
          "lightSleepMinutes": {"type": ["integer", "null"], "minimum": 0},
          "remSleepMinutes": {"type": ["integer", "null"], "minimum": 0},
          "awakeMinutes": {"type": ["integer", "null"], "minimum": 0},
          "sleepScore": {"type": ["number", "null"], "minimum": 0, "maximum": 100},
          "steps": {"type": ["integer", "null"], "minimum": 0},
          "distanceKm": {"type": ["number", "null"], "minimum": 0},
          "activeCaloriesKcal": {"type": ["number", "null"], "minimum": 0},
          "exerciseMinutes": {"type": ["integer", "null"], "minimum": 0},
          "exerciseCaloriesKcal": {"type": ["number", "null"], "minimum": 0},
          "restingHeartRateBpm": {"type": ["number", "null"], "minimum": 0},
          "averageHeartRateBpm": {"type": ["number", "null"], "minimum": 0},
          "workouts": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "type": {"type": "string"},
                "startAt": {"type": ["string", "null"]},
                "durationMinutes": {"type": ["number", "null"], "minimum": 0},
                "distanceKm": {"type": ["number", "null"], "minimum": 0},
                "caloriesKcal": {"type": ["number", "null"], "minimum": 0},
                "averageHeartRateBpm": {"type": ["number", "null"], "minimum": 0},
                "maximumHeartRateBpm": {"type": ["number", "null"], "minimum": 0}
              },
              "required": ["type", "startAt", "durationMinutes", "distanceKm", "caloriesKcal", "averageHeartRateBpm", "maximumHeartRateBpm"],
              "additionalProperties": false
            }
          },
          "confidence": {"type": "number", "minimum": 0, "maximum": 1}
        },
        "required": ["date", "sleepStart", "sleepEnd", "sleepMinutes", "deepSleepMinutes", "lightSleepMinutes", "remSleepMinutes", "awakeMinutes", "sleepScore", "steps", "distanceKm", "activeCaloriesKcal", "exerciseMinutes", "exerciseCaloriesKcal", "restingHeartRateBpm", "averageHeartRateBpm", "workouts", "confidence"],
        "additionalProperties": false
      }
    },
    "warnings": {"type": "array", "items": {"type": "string"}}
  },
  "required": ["sourceApp", "screenType", "observations", "warnings"],
  "additionalProperties": false
}
"""
}
