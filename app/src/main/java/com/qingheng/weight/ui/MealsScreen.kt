package com.qingheng.weight.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.qingheng.weight.data.FoodMealFrequency
import com.qingheng.weight.data.MealFoodItem
import com.qingheng.weight.data.MealRecord
import com.qingheng.weight.meal.CodexTaskClient
import com.qingheng.weight.meal.MealAnalysis
import com.qingheng.weight.settings.AppSettings
import com.qingheng.weight.share.ShareCardContent
import com.qingheng.weight.share.toMealShareCard
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MealsScreen(vm: AppViewModel, shareCard: (ShareCardContent) -> Unit = {}) {
    val meals by vm.meals.collectAsState()
    val allFoodItems by vm.mealFoodItems.collectAsState()
    val foodFrequencies by vm.foodFrequencies.collectAsState()
    val settings by vm.settings.collectAsState()
    val foodsByMeal = remember(allFoodItems) { allFoodItems.groupBy(MealFoodItem::mealId) }
    val context = LocalContext.current
    var selectedImage by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedMealId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }

    LaunchedEffect(meals) {
        val validIds = meals.mapTo(mutableSetOf(), MealRecord::id)
        selectedIds = selectedIds.intersect(validIds)
        if (selectedMealId !in validIds) selectedMealId = null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selectedImage = uri.toString()
            showAdd = true
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            selectedImage = pendingCameraUri?.toString()
            showAdd = true
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) createMealPhotoUri(context)?.let { pendingCameraUri = it; camera.launch(it) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { ScreenHeader("饮食记录", "每一餐，都变成看得见的习惯") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button({ picker.launch(arrayOf("image/*")) }, Modifier.weight(1f).height(54.dp)) {
                    Icon(Icons.Outlined.AddAPhoto, null)
                    Spacer(Modifier.width(6.dp))
                    Text("选照片")
                }
                OutlinedButton({
                    if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        createMealPhotoUri(context)?.let { pendingCameraUri = it; camera.launch(it) }
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }, Modifier.weight(1f).height(54.dp)) {
                    Icon(Icons.Outlined.CameraAlt, null)
                    Spacer(Modifier.width(6.dp))
                    Text("拍一餐")
                }
            }
            if (foodFrequencies.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                FoodFrequencySection(foodFrequencies)
            }
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("最近记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (selectionMode) {
                        Text(
                            "已选择 ${selectedIds.size} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = Emerald,
                        )
                    }
                }
                if (meals.isNotEmpty()) {
                    TextButton(onClick = {
                        selectionMode = !selectionMode
                        if (!selectionMode) selectedIds = emptySet()
                    }) { Text(if (selectionMode) "完成" else "批量管理") }
                }
            }
            if (selectionMode) {
                BatchActionBar(
                    selectedCount = selectedIds.size,
                    allSelected = selectedIds.size == meals.size,
                    onToggleAll = { selectedIds = if (selectedIds.size == meals.size) emptySet() else meals.mapTo(mutableSetOf(), MealRecord::id) },
                    onDelete = { confirmBatchDelete = true },
                )
            }
        }
        items(meals, key = MealRecord::id) { meal ->
            MealRecordCard(
                meal = meal,
                foods = foodsByMeal[meal.id].orEmpty(),
                selectionMode = selectionMode,
                selected = meal.id in selectedIds,
                onClick = {
                    if (selectionMode) {
                        selectedIds = if (meal.id in selectedIds) selectedIds - meal.id else selectedIds + meal.id
                    } else {
                        selectedMealId = meal.id
                    }
                },
                onShare = { shareCard(meal.toMealShareCard()) },
            )
        }
        if (meals.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.Restaurant, null, Modifier.size(42.dp), tint = Emerald)
                    Spacer(Modifier.height(12.dp))
                    Text("还没有饮食记录", fontWeight = FontWeight.Bold)
                    Text("选一张餐食照片，开始积累你的食物账本。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showAdd) AddMealDialog(vm, settings, selectedImage) {
        showAdd = false
        selectedImage = null
    }

    meals.firstOrNull { it.id == selectedMealId }?.let { meal ->
        MealDetailSheet(
            meal = meal,
            foods = foodsByMeal[meal.id].orEmpty(),
            onDismiss = { selectedMealId = null },
            onDelete = { vm.deleteMeal(meal) },
        )
    }

    if (confirmBatchDelete) {
        val records = meals.filter { it.id in selectedIds }
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("删除 ${records.size} 条饮食记录？") },
            text = { Text("这些记录的照片引用、食物明细和统计次数会一起移除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMeals(records)
                    selectedIds = emptySet()
                    selectionMode = false
                    confirmBatchDelete = false
                }, enabled = records.isNotEmpty()) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ confirmBatchDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FoodFrequencySection(frequencies: List<FoodMealFrequency>) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("常吃食物", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "数字表示出现过的餐数，同一餐只计一次",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            frequencies.take(12).forEach { food ->
                val accent = mealCategoryColor(food.category)
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(food.canonicalName, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(9.dp))
                        Box(
                            Modifier.size(30.dp).background(accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                food.mealCount.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onToggleAll) { Text(if (allSelected) "取消全选" else "全选") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onDelete,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Outlined.DeleteSweep, null)
                Spacer(Modifier.width(5.dp))
                Text("删除 $selectedCount")
            }
        }
    }
}

@Composable
private fun MealRecordCard(
    meal: MealRecord,
    foods: List<MealFoodItem>,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(selected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(4.dp))
            }
            if (meal.imageUri != null) {
                AsyncImage(
                    meal.imageUri,
                    meal.foodNames,
                    Modifier.size(92.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier.size(92.dp).background(Mint, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Restaurant, null, tint = Emerald) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        meal.mealType,
                        style = MaterialTheme.typography.labelMedium,
                        color = Emerald,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " · ${meal.createdAt.asDate("M月d日 HH:mm")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    meal.foodNames,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${calorieRange(meal.calorieLow, meal.calorieHigh)} kcal",
                    color = Emerald,
                    fontWeight = FontWeight.Bold,
                )
                if (foods.isNotEmpty()) {
                    Text(
                        foods.take(4).joinToString(" · ", transform = MealFoodItem::canonicalName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!selectionMode) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, "分享这条饮食记录", tint = Emerald)
                }
                Icon(Icons.Outlined.ChevronRight, "查看详情", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun createMealPhotoUri(context: android.content.Context): Uri? = runCatching {
    val dir = File(context.filesDir, "meal_photos").apply { mkdirs() }
    val file = File(dir, "meal-${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}.getOrNull()

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AddMealDialog(vm: AppViewModel, settings: AppSettings, imageUri: String?, dismiss: () -> Unit) {
    val context = LocalContext.current
    val initialDateTime = remember { java.time.ZonedDateTime.now() }
    var name by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(defaultMealType(initialDateTime.toLocalTime())) }
    var mealTypeManuallyChanged by remember { mutableStateOf(false) }
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(initialDateTime.toLocalDate().toEpochDay()) }
    var selectedHour by rememberSaveable { mutableIntStateOf(initialDateTime.hour) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(initialDateTime.minute) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var calorieLow by remember { mutableStateOf("") }
    var calorieHigh by remember { mutableStateOf("") }
    var advice by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf<MealAnalysis?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun analyze() {
        if (imageUri == null) return
        if (settings.serviceToken.isBlank()) {
            error = "请先到“我的”中填写 CodexTask Service Token"
            return
        }
        analyzing = true
        error = null
        scope.launch {
            runCatching {
                CodexTaskClient(settings.serviceUrl, settings.serviceToken, model = settings.serviceModel, reasoning = settings.serviceReasoning)
                    .analyze(context.contentResolver, Uri.parse(imageUri))
            }.onSuccess { result ->
                analysis = result
                name = result.dishes.joinToString("、")
                calorieLow = result.caloriesKcal.min.toInt().toString()
                calorieHigh = result.caloriesKcal.max.toInt().toString()
                advice = result.advice
            }.onFailure { error = it.message ?: "识别失败，请稍后重试" }
            analyzing = false
        }
    }

    LaunchedEffect(imageUri) { analyze() }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (analyzing) "正在识别这一餐…" else "记录这一餐") },
        text = {
            Column(
                Modifier.heightIn(max = 610.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                MealScheduleEditor(
                    date = LocalDate.ofEpochDay(selectedEpochDay),
                    hour = selectedHour,
                    minute = selectedMinute,
                    mealType = mealType,
                    onPickDate = { showDatePicker = true },
                    onPickTime = { showTimePicker = true },
                    onSelectType = {
                        mealType = it
                        mealTypeManuallyChanged = true
                    },
                    onRenameType = {
                        mealType = it
                        mealTypeManuallyChanged = true
                    },
                )
                OutlinedTextField(name, { name = it }, label = { Text("菜品") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(calorieLow, { calorieLow = it.filter(Char::isDigit) }, label = { Text("最低 kcal") }, modifier = Modifier.weight(1f))
                    Text("–")
                    OutlinedTextField(calorieHigh, { calorieHigh = it.filter(Char::isDigit) }, label = { Text("最高 kcal") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(advice, { advice = it }, label = { Text("饮食建议") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                if (error != null) {
                    OutlinedButton(::analyze, enabled = !analyzing, modifier = Modifier.fillMaxWidth()) { Text("重新识别") }
                }
            }
        },
        confirmButton = {
            Button({
                val low = calorieLow.toIntOrNull() ?: 0
                val high = calorieHigh.toIntOrNull() ?: low
                val result = analysis
                val mealId = UUID.randomUUID().toString()
                val createdAt = LocalDate.ofEpochDay(selectedEpochDay)
                    .atTime(selectedHour, selectedMinute)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
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
                    vm.saveMeal(
                        MealRecord(
                            id = mealId,
                            createdAt = createdAt,
                            mealType = mealType.ifBlank { defaultMealType(LocalTime.of(selectedHour, selectedMinute)) },
                            imageUri = imageUri,
                            foodNames = name.ifBlank { "未命名餐食" },
                            calorieLow = minOf(low, high),
                            calorieHigh = maxOf(low, high),
                            proteinGrams = result?.proteinGrams?.let { (it.min + it.max) / 2 },
                            carbsGrams = result?.carbohydrateGrams?.let { (it.min + it.max) / 2 },
                            fatGrams = result?.fatGrams?.let { (it.min + it.max) / 2 },
                            advice = advice.ifBlank { "已记录，可补充蔬菜并注意份量。" },
                            confidence = if (result != null) 0.7 else null,
                            rawAnalysis = result?.toJsonString(),
                        ),
                        foodItems,
                    )
                    dismiss()
                }
            }, enabled = name.isNotBlank() && !analyzing) { Text("保存") }
        },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )

    if (showDatePicker) {
        MealDatePickerDialog(
            initialDate = LocalDate.ofEpochDay(selectedEpochDay),
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                selectedEpochDay = minOf(date, LocalDate.now()).toEpochDay()
                showDatePicker = false
            },
        )
    }
    if (showTimePicker) {
        MealTimePickerDialog(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                if (!mealTypeManuallyChanged) mealType = defaultMealType(LocalTime.of(hour, minute))
                showTimePicker = false
            },
        )
    }
}

private val MEAL_TYPES = listOf("早餐", "上午", "午餐", "下午", "晚餐", "夜宵")

internal fun defaultMealType(time: LocalTime = LocalTime.now()): String = when (time.hour) {
    in 5..9 -> "早餐"
    in 10..11 -> "上午"
    in 12..14 -> "午餐"
    in 15..17 -> "下午"
    in 18..21 -> "晚餐"
    else -> "夜宵"
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MealScheduleEditor(
    date: LocalDate,
    hour: Int,
    minute: Int,
    mealType: String,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onSelectType: (String) -> Unit,
    onRenameType: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Mint),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("记录时间", fontWeight = FontWeight.Bold, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPickDate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Today, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(mealDateLabel(date), maxLines = 1)
                }
                FilledTonalButton(onClick = onPickTime, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(String.format(Locale.CHINA, "%02d:%02d", hour, minute))
                }
            }
            Text("餐次", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.72f))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MEAL_TYPES.forEach { type ->
                    FilterChip(
                        selected = mealType == type,
                        onClick = { onSelectType(type) },
                        label = { Text(type) },
                    )
                }
            }
            OutlinedTextField(
                value = mealType,
                onValueChange = { onRenameType(it.take(12)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("餐次名称，可自定义") },
                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                singleLine = true,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MealDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val todayUtcMillis = remember {
        LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val selectableDates = remember(todayUtcMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis
            override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state, title = { Text("选择记录日期", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MealTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择记录时间") },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
        confirmButton = { TextButton({ onConfirm(state.hour, state.minute) }) { Text("确定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

private fun mealDateLabel(date: LocalDate): String =
    when {
        date == LocalDate.now() -> "今天"
        date.year == LocalDate.now().year -> "${date.monthValue}月${date.dayOfMonth}日"
        else -> date.format(DateTimeFormatter.ofPattern("yy/M/d", Locale.CHINA))
    }
