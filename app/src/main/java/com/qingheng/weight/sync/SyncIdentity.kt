package com.qingheng.weight.sync

import com.qingheng.weight.data.WeightRecord
import com.qingheng.weight.data.WorkoutRecord
import kotlin.math.roundToLong

object SyncIdentity {
    data class WeightKey(val epochMinute: Long, val centiKg: Long)

    fun weight(measuredAt: Long, weightKg: Double): String =
        "${measuredAt / MILLIS_PER_MINUTE}:${(weightKg * 100.0).roundToLong()}"

    fun weight(record: WeightRecord): String = weight(record.measuredAt, record.weightKg)

    fun workout(record: WorkoutRecord): String =
        "${record.source}:${record.startAt}:${record.workoutType.trim()}"

    fun parseWeight(value: String): WeightKey? {
        val pieces = value.split(':', limit = 2)
        if (pieces.size != 2) return null
        return WeightKey(pieces[0].toLongOrNull() ?: return null, pieces[1].toLongOrNull() ?: return null)
    }

    private const val MILLIS_PER_MINUTE = 60_000L
}
