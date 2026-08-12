package com.qingheng.weight.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TrendMetric(
    val title: String,
    val unit: String,
    val valueOf: (WeightRecord) -> Double?,
) {
    WEIGHT("体重", "kg", { it.weightKg }),
    BMI("BMI", "", { it.bmi }),
    BODY_FAT("体脂率", "%", { it.bodyFatPercent }),
    BODY_WATER("体水分", "%", { it.bodyWaterPercent }),
    SKELETAL_MUSCLE("骨骼肌率", "%", { it.skeletalMusclePercent }),
    BMR("基础代谢", "kcal", { it.bmrKcal?.toDouble() }),
    FAT_FREE_MASS("去脂体重", "kg", { it.fatFreeMassKg }),
    SUBCUTANEOUS_FAT("皮下脂肪", "%", { it.subcutaneousFatPercent }),
    VISCERAL_FAT("内脏脂肪", "", { it.visceralFat }),
    MUSCLE_MASS("肌肉量", "kg", { it.muscleMassKg }),
    BONE_MASS("骨量", "kg", { it.boneMassKg }),
    PROTEIN("蛋白质", "%", { it.proteinPercent }),
    BODY_AGE("身体年龄", "岁", { it.bodyAge?.toDouble() }),
}

data class DailyWeightHistory(
    val date: LocalDate,
    val records: List<WeightRecord>,
) {
    /** The latest measurement is the representative weight for this day. */
    val latest: WeightRecord get() = records.first()
}

data class DailyMetricPoint(
    val date: LocalDate,
    val value: Double,
    val measuredAt: Long,
)

fun groupWeightRecordsByDay(
    records: List<WeightRecord>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<DailyWeightHistory> = records
    .groupBy { it.measuredAt.toLocalDate(zoneId) }
    .map { (date, values) ->
        DailyWeightHistory(date, values.sortedByDescending(WeightRecord::measuredAt))
    }
    .sortedByDescending(DailyWeightHistory::date)

/**
 * Produces one point per day. Weight uses the last measurement of that day. For
 * body-composition fields, the last non-null value is used so that a later
 * weight-only measurement does not erase an earlier complete measurement.
 */
fun dailyMetricPoints(
    records: List<WeightRecord>,
    metric: TrendMetric,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<DailyMetricPoint> = groupWeightRecordsByDay(records, zoneId)
    .mapNotNull { day ->
        day.records.firstNotNullOfOrNull { record ->
            metric.valueOf(record)?.let { DailyMetricPoint(day.date, it, record.measuredAt) }
        }
    }
    .sortedBy(DailyMetricPoint::date)

data class NormalizedWeightRange(val minimum: Double, val maximum: Double) {
    fun level(weightKg: Double): Float {
        val span = maximum - minimum
        return if (span < 0.001) 0.5f
        else ((weightKg - minimum) / span).toFloat().coerceIn(0f, 1f)
    }
}

fun normalizedWeightRange(days: List<DailyWeightHistory>): NormalizedWeightRange? {
    if (days.isEmpty()) return null
    return NormalizedWeightRange(
        minimum = days.minOf { it.latest.weightKg },
        maximum = days.maxOf { it.latest.weightKg },
    )
}

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
