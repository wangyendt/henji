package com.qingheng.weight.data

import java.time.LocalDate

/**
 * A robust first-order trend across the selected date range.
 *
 * The slope is the median of pairwise slopes (Theil-Sen), so an unusual daily
 * measurement does not pull the long-term direction as strongly as ordinary
 * least squares. A bounded, evenly spaced sample keeps the calculation cheap
 * for multi-year histories.
 */
data class MetricTrendLine(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startValue: Double,
    val endValue: Double,
    val slopePerDay: Double,
) {
    val change: Double get() = endValue - startValue
}

fun robustLinearTrend(points: List<DailyMetricPoint>): MetricTrendLine? {
    val daily = points
        .asSequence()
        .filter { it.value.isFinite() }
        .sortedBy(DailyMetricPoint::date)
        .groupBy(DailyMetricPoint::date)
        .map { (date, values) -> date to values.map(DailyMetricPoint::value).average() }

    if (daily.size < 2) return null
    val firstDay = daily.first().first.toEpochDay()
    val lastDay = daily.last().first.toEpochDay()
    if (lastDay == firstDay) return null

    val sample = evenlySpaced(daily, maximumSize = 240)
    val slopes = ArrayList<Double>(sample.size * (sample.size - 1) / 2)
    sample.forEachIndexed { leftIndex, left ->
        for (rightIndex in leftIndex + 1 until sample.size) {
            val right = sample[rightIndex]
            val elapsedDays = right.first.toEpochDay() - left.first.toEpochDay()
            if (elapsedDays > 0) slopes += (right.second - left.second) / elapsedDays
        }
    }
    if (slopes.isEmpty()) return null

    val slope = slopes.median()
    val intercept = daily
        .map { (date, value) -> value - slope * (date.toEpochDay() - firstDay) }
        .median()

    return MetricTrendLine(
        startDate = daily.first().first,
        endDate = daily.last().first,
        startValue = intercept,
        endValue = intercept + slope * (lastDay - firstDay),
        slopePerDay = slope,
    )
}

private fun <T> evenlySpaced(values: List<T>, maximumSize: Int): List<T> {
    if (values.size <= maximumSize) return values
    return List(maximumSize) { index ->
        values[(index.toLong() * values.lastIndex / (maximumSize - 1)).toInt()]
    }
}

private fun List<Double>.median(): Double {
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle]
    else (sorted[middle - 1] + sorted[middle]) / 2.0
}
