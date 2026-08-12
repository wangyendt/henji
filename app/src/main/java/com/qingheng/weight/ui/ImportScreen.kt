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
) {
    val importState by vm.fitdaysImportState.collectAsState()
    var manual by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("导入身体数据", "从 Fitdays 导入完整历史记录") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Fitdays 全部历史", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("体重、BMI、体脂、体水分、骨骼肌、基础代谢、骨量、蛋白质和身体年龄都会保留；重复导入会自动去重并更新。")
                    Text(
                        "导出路径：Fitdays → 图表 → 查看历史记录 → 全部 → 中间的导出图标 → 导出。分享文件时选择“轻衡”。",
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
                    label = { Text("体重 kg") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button({ manual.toDoubleOrNull()?.let(vm::addManualWeight); manual = "" }) { Text("保存") }
            }
        }
    }
}
