package com.qingheng.weight

import android.Manifest
import android.os.Build
import android.os.Bundle
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
    private var scanAfterPermission = false
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (scanAfterPermission && result.values.all { it }) viewModel.startScan()
        scanAfterPermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QingHengTheme { QingHengRoot(viewModel, ::requestScan) } }
    }

    private fun requestScan() {
        val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permissions.all { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) viewModel.startScan()
        else { scanAfterPermission = true; permissionLauncher.launch(permissions) }
    }
}

private data class Destination(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun QingHengRoot(vm: AppViewModel, requestScan: () -> Unit) {
    val nav = rememberNavController()
    val destinations = listOf(
        Destination("home", "首页", Icons.Outlined.Home), Destination("progress", "趋势", Icons.Outlined.ShowChart),
        Destination("measure", "称重", Icons.Outlined.BluetoothSearching), Destination("meals", "饮食", Icons.Outlined.Restaurant),
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
            composable("home") { DashboardScreen(vm, { nav.navigate("measure") }, { nav.navigate("meals") }) }
            composable("progress") { ProgressScreen(vm) }
            composable("measure") { MeasureScreen(vm, requestScan) }
            composable("meals") { MealsScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
