package com.qingheng.weight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(vm: AppViewModel, onImport: () -> Unit, onMeal: () -> Unit) {
    val records by vm.weights.collectAsState()
    val meals by vm.meals.collectAsState()
    val settings by vm.settings.collectAsState()
    val dailyHistory = remember(records) { com.qingheng.weight.data.groupWeightRecordsByDay(records) }
    val latest = dailyHistory.firstOrNull()?.latest
    val previous = dailyHistory.getOrNull(1)?.latest
    val latestBmi = remember(records) { records.firstNotNullOfOrNull { it.bmi } }
    val latestBodyFat = remember(records) { records.firstNotNullOfOrNull { it.bodyFatPercent } }
    val latestBodyWater = remember(records) { records.firstNotNullOfOrNull { it.bodyWaterPercent } }
    val latestSkeletalMuscle = remember(records) { records.firstNotNullOfOrNull { it.skeletalMusclePercent } }
    val todayStart = remember { java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }.timeInMillis }
    val todayCalories = meals.filter { it.createdAt >= todayStart }.sumOf { (it.calorieLow + it.calorieHigh) / 2 }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("轻衡", "今天也在更了解自己的路上")
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("当前体重", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(latest?.weightKg?.one() ?: "--", fontSize = 58.sp, fontWeight = FontWeight.Bold)
                    Text(" kg", modifier = Modifier.padding(bottom = 10.dp))
                }
                val delta = if (latest != null && previous != null) latest.weightKg - previous.weightKg else null
                Text(
                    when { delta == null -> "完成一次称重，开始记录趋势"; delta > 0 -> "较上次 +${delta.one()} kg"; else -> "较上次 ${delta.one()} kg" },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { latest?.let { (1 - kotlin.math.abs(it.weightKg - settings.profile.goalWeightKg) / 20).toFloat().coerceIn(0f, 1f) } ?: 0f },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                )
                Text("目标 ${settings.profile.goalWeightKg.one()} kg", Modifier.align(Alignment.Start).padding(top = 7.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onImport, Modifier.weight(1f).height(52.dp)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(7.dp)); Text("导入数据") }
            OutlinedButton(onMeal, Modifier.weight(1f).height(52.dp)) { Icon(Icons.Outlined.AddAPhoto, null); Spacer(Modifier.width(7.dp)); Text("记录饮食") }
        }
        Text("身体数据", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("BMI", latestBmi?.one() ?: "--", modifier = Modifier.weight(1f))
                MetricCard("体脂率", latestBodyFat?.one() ?: "--", "%", Amber, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("体水分", latestBodyWater?.one() ?: "--", "%", Color(0xFF3189C9), Modifier.weight(1f))
                MetricCard("骨骼肌率", latestSkeletalMuscle?.one() ?: "--", "%", Color(0xFF7C65C1), Modifier.weight(1f))
            }
        }
        Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(Color(0xFFFFF1D7), CircleShape), contentAlignment = Alignment.Center) { Text("🍱", fontSize = 22.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { Text("今日饮食", fontWeight = FontWeight.SemiBold); Text("已记录 ${meals.count { it.createdAt >= todayStart }} 餐", style = MaterialTheme.typography.bodySmall) }
                Text("约 $todayCalories kcal", color = Emerald, fontWeight = FontWeight.Bold)
            }
        }
        Text("体脂秤通过 BIA 阻抗与个人资料估算身体成分，结果用于日常趋势参考。", Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
