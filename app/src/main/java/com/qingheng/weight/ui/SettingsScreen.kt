package com.qingheng.weight.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingheng.weight.data.Sex
import com.qingheng.weight.data.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val current by vm.settings.collectAsStateWithLifecycle()
    var height by remember(current.profile.heightCm) { mutableStateOf(current.profile.heightCm.toString()) }
    var year by remember(current.profile.birthYear) { mutableStateOf(current.profile.birthYear.toString()) }
    var goal by remember(current.profile.goalWeightKg) { mutableStateOf(current.profile.goalWeightKg.toString()) }
    var sex by remember(current.profile.sex) { mutableStateOf(current.profile.sex) }
    var url by remember(current.serviceUrl) { mutableStateOf(current.serviceUrl) }
    var token by remember(current.serviceToken) { mutableStateOf(current.serviceToken) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("我的", "个人资料决定身体成分估算结果")
        Text("身体资料", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(height, { height = it.filter(Char::isDigit) }, label = { Text("身高 cm") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(year, { year = it.filter(Char::isDigit) }, label = { Text("出生年份") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Sex.entries.forEachIndexed { index, value ->
                    SegmentedButton(selected = sex == value, onClick = { sex = value }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(if (value == Sex.MALE) "男" else "女") }
                }
            }
            OutlinedTextField(goal, { goal = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("目标体重 kg") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button({
                vm.updateProfile(UserProfile(height.toIntOrNull()?.coerceIn(100, 230) ?: 170, year.toIntOrNull()?.coerceIn(1920, 2020) ?: 1990, sex, current.profile.activityLevel, goal.toDoubleOrNull() ?: 65.0)); saved = true
            }, Modifier.fillMaxWidth()) { Text("保存身体资料") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("CodexTask 饮食识别", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("真机请把 10.0.2.2 改成运行服务的电脑局域网地址；模拟器可直接使用默认地址。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(url, { url = it }, label = { Text("服务地址") }, placeholder = { Text("http://10.0.2.2:7777") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(token, { token = it }, label = { Text("Service Token") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button({ vm.updateService(url, token); saved = true }, Modifier.fillMaxWidth()) { Text("保存服务配置") }
        }
        Card(Modifier.fillMaxWidth().padding(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text("数据默认只保存在本机。照片仅在你主动点击识别时发送到所配置的 CodexTask 服务。身体成分不是医疗诊断结果。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (saved) LaunchedEffect(Unit) { kotlinx.coroutines.delay(1800); saved = false }
        if (saved) Text("已保存", Modifier.padding(horizontal = 20.dp), color = Emerald)
        Spacer(Modifier.height(24.dp))
    }
}
