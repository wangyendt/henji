package com.qingheng.weight.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ImportScreen(
    vm: AppViewModel,
    chooseFitdaysFile: () -> Unit,
    openFitdays: () -> Unit,
    requestHealthConnectPermissions: () -> Unit,
) {
    val importState by vm.fitdaysImportState.collectAsState()
    val healthState by vm.healthSyncState.collectAsState()
    val settings by vm.settings.collectAsState()
    val unit = settings.weightUnit
    var manual by remember(unit) { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("导入身体数据", "从 Fitdays 导入完整历史记录") }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Health Connect 自动同步", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(healthState.description(), style = MaterialTheme.typography.bodyMedium)
                    when {
                        healthState.needsPermission() -> Button(requestHealthConnectPermissions, Modifier.fillMaxWidth()) {
                            Text("开启自动增量同步")
                        }
                        healthState is HealthSyncState.Syncing -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        healthState.permissionGranted() -> {
                            OutlinedButton(vm::syncHealthConnectNow, Modifier.fillMaxWidth()) { Text("立即同步新增数据") }
                            if (!healthState.backgroundEnabled()) {
                                TextButton(requestHealthConnectPermissions, Modifier.fillMaxWidth()) { Text("允许后台自动同步") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Fitdays 全部历史", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("体重、BMI、体脂、体水分、骨骼肌、基础代谢、骨量、蛋白质和身体年龄都会保留；重复导入会自动去重并更新。")
                    Text(
                        "导出路径：Fitdays → 图表 → 查看历史记录 → 全部 → 中间的导出图标 → 导出。分享文件时选择“衡迹”。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(chooseFitdaysFile, enabled = importState !is FitdaysImportState.Importing, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.FileDownload, null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (importState is FitdaysImportState.Importing) "正在导入全部历史…" else "选择 Fitdays 导出文件")
                    }
                    OutlinedButton(openFitdays, Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.OpenInNew, null)
                        Spacer(Modifier.width(7.dp))
                        Text("打开 Fitdays 去导出")
                    }
                    when (val result = importState) {
                        is FitdaysImportState.Success -> Text(
                            "完成：新增 ${result.summary.added} 条，更新 ${result.summary.updated} 条" +
                                if (result.summary.skipped > 0) "，跳过 ${result.summary.skipped} 行" else "",
                            color = Emerald,
                        )
                        is FitdaysImportState.Error -> Text(result.message, color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                }
            }
        }
        item {
            HorizontalDivider(Modifier.padding(20.dp))
            Text("手动记录体重", Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    manual,
                    { manual = it.filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("体重 ${unit.symbol}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button({ manual.toDoubleOrNull()?.let(vm::addManualWeight); manual = "" }) { Text("保存") }
            }
        }
    }
}

private fun HealthSyncState.description(): String = when (this) {
    HealthSyncState.Checking -> "正在检查连接状态…"
    HealthSyncState.Unavailable -> "这台手机暂不支持 Health Connect。"
    HealthSyncState.PermissionRequired -> "授权一次后，Fitdays 的新体重和体脂数据会自动进入衡迹。"
    HealthSyncState.Syncing -> "正在从 Health Connect 读取 Fitdays 新数据…"
    is HealthSyncState.Ready -> if (backgroundEnabled) "已连接，后台会定时同步。" else "已连接，打开衡迹时会自动同步。"
    is HealthSyncState.Success -> buildString {
        append("已连接，本次读取 $recordsRead 条")
        if (recordsChanged > 0) append("，更新 $recordsChanged 条")
        append(if (backgroundEnabled) "；后台自动同步已开启。" else "；打开衡迹时自动同步。")
    }
    is HealthSyncState.Error -> "同步遇到问题：$message"
}

private fun HealthSyncState.needsPermission(): Boolean =
    this is HealthSyncState.PermissionRequired

private fun HealthSyncState.permissionGranted(): Boolean = when (this) {
    is HealthSyncState.Ready, is HealthSyncState.Success -> true
    is HealthSyncState.Error -> permissionGranted
    else -> false
}

private fun HealthSyncState.backgroundEnabled(): Boolean = when (this) {
    is HealthSyncState.Ready -> backgroundEnabled
    is HealthSyncState.Success -> backgroundEnabled
    else -> false
}
