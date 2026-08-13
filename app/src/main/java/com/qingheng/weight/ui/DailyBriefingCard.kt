package com.qingheng.weight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingheng.weight.data.DailyBriefingRecord
import java.time.LocalDate

@Composable
fun DailyBriefingCard(
    briefing: DailyBriefingRecord?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F3FF)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).background(Color(0xFF6F6BD9), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("昨日节律", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (briefing == null) "等待第一份完整小结" else "饮食 · 活动 · 恢复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onRefresh) { Icon(Icons.Outlined.Refresh, "重新生成昨日小结") }
            }
            if (briefing == null) {
                Text(
                    "每天上午 10 点整理前一天。现在可先同步已有数据；缺少的项目会明确留空，不会用估算值补齐。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onRefresh, Modifier.fillMaxWidth()) { Text("生成昨天的小结") }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BriefingMetric(
                        Icons.Outlined.Bedtime,
                        "睡眠",
                        briefing.sleepMinutes?.let(::durationText) ?: "待同步",
                        Color(0xFF6866C5),
                        Modifier.weight(1f),
                    )
                    BriefingMetric(
                        Icons.Outlined.DirectionsWalk,
                        "活动",
                        briefing.steps?.let(::stepText)
                            ?: briefing.exerciseMinutes?.let { "${it}分钟" }
                            ?: "待同步",
                        Color(0xFF1C8C77),
                        Modifier.weight(1f),
                    )
                    BriefingMetric(
                        Icons.Outlined.Restaurant,
                        "饮食",
                        if (briefing.mealCount > 0) "${briefing.mealCount}餐" else "未记录",
                        Color(0xFFE2952A),
                        Modifier.weight(1f),
                    )
                }
                briefing.calorieLow?.let { low ->
                    val high = briefing.calorieHigh ?: low
                    Text(
                        "已记录 $low–$high kcal" + if (briefing.foodNames.isNotBlank()) " · ${briefing.foodNames}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(color = Color.White.copy(alpha = 0.72f), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("今天这样做", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        briefing.advice.lines().filter { it.isNotBlank() }.forEach { line ->
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    Modifier.padding(top = 7.dp).size(5.dp).background(Color(0xFF6F6BD9), CircleShape)
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(line, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (briefing.dateEpochDay != LocalDate.now().minusDays(1).toEpochDay()) {
                    Text(
                        "这是最近一次已生成的小结，打开应用后会自动补算昨天。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BriefingMetric(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier,
) {
    Surface(modifier, color = Color.White.copy(alpha = 0.78f), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun durationText(minutes: Int): String = "${minutes / 60}时${minutes % 60}分"
private fun stepText(steps: Long): String = if (steps >= 10_000) String.format("%.1f万步", steps / 10_000.0) else "${steps}步"
