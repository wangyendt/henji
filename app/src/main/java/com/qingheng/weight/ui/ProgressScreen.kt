package com.qingheng.weight.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qingheng.weight.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private data class RangeOption(val title: String, val days: Int?)

private val rangeOptions = listOf(
    RangeOption("7 天", 7),
    RangeOption("30 天", 30),
    RangeOption("90 天", 90),
    RangeOption("全部", null),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(vm: AppViewModel) {
    val all by vm.weights.collectAsState()
    val settings by vm.settings.collectAsState()
    val unit = settings.weightUnit
    val earliestKg = remember(all) { earliestWeightKg(all) }
    val history = remember(all) { groupWeightRecordsByDay(all) }
    var metricName by rememberSaveable { mutableStateOf(TrendMetric.WEIGHT.name) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(1) }
    val metric = TrendMetric.valueOf(metricName)
    val allPoints = remember(all, metric) { dailyMetricPoints(all, metric) }
    val cutoff = rangeOptions[rangeIndex].days?.let { LocalDate.now().minusDays((it - 1).toLong()) }
    val points = remember(allPoints, cutoff) {
        cutoff?.let { date -> allPoints.filter { !it.date.isBefore(date) } } ?: allPoints
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item { ScreenHeader("身体趋势", "每天一个代表值，长期变化更清楚") }
        item {
            Text(
                "查看指标",
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TrendMetric.values(), key = { it.name }) { item ->
                    FilterChip(
                        selected = metric == item,
                        onClick = { metricName = item.name },
                        label = { Text(item.title) },
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                rangeOptions.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = rangeIndex == index,
                        onClick = { rangeIndex = index },
                        shape = SegmentedButtonDefaults.itemShape(index, rangeOptions.size),
                    ) { Text(option.title) }
                }
            }
            TrendCard(metric, points, unit, settings.hideAbsoluteWeight, earliestKg)
            DailyRuleNote()
            MeasurementCalendar(history, unit, settings.hideAbsoluteWeight, earliestKg, vm::deleteWeight)
        }
        if (all.isEmpty()) {
            item {
                Text(
                    "还没有记录，完成导入后这里会显示趋势和日历。",
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrendCard(
    metric: TrendMetric,
    points: List<DailyMetricPoint>,
    unit: WeightUnit,
    hideAbsoluteWeight: Boolean,
    earliestWeightKg: Double?,
) {
    val metricHidden = hideAbsoluteWeight && metric.hideInPrivacyMode
    val first = points.firstOrNull()
    val latest = points.lastOrNull()
    val change = if (first != null && latest != null && first != latest) latest.value - first.value else null
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (metric == TrendMetric.WEIGHT && hideAbsoluteWeight) "体重（相对最早）" else metric.title,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "每天最后一个有效值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (metricHidden) {
                    Text(
                        "已隐藏",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (latest != null) {
                    Text(
                        metric.format(latest.value, unit, hideAbsoluteWeight, earliestWeightKg),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Emerald,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (metricHidden) {
                Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "隐私模式下已隐藏${metric.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                MetricTrendChart(points, Modifier.fillMaxWidth().height(170.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        points.firstOrNull()?.date?.format(shortDateFormatter) ?: "--",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        points.lastOrNull()?.date?.format(shortDateFormatter) ?: "--",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    when {
                        points.isEmpty() -> "这段时间还没有${metric.title}数据"
                        change == null -> "1 个有数据的日期"
                        else -> "${points.size} 个有数据的日期 · 净变化 ${change.signed(metric, unit)}"
                    },
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MetricTrendChart(points: List<DailyMetricPoint>, modifier: Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    Canvas(modifier) {
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
        }
        if (points.isEmpty()) return@Canvas
        val rawMin = points.minOf { it.value }
        val rawMax = points.maxOf { it.value }
        val padding = ((rawMax - rawMin) * 0.12).coerceAtLeast(0.15)
        val minimum = rawMin - padding
        val span = (rawMax + padding - minimum).coerceAtLeast(0.3)
        val firstDay = points.first().date.toEpochDay()
        val daySpan = (points.last().date.toEpochDay() - firstDay).coerceAtLeast(1L)
        fun coordinate(index: Int, value: Double): Offset {
            val x = if (points.size == 1) size.width / 2f else {
                size.width * (points[index].date.toEpochDay() - firstDay).toFloat() / daySpan
            }
            val y = size.height * (1f - ((value - minimum) / span).toFloat())
            return Offset(x, y)
        }

        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { index, point ->
                val offset = coordinate(index, point.value)
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(path, Emerald, style = Stroke(width = 7f, cap = StrokeCap.Round))
        }
        points.forEachIndexed { index, point ->
            drawCircle(Emerald, 7f, coordinate(index, point.value))
        }
    }
}

@Composable
private fun DailyRuleNote() {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("同一天称多次时", fontWeight = FontWeight.SemiBold)
            Text(
                "体重取当天最后一次；体脂等其他指标取当天最后一个非空结果。日历中会保留当天全部记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun MeasurementCalendar(
    history: List<DailyWeightHistory>,
    unit: WeightUnit,
    hideAbsoluteWeight: Boolean,
    earliestWeightKg: Double?,
    delete: (WeightRecord) -> Unit,
) {
    val latestDate = history.firstOrNull()?.date ?: LocalDate.now()
    var monthKey by rememberSaveable { mutableIntStateOf(latestDate.year * 12 + latestDate.monthValue - 1) }
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(latestDate.toEpochDay()) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(latestDate) {
        if (!initialized && history.isNotEmpty()) {
            monthKey = latestDate.year * 12 + latestDate.monthValue - 1
            selectedEpochDay = latestDate.toEpochDay()
            initialized = true
        }
    }
    val month = YearMonth.of(monthKey / 12, monthKey % 12 + 1)
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
    val historyByDate = remember(history) { history.associateBy(DailyWeightHistory::date) }
    val selectedHistory = historyByDate[selectedDate]
    val normalizedRange = remember(history) { normalizedWeightRange(history) }

    fun moveMonth(delta: Int) {
        monthKey += delta
        val target = YearMonth.of(monthKey / 12, monthKey % 12 + 1)
        val firstRecorded = history.firstOrNull { YearMonth.from(it.date) == target }?.date
        selectedEpochDay = (firstRecorded ?: target.atDay(1)).toEpochDay()
    }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(vertical = 18.dp)) {
            Text(
                "称重日历",
                Modifier.padding(horizontal = 18.dp),
                fontWeight = FontWeight.Bold,
            )
            Text(
                "体重底色按全部历史记录统一换算，颜色越深代表体重越高",
                Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton({ moveMonth(-1) }) { Icon(Icons.Outlined.ChevronLeft, "上个月") }
                Text(
                    month.format(monthFormatter),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
                IconButton({ moveMonth(1) }) { Icon(Icons.Outlined.ChevronRight, "下个月") }
            }
            CalendarMonthGrid(
                month = month,
                selectedDate = selectedDate,
                historyByDate = historyByDate,
                normalizedRange = normalizedRange,
                unit = unit,
                hideAbsoluteWeight = hideAbsoluteWeight,
                earliestWeightKg = earliestWeightKg,
                onSelect = { selectedEpochDay = it.toEpochDay() },
            )
            HorizontalDivider(Modifier.padding(top = 10.dp))
            Text(
                "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 · ${selectedHistory?.records?.size ?: 0} 次",
                Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                fontWeight = FontWeight.Bold,
            )
            if (selectedHistory == null) {
                Text(
                    "这一天没有称重记录",
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                selectedHistory.records.forEach { record ->
                    MeasurementRow(record, unit, hideAbsoluteWeight, earliestWeightKg, delete)
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    historyByDate: Map<LocalDate, DailyWeightHistory>,
    normalizedRange: NormalizedWeightRange?,
    unit: WeightUnit,
    hideAbsoluteWeight: Boolean,
    earliestWeightKg: Double?,
    onSelect: (LocalDate) -> Unit,
) {
    val weekdayTitles = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        weekdayTitles.forEach {
            Text(
                it,
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cellCount = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        (0 until cellCount).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { index ->
                    val dayNumber = index - offset + 1
                    if (dayNumber !in 1..month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(0.88f))
                    } else {
                        val date = month.atDay(dayNumber)
                        val day = historyByDate[date]
                        CalendarDay(
                            date = date,
                            weightKg = day?.latest?.weightKg,
                            weightLevel = day?.latest?.weightKg?.let { normalizedRange?.level(it) },
                            unit = unit,
                            hideAbsoluteWeight = hideAbsoluteWeight,
                            earliestWeightKg = earliestWeightKg,
                            recordCount = day?.records?.size ?: 0,
                            selected = date == selectedDate,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f).aspectRatio(0.88f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    weightKg: Double?,
    weightLevel: Float?,
    unit: WeightUnit,
    hideAbsoluteWeight: Boolean,
    earliestWeightKg: Double?,
    recordCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        weightKg != null -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    Column(
        modifier.clip(RoundedCornerShape(11.dp)).background(background).clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}${if (recordCount > 1) " ×$recordCount" else ""}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (weightKg != null) {
            val level = weightLevel ?: 0.5f
            Surface(color = heatColor(level), shape = RoundedCornerShape(7.dp)) {
                Text(
                    unit.displayedValueFromKg(weightKg, hideAbsoluteWeight, earliestWeightKg) ?: "--",
                    Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (level >= 0.55f) Color.White else Color(0xFF174B3B),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        } else {
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun MeasurementRow(
    record: WeightRecord,
    unit: WeightUnit,
    hideAbsoluteWeight: Boolean,
    earliestWeightKg: Double?,
    delete: (WeightRecord) -> Unit,
) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    unit.displayedWeightFromKg(record.weightKg, hideAbsoluteWeight, earliestWeightKg) ?: "--",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildList {
                        add(record.measuredAt.asDate("HH:mm"))
                        if (!hideAbsoluteWeight) record.bodyFatPercent?.let { add("体脂 ${it.one()}%") }
                        add(record.deviceName ?: "手动记录")
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "详情") }
            IconButton(onClick = { delete(record) }) { Icon(Icons.Outlined.Delete, "删除") }
        }
        if (expanded) {
            RecordMetricDetails(record, unit, hideAbsoluteWeight, earliestWeightKg)
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 18.dp))
}

@Composable
private fun RecordMetricDetails(
    record: WeightRecord,
    unit: WeightUnit,
    hideAbsoluteWeight: Boolean,
    earliestWeightKg: Double?,
) {
    val metrics = TrendMetric.values().mapNotNull { metric ->
        if (hideAbsoluteWeight && metric.hideInPrivacyMode) return@mapNotNull null
        metric.valueOf(record)?.let {
            metric.title to metric.format(it, unit, hideAbsoluteWeight, earliestWeightKg)
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.forEach { (title, value) ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text(title, style = MaterialTheme.typography.labelSmall)
                    Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun TrendMetric.format(
    value: Double,
    weightUnit: WeightUnit,
    hideAbsoluteWeight: Boolean = false,
    earliestWeightKg: Double? = null,
): String {
    if (this == TrendMetric.WEIGHT) {
        return weightUnit.displayedWeightFromKg(value, hideAbsoluteWeight, earliestWeightKg) ?: "--"
    }
    if (isMass) return weightUnit.weightFromKg(value)
    val number = when (this) {
        TrendMetric.BMR, TrendMetric.BODY_AGE -> value.roundToInt().toString()
        else -> value.one()
    }
    return if (unit.isBlank()) number else "$number $unit"
}

private fun Double.signed(metric: TrendMetric, weightUnit: WeightUnit): String =
    if (metric.isMass) weightUnit.signedWeightFromKg(this)
    else "${if (this > 0) "+" else ""}${metric.format(this, weightUnit)}"

private fun heatColor(level: Float): Color = lerp(
    Color(0xFFDDF4EA),
    Color(0xFF087A56),
    (0.12f + level * 0.88f).coerceIn(0f, 1f),
)

private val shortDateFormatter = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
private val monthFormatter = DateTimeFormatter.ofPattern("yyyy年 M月", Locale.CHINA)
