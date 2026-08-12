package com.qingheng.weight

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.qingheng.weight.ui.*

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()
    private val fitdaysFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.importFitdaysHistory(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSharedFitdaysFile(intent)
        setContent {
            QingHengTheme {
                QingHengRoot(viewModel, ::chooseFitdaysFile, ::openFitdays)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedFitdaysFile(intent)
    }

    private fun chooseFitdaysFile() = fitdaysFileLauncher.launch(
        arrayOf("text/csv", "application/csv", "application/vnd.ms-excel", "application/octet-stream")
    )

    private fun openFitdays() {
        val launcher = ComponentName(
            "cn.fitdays.fitdays",
            "cn.fitdays.fitdays.mvp.ui.activity.SplashActivity1",
        )
        runCatching { startActivity(Intent.makeMainActivity(launcher)) }
            .onFailure { Toast.makeText(this, "没有找到 Fitdays，请确认已经安装", Toast.LENGTH_LONG).show() }
    }

    private fun handleSharedFitdaysFile(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        uri?.let(viewModel::importFitdaysHistory)
    }

}

private data class Destination(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun QingHengRoot(
    vm: AppViewModel,
    chooseFitdaysFile: () -> Unit,
    openFitdays: () -> Unit,
) {
    val nav = rememberNavController()
    val destinations = listOf(
        Destination("home", "首页", Icons.Outlined.Home), Destination("progress", "趋势", Icons.Outlined.ShowChart),
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
                        onClick = { nav.navigate(item.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(item.icon, item.label) }, label = { Text(item.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { DashboardScreen(vm, { nav.navigate("import") }, { nav.navigate("meals") }) }
            composable("progress") { ProgressScreen(vm) }
            composable("import") { ImportScreen(vm, chooseFitdaysFile, openFitdays) }
            composable("meals") { MealsScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
