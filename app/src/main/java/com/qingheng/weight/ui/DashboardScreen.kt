package com.qingheng.weight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingheng.weight.data.WeightUnit
import com.qingheng.weight.data.dashboardWeightSummary
import com.qingheng.weight.data.groupWeightRecordsByDay
import kotlin.math.abs

@Composable
fun DashboardScreen(vm: AppViewModel, onImport: () -> Unit, onMeal: () -> Unit) {
    val records by vm.weights.collectAsState()
    val meals by vm.meals.collectAsState()
    val settings by vm.settings.collectAsState()
    val unit = settings.weightUnit
    val dailyHistory = remember(records) { groupWeightRecordsByDay(records) }
    val summary = remember(dailyHistory, settings.profile.goalWeightKg) {
        dashboardWeightSummary(dailyHistory, settings.profile.goalWeightKg)
    }
    val latestBmi = remember(records) { records.firstNotNullOfOrNull { it.bmi } }
    val latestBodyFat = remember(records) { records.firstNotNullOfOrNull { it.bodyFatPercent } }
    val latestBodyWater = remember(records) { records.firstNotNullOfOrNull { it.bodyWaterPercent } }
    val latestSkeletalMuscle = remember(records) { records.firstNotNullOfOrNull { it.skeletalMusclePercent } }
    val todayStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis
    }
    val todayCalories = meals.filter { it.createdAt >= todayStart }.sumOf { (it.calorieLow + it.calorieHigh) / 2 }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("轻衡", "今天也在更了解自己的路上")
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "当前体重",
                        Modifier.weight(1f).padding(start = 74.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    FilledTonalButton(
                        onClick = vm::toggleWeightUnit,
                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp),
                    ) { Text("${unit.symbol} ⇄ ${unit.other().symbol}") }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        summary?.let { unit.valueFromKg(it.currentKg) } ?: "--",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(" ${unit.symbol}", modifier = Modifier.padding(bottom = 9.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WeightChangeStat("较最早", summary?.earliestChangeKg, unit, Modifier.weight(1f))
                    WeightChangeStat("近 30 天", summary?.thirtyDayChangeKg, unit, Modifier.weight(1f))
                    WeightChangeStat("近 7 天", summary?.sevenDayChangeKg, unit, Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("变化比例", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Text(
                        summary?.let { "${it.requestedPercentage.one()}%" } ?: "--",
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = { ((summary?.requestedPercentage ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).height(7.dp).clip(CircleShape),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "目标 ${unit.weightFromKg(settings.profile.goalWeightKg)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        summary?.let { "还需减 ${unit.weightFromKg(it.remainingToGoalKg)}" } ?: "还没有称重数据",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onImport, Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(7.dp)); Text("导入数据")
            }
            OutlinedButton(onMeal, Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Outlined.AddAPhoto, null); Spacer(Modifier.width(7.dp)); Text("记录饮食")
            }
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
                Box(
                    Modifier.size(44.dp).background(Color(0xFFFFF1D7), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("🍱", fontSize = 22.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("今日饮食", fontWeight = FontWeight.SemiBold)
                    Text("已记录 ${meals.count { it.createdAt >= todayStart }} 餐", style = MaterialTheme.typography.bodySmall)
                }
                Text("约 $todayCalories kcal", color = Emerald, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "体脂秤通过 BIA 阻抗与个人资料估算身体成分，结果用于日常趋势参考。",
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeightChangeStat(
    label: String,
    changeKg: Double?,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val color = when {
        changeKg == null || abs(changeKg) < 0.001 -> MaterialTheme.colorScheme.onSurfaceVariant
        changeKg < 0 -> Emerald
        else -> MaterialTheme.colorScheme.error
    }
    val icon = when {
        changeKg == null || abs(changeKg) < 0.001 -> Icons.Outlined.Remove
        changeKg < 0 -> Icons.Outlined.ArrowDownward
        else -> Icons.Outlined.ArrowUpward
    }
    Surface(modifier, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp), tint = color)
                Spacer(Modifier.width(2.dp))
                Text(
                    changeKg?.let { unit.signedWeightFromKg(it) } ?: "--",
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}
