package com.qingheng.weight.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.qingheng.weight.data.MealFoodItem
import com.qingheng.weight.data.MealRecord
import com.qingheng.weight.meal.CodexTaskClient
import com.qingheng.weight.meal.MealAnalysis
import com.qingheng.weight.settings.AppSettings
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MealsScreen(vm: AppViewModel) {
    val meals by vm.meals.collectAsState()
    val foodFrequencies by vm.foodFrequencies.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    var selectedImage by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selectedImage = uri.toString(); showAdd = true
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) { selectedImage = pendingCameraUri?.toString(); showAdd = true }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) createMealPhotoUri(context)?.let { pendingCameraUri = it; camera.launch(it) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("饮食记录", "用照片留下真实的一餐") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({ picker.launch(arrayOf("image/*")) }, Modifier.weight(1f).height(54.dp)) {
                    Icon(Icons.Outlined.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text("选照片")
                }
                OutlinedButton({
                    if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        createMealPhotoUri(context)?.let { pendingCameraUri = it; camera.launch(it) }
                    } else cameraPermission.launch(Manifest.permission.CAMERA)
                }, Modifier.weight(1f).height(54.dp)) {
                    Icon(Icons.Outlined.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("拍一餐")
                }
            }
            Spacer(Modifier.height(18.dp))
            if (foodFrequencies.isNotEmpty()) {
                Text("常吃食物", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
                Text(
                    "同一种食物每餐只计一次",
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    foodFrequencies.take(12).forEach { food ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                "${food.canonicalName} ${food.mealCount}顿",
                                Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            Text("最近记录", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        }
        items(meals, key = { it.id }) { meal ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(12.dp)) {
                    if (meal.imageUri != null) AsyncImage(meal.imageUri, null, Modifier.size(82.dp), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(meal.foodNames, style = MaterialTheme.typography.titleSmall)
                        Text("${meal.calorieLow}–${meal.calorieHigh} kcal", color = Emerald)
                        Text(meal.advice, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Text(meal.createdAt.asDate(), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton({ vm.deleteMeal(meal) }) { Icon(Icons.Outlined.Delete, "删除") }
                }
            }
        }
        if (meals.isEmpty()) item { Text("还没有饮食记录。选择一张餐食照片开始。", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (showAdd) AddMealDialog(vm, settings, selectedImage) { showAdd = false; selectedImage = null }
}

private fun createMealPhotoUri(context: android.content.Context): Uri? = runCatching {
    val dir = File(context.filesDir, "meal_photos").apply { mkdirs() }
    val file = File(dir, "meal-${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()

@Composable private fun AddMealDialog(vm: AppViewModel, settings: AppSettings, imageUri: String?, dismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var calorieLow by remember { mutableStateOf("") }
    var calorieHigh by remember { mutableStateOf("") }
    var advice by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf<MealAnalysis?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun analyze() {
        if (imageUri == null) return
        if (settings.serviceToken.isBlank()) { error = "请先到“我的”中填写 CodexTask Service Token"; return }
        analyzing = true; error = null
        scope.launch {
            runCatching { CodexTaskClient(settings.serviceUrl, settings.serviceToken).analyze(context.contentResolver, Uri.parse(imageUri)) }
                .onSuccess { result ->
                    analysis = result; name = result.dishes.joinToString("、")
                    calorieLow = result.caloriesKcal.min.toInt().toString(); calorieHigh = result.caloriesKcal.max.toInt().toString()
                    advice = result.advice
                }
                .onFailure { error = it.message ?: "识别失败，请稍后重试" }
            analyzing = false
        }
    }

    LaunchedEffect(imageUri) { analyze() }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (analyzing) "正在识别这一餐…" else "记录这一餐") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            imageUri?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop) }
            if (analyzing) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            analysis?.foods?.takeIf { it.isNotEmpty() }?.let { foods ->
                Text(
                    "可统计食物：${foods.joinToString("、") { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Emerald,
                )
            }
            OutlinedTextField(name, { name = it }, label = { Text("菜品") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(calorieLow, { calorieLow = it.filter(Char::isDigit) }, label = { Text("最低 kcal") }, modifier = Modifier.weight(1f))
                Text("–")
                OutlinedTextField(calorieHigh, { calorieHigh = it.filter(Char::isDigit) }, label = { Text("最高 kcal") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(advice, { advice = it }, label = { Text("饮食建议") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (error != null) OutlinedButton(::analyze, enabled = !analyzing, modifier = Modifier.fillMaxWidth()) { Text("重新识别") }
        } },
        confirmButton = { Button({
            val low = calorieLow.toIntOrNull() ?: 0; val high = calorieHigh.toIntOrNull() ?: low
            val result = analysis
            val mealId = UUID.randomUUID().toString()
            val foodItems = result?.foods.orEmpty().mapIndexed { index, food ->
                MealFoodItem(
                    id = "$mealId:$index",
                    mealId = mealId,
                    canonicalName = food.name,
                    displayName = food.displayName,
                    category = food.category,
                    estimatedGramsLow = food.estimatedGrams.min,
                    estimatedGramsHigh = food.estimatedGrams.max,
                    calorieLow = food.caloriesKcal.min,
                    calorieHigh = food.caloriesKcal.max,
                    confidence = food.confidence,
                )
            }
            scope.launch {
                vm.saveMeal(MealRecord(
                    id = mealId, createdAt = System.currentTimeMillis(), mealType = "餐食",
                    imageUri = imageUri, foodNames = name.ifBlank { "未命名餐食" }, calorieLow = minOf(low, high), calorieHigh = maxOf(low, high),
                    proteinGrams = result?.proteinGrams?.let { (it.min + it.max) / 2 },
                    carbsGrams = result?.carbohydrateGrams?.let { (it.min + it.max) / 2 },
                    fatGrams = result?.fatGrams?.let { (it.min + it.max) / 2 },
                    advice = advice.ifBlank { "已记录，可补充蔬菜并注意份量。" }, confidence = if (result != null) 0.7 else null,
                    rawAnalysis = result?.toJsonString(),
                ), foodItems); dismiss()
            }
        }, enabled = name.isNotBlank() && !analyzing) { Text("保存") } },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}
