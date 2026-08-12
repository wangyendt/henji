package com.qingheng.weight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingheng.weight.ble.BleState

@Composable
fun MeasureScreen(vm: AppViewModel, requestScan: () -> Unit) {
    val state by vm.ble.state.collectAsStateWithLifecycle()
    val devices by vm.ble.devices.collectAsStateWithLifecycle()
    val metrics by vm.ble.measurement.collectAsStateWithLifecycle()
    val packets by vm.ble.packets.collectAsStateWithLifecycle()
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
                    Text(if (metrics?.isStable == true) "测量完成" else "请赤脚站上体脂秤", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(metrics?.weightKg?.one() ?: "--", fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        Text(" kg", Modifier.padding(bottom = 9.dp))
                    }
                    if (metrics?.isStable == false) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    metrics?.impedanceOhm?.let { Text("阻抗 ${it.one()} Ω · ${if (metrics?.isEstimated == true) "身体成分由本机估算" else "设备直接数据"}", style = MaterialTheme.typography.bodySmall) }
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

