package com.qingheng.weight

import android.content.ComponentName
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.qingheng.weight.ui.*
import com.qingheng.weight.share.ShareCardContent
import com.qingheng.weight.share.ShareCardRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()
    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { viewModel.onHealthPermissionsResult() }
    private val fitdaysFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.importFitdaysHistory(it)
        }
    }
    private val healthScreenshotLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importVivoHealthScreenshot)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A task restored after process death can retain its original ACTION_SEND intent.
        // Only a fresh activity launch represents a new share; restored UI state must not replay it.
        if (savedInstanceState == null) handleSharedContent(intent) else clearSharedContent(intent)
        setContent {
            HengJiTheme {
                HengJiRoot(
                    viewModel,
                    ::chooseFitdaysFile,
                    ::chooseHealthScreenshot,
                    ::openFitdays,
                    ::requestHealthConnectPermissions,
                    ::shareCard,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshHealthConnect()
        viewModel.syncPersonalData()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedContent(intent)
    }

    private fun chooseFitdaysFile() = fitdaysFileLauncher.launch(
        arrayOf("text/csv", "application/csv", "application/vnd.ms-excel", "application/octet-stream")
    )

    private fun chooseHealthScreenshot() = healthScreenshotLauncher.launch(arrayOf("image/*"))

    private fun requestHealthConnectPermissions() {
        val permissions = viewModel.healthPermissionsToRequest()
        if (permissions.isEmpty()) {
            Toast.makeText(this, "这台手机暂不支持 Health Connect", Toast.LENGTH_LONG).show()
        } else {
            healthPermissionLauncher.launch(permissions)
        }
    }

    private fun openFitdays() {
        val launcher = ComponentName(
            "cn.fitdays.fitdays",
            "cn.fitdays.fitdays.mvp.ui.activity.SplashActivity1",
        )
        runCatching { startActivity(Intent.makeMainActivity(launcher)) }
            .onFailure { Toast.makeText(this, "没有找到 Fitdays，请确认已经安装", Toast.LENGTH_LONG).show() }
    }

    private fun handleSharedContent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        uri ?: return
        val mimeType = intent.type ?: contentResolver.getType(uri).orEmpty()
        if (mimeType.startsWith("image/")) {
            viewModel.importVivoHealthScreenshot(uri)
        } else {
            viewModel.importFitdaysHistory(uri)
        }
        clearSharedContent(intent)
    }

    private fun clearSharedContent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        intent.action = null
        intent.removeExtra(Intent.EXTRA_STREAM)
    }

    private fun shareCard(content: ShareCardContent) {
        lifecycleScope.launch {
            val uri = runCatching {
                withContext(Dispatchers.IO) { ShareCardRenderer(this@MainActivity).render(content) }
            }.getOrElse { error ->
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "生成分享图片失败",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, content.shareText)
                clipData = ClipData.newUri(contentResolver, content.shareText, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "分享到微信、朋友圈或抖音").apply {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(componentName))
            }
            startActivity(chooser)
        }
    }

}

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private const val HOME_ROUTE = "home"

private fun NavHostController.openTopLevel(route: String) {
    if (route == HOME_ROUTE) {
        if (currentDestination?.route != HOME_ROUTE) popBackStack(HOME_ROUTE, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun HengJiRoot(
    vm: AppViewModel,
    chooseFitdaysFile: () -> Unit,
    chooseHealthScreenshot: () -> Unit,
    openFitdays: () -> Unit,
    requestHealthConnectPermissions: () -> Unit,
    shareCard: (ShareCardContent) -> Unit,
) {
    val nav = rememberNavController()
    val destinations = listOf(
        Destination(HOME_ROUTE, "首页", Icons.Outlined.Home), Destination("progress", "趋势", Icons.Outlined.ShowChart),
        Destination("import", "导入", Icons.Outlined.FileDownload), Destination("meals", "饮食", Icons.Outlined.Restaurant),
        Destination("settings", "我的", Icons.Outlined.Person),
    )
    val backStack by nav.currentBackStackEntryAsState()
    Scaffold(
        bottomBar = {
            NavigationBar(tonalElevation = 0.dp) {
                destinations.forEach { item ->
                    NavigationBarItem(
                        selected = backStack?.destination?.route == item.route,
                        onClick = { nav.openTopLevel(item.route) },
                        icon = { Icon(item.icon, item.label) }, label = { Text(item.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = HOME_ROUTE, modifier = Modifier.padding(padding)) {
            composable(HOME_ROUTE) { DashboardScreen(vm, { nav.openTopLevel("import") }, { nav.openTopLevel("meals") }) }
            composable("progress") { ProgressScreen(vm, shareCard) }
            composable("import") {
                ImportScreen(
                    vm = vm,
                    chooseFitdaysFile = chooseFitdaysFile,
                    chooseHealthScreenshot = chooseHealthScreenshot,
                    openFitdays = openFitdays,
                    requestHealthConnectPermissions = requestHealthConnectPermissions,
                )
            }
            composable("meals") { MealsScreen(vm, shareCard) }
            composable("settings") { SettingsScreen(vm) }
        }
        HealthScreenshotImportDialog(vm)
    }
}

@Composable
private fun HealthScreenshotImportDialog(vm: AppViewModel) {
    val state by vm.healthScreenshotImportState.collectAsState()
    when (val current = state) {
        HealthScreenshotImportState.Idle -> Unit
        HealthScreenshotImportState.Duplicate -> AlertDialog(
            onDismissRequest = vm::dismissHealthScreenshotImport,
            title = { Text("运动截图已导入") },
            text = { Text("这张截图之前已经成功导入，不再重复上传和解析。") },
            confirmButton = { TextButton(vm::dismissHealthScreenshotImport) { Text("知道了") } },
        )
        HealthScreenshotImportState.Analyzing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在解析 vivo 健康运动图") },
            text = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("正在识别运动类型、时间、距离、热量和心率…")
                }
            },
            confirmButton = {},
        )
        is HealthScreenshotImportState.Error -> AlertDialog(
            onDismissRequest = vm::dismissHealthScreenshotImport,
            title = { Text("截图导入失败") },
            text = { Text(current.message) },
            confirmButton = { TextButton(vm::dismissHealthScreenshotImport) { Text("关闭") } },
        )
        is HealthScreenshotImportState.Success -> AlertDialog(
            onDismissRequest = vm::dismissHealthScreenshotImport,
            title = { Text("运动记录已录入") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.records.forEach { record ->
                        Text("${record.startAt.asDate()}  ${record.summaryText()}")
                    }
                    Text(
                        when {
                            current.uploaded -> "结构化数据已自动上传到个人数据库。"
                            current.syncMessage != null -> "本地已保存，服务器同步已排队：${current.syncMessage}"
                            else -> "本地已保存；配置个人同步后会自动上传。"
                        },
                        color = if (current.uploaded) Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    current.warnings.takeIf { it.isNotEmpty() }?.let {
                        Text("识别提示：${it.joinToString("；")}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(vm::dismissHealthScreenshotImport) { Text("完成") } },
        )
    }
}
