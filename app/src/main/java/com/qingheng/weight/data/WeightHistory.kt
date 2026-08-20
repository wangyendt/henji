package com.qingheng.weight.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

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
    ;

    val isMass: Boolean
        get() = this == WEIGHT || this == FAT_FREE_MASS || this == MUSCLE_MASS || this == BONE_MASS

    val hideInPrivacyMode: Boolean
        get() = this == BMI || this == BODY_FAT
}

data class DailyWeightHistory(
    val date: LocalDate,
    val records: List<WeightRecord>,
) {
    /** The latest measurement before the daily display cutoff represents this day. */
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
): List<DailyWeightHistory> {
    val firstMeasurementId = records.minByOrNull(WeightRecord::measuredAt)?.id
    return records
        .filter { it.id == firstMeasurementId || it.measuredAt.isBeforeDailyDisplayCutoff(zoneId) }
        .groupBy { it.measuredAt.toLocalDate(zoneId) }
        .map { (date, values) ->
            DailyWeightHistory(date, values.sortedByDescending(WeightRecord::measuredAt))
        }
        .sortedByDescending(DailyWeightHistory::date)
}

/**
 * Produces one point per day. The first measurement in the complete history is
 * always eligible; later measurements must be before 14:00. Weight uses the
 * last eligible measurement. For body-composition fields, the last non-null
 * eligible value is used so that a later weight-only measurement does not erase
 * an earlier complete measurement.
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

data class DashboardWeightSummary(
    val currentKg: Double,
    val earliestChangeKg: Double,
    val thirtyDayChangeKg: Double,
    val sevenDayChangeKg: Double,
    val previousDayChangeKg: Double?,
    val weightLossProgressPercentage: Double,
    val remainingToGoalKg: Double,
)

fun dashboardWeightSummary(
    days: List<DailyWeightHistory>,
    goalWeightKg: Double,
): DashboardWeightSummary? {
    val currentDay = days.maxByOrNull(DailyWeightHistory::date) ?: return null
    val earliestDay = days.minByOrNull(DailyWeightHistory::date) ?: return null
    val currentKg = currentDay.latest.weightKg
    val earliestKg = earliestDay.latest.weightKg

    fun changeWithin(periodDays: Long): Double {
        val cutoff = currentDay.date.minusDays(periodDays)
        val baseline = days.filter { !it.date.isBefore(cutoff) }
            .minByOrNull(DailyWeightHistory::date)?.latest?.weightKg ?: currentKg
        return currentKg - baseline
    }

    val totalWeightToLoseKg = earliestKg - goalWeightKg
    val weightLossProgressPercentage = if (abs(totalWeightToLoseKg) < 0.000_001) {
        if (currentKg <= goalWeightKg) 100.0 else 0.0
    } else {
        ((earliestKg - currentKg) / totalWeightToLoseKg * 100.0).coerceIn(0.0, 100.0)
    }

    return DashboardWeightSummary(
        currentKg = currentKg,
        earliestChangeKg = currentKg - earliestKg,
        thirtyDayChangeKg = changeWithin(30),
        sevenDayChangeKg = changeWithin(7),
        previousDayChangeKg = days.firstOrNull { it.date == currentDay.date.minusDays(1) }
            ?.let { currentKg - it.latest.weightKg },
        weightLossProgressPercentage = weightLossProgressPercentage,
        remainingToGoalKg = max(0.0, currentKg - goalWeightKg),
    )
}

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

private val dailyDisplayCutoff: LocalTime = LocalTime.of(14, 0)

private fun Long.isBeforeDailyDisplayCutoff(zoneId: ZoneId): Boolean =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalTime().isBefore(dailyDisplayCutoff)
