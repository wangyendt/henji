package com.qingheng.weight.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingheng.weight.data.WeightUnit
import com.qingheng.weight.data.DailyWellnessRecord
import com.qingheng.weight.data.displayedWeightKg
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

@Composable fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun MetricCard(label: String, value: String, unit: String = "", color: Color = Emerald, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = color)
                if (unit.isNotEmpty()) Text(" $unit", fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

fun Long.asDate(pattern: String = "M月d日 HH:mm"): String = SimpleDateFormat(pattern, Locale.CHINA).format(Date(this))
fun Double.one() = String.format(Locale.CHINA, "%.1f", this)

fun WeightUnit.valueFromKg(valueKg: Double): String = fromKilograms(valueKg).one()
fun WeightUnit.weightFromKg(valueKg: Double): String = "${valueFromKg(valueKg)} $symbol"
fun WeightUnit.signedWeightFromKg(valueKg: Double): String =
    "${if (valueKg > 0) "+" else ""}${weightFromKg(valueKg)}"

fun WeightUnit.displayedValueFromKg(
    valueKg: Double,
    hideAbsoluteWeight: Boolean,
    earliestWeightKg: Double?,
): String? = displayedWeightKg(valueKg, hideAbsoluteWeight, earliestWeightKg)?.let { displayed ->
    val prefix = if (hideAbsoluteWeight && displayed > 0.0) "+" else ""
    "$prefix${valueFromKg(displayed)}"
}

fun WeightUnit.displayedWeightFromKg(
    valueKg: Double,
    hideAbsoluteWeight: Boolean,
    earliestWeightKg: Double?,
): String? = displayedValueFromKg(valueKg, hideAbsoluteWeight, earliestWeightKg)?.let { "$it $symbol" }

fun DailyWellnessRecord.summaryText(): String {
    val parts = mutableListOf<String>()
    sleepMinutes?.let { parts += "睡眠 ${it / 60}小时${it % 60}分" }
    steps?.let { parts += "$it 步" }

    val workouts = runCatching { JSONArray(workoutsJson) }.getOrDefault(JSONArray())
    val workoutTypes = mutableListOf<String>()
    var workoutMinutes = 0.0
    var workoutDistanceKm = 0.0
    var workoutCalories = 0.0
    for (index in 0 until workouts.length()) {
        val workout = workouts.optJSONObject(index) ?: continue
        workout.optString("type").takeIf(String::isNotBlank)?.let { if (it !in workoutTypes) workoutTypes += it }
        workoutMinutes += workout.optDouble("durationMinutes", 0.0)
        workoutDistanceKm += workout.optDouble("distanceKm", 0.0)
        workoutCalories += workout.optDouble("caloriesKcal", 0.0)
    }
    if (exerciseMinutes != null) {
        parts += "运动 $exerciseMinutes 分钟"
    } else if (workoutMinutes > 0.0) {
        parts += "${workoutTypes.joinToString("、").ifBlank { "运动" }} ${workoutMinutes.toInt()} 分钟"
    }
    val distanceKm = distanceMeters?.div(1_000.0) ?: workoutDistanceKm.takeIf { it > 0.0 }
    distanceKm?.let { parts += "${it.one()} km" }
    val calories = activeCaloriesKcal ?: exerciseCaloriesKcal ?: workoutCalories.takeIf { it > 0.0 }
    calories?.let { parts += "${it.toInt()} kcal" }
    return parts.ifEmpty { listOf(screenType) }.joinToString(" · ")
}
