package com.qingheng.weight.data

import kotlin.math.abs

/** The first measurement in the complete history, regardless of list ordering. */
fun earliestWeightKg(records: List<WeightRecord>): Double? =
    records.minByOrNull(WeightRecord::measuredAt)?.weightKg

/**
 * Converts an absolute weight into the value shown by the UI. Relative mode
 * deliberately returns null until history is available so it never falls back
 * to exposing an absolute value.
 */
fun displayedWeightKg(
    valueKg: Double,
    hideAbsoluteWeight: Boolean,
    earliestWeightKg: Double?,
): Double? {
    if (!hideAbsoluteWeight) return valueKg
    val baseline = earliestWeightKg ?: return null
    val difference = valueKg - baseline
    return if (abs(difference) < 0.000_001) 0.0 else difference
}

/** Converts an edited relative value back to the absolute value stored in the database. */
fun absoluteWeightKg(
    displayedKg: Double,
    hideAbsoluteWeight: Boolean,
    earliestWeightKg: Double?,
): Double? = when {
    !hideAbsoluteWeight -> displayedKg
    earliestWeightKg != null -> displayedKg + earliestWeightKg
    else -> null
}
