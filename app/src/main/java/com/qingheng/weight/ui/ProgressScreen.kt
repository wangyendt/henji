package com.qingheng.weight.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingheng.weight.data.WeightRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(vm: AppViewModel) {
    val all by vm.weights.collectAsState()
    var days by remember { mutableIntStateOf(30) }
    val cutoff = System.currentTimeMillis() - days * 86_400_000L
    val records = all.filter { it.measuredAt >= cutoff }.sortedBy { it.measuredAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("身体趋势", "用长期变化代替单次数字的焦虑") }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                listOf(7, 30, 90).forEachIndexed { index, value ->
                    SegmentedButton(selected = days == value, onClick = { days = value }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text("$value 天") }
                }
            }
            Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("体重变化", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    WeightChart(records, Modifier.fillMaxWidth().height(170.dp))
                    val change = records.lastOrNull()?.weightKg?.minus(records.firstOrNull()?.weightKg ?: 0.0)
                    Text(if (change == null) "还没有这段时间的数据" else "${records.size} 次记录 · 净变化 ${if (change > 0) "+" else ""}${change.one()} kg", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("称重记录", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        items(all, key = { it.id }) { record -> WeightRow(record) { vm.deleteWeight(record) } }
        if (all.isEmpty()) item { Text("还没有记录，去完成第一次称重吧。", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun WeightChart(records: List<WeightRecord>, modifier: Modifier) {
    Canvas(modifier) {
        if (records.size < 2) return@Canvas
        val min = records.minOf { it.weightKg } - 0.5; val max = records.maxOf { it.weightKg } + 0.5
        val span = (max - min).coerceAtLeast(1.0)
        val path = Path()
        records.forEachIndexed { index, record ->
            val x = size.width * index / (records.size - 1)
            val y = size.height * (1 - ((record.weightKg - min) / span)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Emerald, style = Stroke(width = 7f, cap = StrokeCap.Round))
        records.forEachIndexed { index, record ->
            val x = size.width * index / (records.size - 1); val y = size.height * (1 - ((record.weightKg - min) / span)).toFloat()
            drawCircle(Emerald, 7f, Offset(x, y))
        }
    }
}

@Composable private fun WeightRow(record: WeightRecord, delete: () -> Unit) {
    ListItem(
        headlineContent = { Text("${record.weightKg.one()} kg", fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("${record.measuredAt.asDate()} · ${record.deviceName ?: "手动记录"}") },
        trailingContent = { IconButton(delete) { Icon(Icons.Outlined.Delete, "删除") } },
    )
    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
}
