package com.qingheng.weight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingheng.weight.ble.BleState

@Composable
fun MeasureScreen(
    vm: AppViewModel,
    requestScan: () -> Unit,
    chooseFitdaysFile: () -> Unit,
    openFitdays: () -> Unit,
) {
    val state by vm.ble.state.collectAsState()
    val devices by vm.ble.devices.collectAsState()
    val metrics by vm.ble.measurement.collectAsState()
    val packets by vm.ble.packets.collectAsState()
    val importState by vm.fitdaysImportState.collectAsState()
    var manual by remember { mutableStateOf("") }
    var showPackets by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("实时称重", state.title()) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(58.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Bluetooth, null, tint = Emerald, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when {
                            metrics?.isStable != true -> "请赤脚站上体脂秤"
                            metrics?.isEstimated == true -> "体重测量完成"
                            else -> "身体成分测量完成"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(metrics?.weightKg?.one() ?: "--", fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        Text(" kg", Modifier.padding(bottom = 9.dp))
                    }
                    if (metrics?.isStable == false) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    metrics?.let {
                        Text(
                            if (it.isEstimated) "仅收到体重 · 下方身体成分为本机估算"
                            else "阻抗 ${it.impedanceOhm?.one() ?: "--"} Ω · 身体指标由本机计算",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(requestScan, Modifier.weight(1f)) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text(if (state is BleState.Scanning) "重新扫描" else "扫描体脂秤") }
                if (state is BleState.Connected) OutlinedButton(vm.ble::disconnect, Modifier.weight(1f)) { Text("断开") }
            }
        }
        if (metrics != null) item {
            Text("本次数据", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("体脂率", metrics?.bodyFatPercent?.one() ?: "--", "%", modifier = Modifier.weight(1f))
                    MetricCard("体水分", metrics?.bodyWaterPercent?.one() ?: "--", "%", modifier = Modifier.weight(1f))
                    MetricCard("基础代谢", metrics?.bmrKcal?.toString() ?: "--", "kcal", modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("肌肉量", metrics?.muscleMassKg?.one() ?: "--", "kg", modifier = Modifier.weight(1f))
                    MetricCard("骨量", metrics?.boneMassKg?.one() ?: "--", "kg", modifier = Modifier.weight(1f))
                    MetricCard("身体年龄", metrics?.bodyAge?.toString() ?: "--", "岁", modifier = Modifier.weight(1f))
                }
            }
        }
        if (devices.isNotEmpty()) item { Text("附近设备", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold) }
        items(devices, key = { it.address }) { device ->
            ListItem(
                headlineContent = { Text(device.name) }, supportingContent = { Text("${device.protocol} · 信号 ${device.rssi} dBm") },
                trailingContent = { FilledTonalButton({ vm.connect(device) }) { Text("连接") } },
            )
        }
        item {
            Text("Fitdays 全部历史", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("首次导入全部记录，重复导入会自动去重并更新。体重、BMI、体脂、体水分、骨骼肌、基础代谢等会一起保留。", style = MaterialTheme.typography.bodyMedium)
                    Text("先在 Fitdays 的历史页选择“全部数据”并导出；分享时可直接选“轻衡”，也可以回到这里选择导出文件。", style = MaterialTheme.typography.bodySmall)
                    Button(chooseFitdaysFile, enabled = importState !is FitdaysImportState.Importing, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.FileDownload, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (importState is FitdaysImportState.Importing) "正在导入全部历史…" else "导入 Fitdays 全部历史")
                    }
                    OutlinedButton(openFitdays, Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.OpenInNew, null)
                        Spacer(Modifier.width(6.dp))
                        Text("打开 Fitdays 去导出")
                    }
                    when (val result = importState) {
                        is FitdaysImportState.Success -> Text(
                            "完成：新增 ${result.summary.added} 条，更新 ${result.summary.updated} 条" +
                                if (result.summary.skipped > 0) "，跳过 ${result.summary.skipped} 行" else "",
                            color = Emerald,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        is FitdaysImportState.Error -> Text(result.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        else -> Unit
                    }
                }
            }
        }
        item {
            HorizontalDivider(Modifier.padding(20.dp))
            Text("手动记录", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(manual, { manual = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("体重 kg") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Button({ manual.toDoubleOrNull()?.let(vm::addManualWeight); manual = "" }) { Text("保存") }
            }
            TextButton({ showPackets = !showPackets }, Modifier.padding(horizontal = 12.dp)) { Text(if (showPackets) "收起协议调试信息" else "查看协议调试信息") }
            if (showPackets) Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("最近原始数据包", fontWeight = FontWeight.Bold)
                    packets.take(12).forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
                    if (packets.isEmpty()) Text("连接后会显示特征值与十六进制数据，方便适配未知型号。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun BleState.title() = when (this) {
    BleState.Idle -> "等待连接体脂秤"
    BleState.Scanning -> "正在寻找附近体脂秤…"
    is BleState.Connecting -> "正在连接 $name"
    is BleState.Connected -> "已连接 $name"
    is BleState.Error -> message
}
