package com.qingheng.weight.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qingheng.weight.data.Sex
import com.qingheng.weight.data.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val current by vm.settings.collectAsState()
    var height by remember(current.profile.heightCm) { mutableStateOf(current.profile.heightCm.toString()) }
    var year by remember(current.profile.birthYear) { mutableStateOf(current.profile.birthYear.toString()) }
    val unit = current.weightUnit
    var goal by remember(current.profile.goalWeightKg, unit) {
        mutableStateOf(unit.valueFromKg(current.profile.goalWeightKg))
    }
    var sex by remember(current.profile.sex) { mutableStateOf(current.profile.sex) }
    var url by remember(current.serviceUrl) { mutableStateOf(current.serviceUrl) }
    var token by remember(current.serviceToken) { mutableStateOf(current.serviceToken) }
    var personalUrl by remember(current.personalSyncUrl) { mutableStateOf(current.personalSyncUrl) }
    var personalToken by remember(current.personalSyncToken) { mutableStateOf(current.personalSyncToken) }
    val personalSyncState by vm.personalSyncState.collectAsState()
    val pendingCount by vm.syncPendingCount.collectAsState()
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
            if (current.hideAbsoluteWeight) {
                Text(
                    "目标体重已隐藏，显示实际体重后可查看和修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    goal,
                    { goal = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("目标体重 ${unit.symbol}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            Button({
                val goalKg = if (current.hideAbsoluteWeight) current.profile.goalWeightKg
                    else goal.toDoubleOrNull()?.let(unit::toKilograms) ?: current.profile.goalWeightKg
                vm.updateProfile(UserProfile(height.toIntOrNull()?.coerceIn(100, 230) ?: 170, year.toIntOrNull()?.coerceIn(1920, 2020) ?: 1990, sex, current.profile.activityLevel, goalKg)); saved = true
            }, Modifier.fillMaxWidth()) { Text("保存身体资料") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("CodexTask 饮食识别", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("填写 CodexTask 服务的完整 HTTPS 地址或局域网地址，以及专用令牌。反向代理地址可以包含路径前缀。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(url, { url = it }, label = { Text("服务地址") }, placeholder = { Text("https://example.com/services/codex-task") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(token, { token = it }, label = { Text("Service Token") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button({ vm.updateService(url, token); saved = true }, Modifier.fillMaxWidth()) { Text("保存服务配置") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("个人数据同步", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "体重、身体成分、饮食和卡路里会同步到你的 hx470。饮食照片不会上传；本机删除会同步为服务端墓碑，换手机后可恢复未删除的数据。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                personalUrl,
                { personalUrl = it },
                label = { Text("同步服务地址") },
                placeholder = { Text("https://example.com/services/henji-sync") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                personalToken,
                { personalToken = it },
                label = { Text("同步 Token") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                if (pendingCount == 0) "本机没有待上传变更" else "待上传 $pendingCount 项变更",
                style = MaterialTheme.typography.bodySmall,
                color = if (pendingCount == 0) Emerald else MaterialTheme.colorScheme.primary,
            )
            when (val state = personalSyncState) {
                PersonalSyncState.Checking -> Text("正在检查同步状态…", style = MaterialTheme.typography.bodySmall)
                PersonalSyncState.Disabled -> Text("填写 Token 后启用同步", style = MaterialTheme.typography.bodySmall)
                PersonalSyncState.Syncing -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is PersonalSyncState.Success -> Text(
                    "同步完成：上传 ${state.result.uploaded} 项，接收 ${state.result.downloaded} 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = Emerald,
                )
                is PersonalSyncState.Error -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                {
                    vm.updatePersonalSync(personalUrl, personalToken)
                    saved = true
                },
                Modifier.fillMaxWidth(),
                enabled = personalSyncState !is PersonalSyncState.Syncing,
            ) { Text(if (personalSyncState is PersonalSyncState.Syncing) "正在同步…" else "保存并立即同步") }
        }
        Card(Modifier.fillMaxWidth().padding(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text("饮食照片仅在你主动识别时发送到 CodexTask，不进入个人同步数据库。身体成分不是医疗诊断结果。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (saved) LaunchedEffect(Unit) { kotlinx.coroutines.delay(1800); saved = false }
        if (saved) Text("已保存", Modifier.padding(horizontal = 20.dp), color = Emerald)
        Spacer(Modifier.height(24.dp))
    }
}
