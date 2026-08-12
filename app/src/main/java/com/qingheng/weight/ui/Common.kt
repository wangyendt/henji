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
