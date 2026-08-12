package com.qingheng.weight.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun MealsScreen(vm: AppViewModel) {
    val meals by vm.meals.collectAsStateWithLifecycle()
    var selectedImage by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImage = uri?.toString(); if (uri != null) showAdd = true
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("饮食记录", "用照片留下真实的一餐") }
        item {
            Button({ picker.launch("image/*") }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(54.dp)) {
                Icon(Icons.Outlined.AddAPhoto, null); Spacer(Modifier.width(8.dp)); Text("选择照片并识别")
            }
            Spacer(Modifier.height(18.dp))
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
    if (showAdd) AddMealDialog(vm, selectedImage) { showAdd = false; selectedImage = null }
}

@Composable private fun AddMealDialog(vm: AppViewModel, imageUri: String?, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("记录这一餐") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            imageUri?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop) }
            Text("CodexTask 智能识别将在下一步接入；现在也可以先手动保存。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it }, label = { Text("菜品") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(calories, { calories = it.filter(Char::isDigit) }, label = { Text("估计热量 kcal") }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button({
            val kcal = calories.toIntOrNull() ?: 0
            scope.launch {
                vm.saveMeal(com.qingheng.weight.data.MealRecord(
                    id = java.util.UUID.randomUUID().toString(), createdAt = System.currentTimeMillis(), mealType = "餐食",
                    imageUri = imageUri, foodNames = name.ifBlank { "未命名餐食" }, calorieLow = kcal, calorieHigh = kcal,
                    advice = "已手动记录", rawAnalysis = null,
                )); dismiss()
            }
        }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}
