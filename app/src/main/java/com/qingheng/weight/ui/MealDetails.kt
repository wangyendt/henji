package com.qingheng.weight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.qingheng.weight.data.MealFoodItem
import com.qingheng.weight.data.MealRecord
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailSheet(
    meal: MealRecord,
    foods: List<MealFoodItem>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var showLargeImage by remember(meal.id) { mutableStateOf(false) }
    var showZoomHint by remember(meal.id) { mutableStateOf(false) }
    var confirmDelete by remember(meal.id) { mutableStateOf(false) }
    LaunchedEffect(showLargeImage) {
        if (showLargeImage) {
            showZoomHint = true
            delay(3_000)
            showZoomHint = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        meal.mealType,
                        style = MaterialTheme.typography.labelLarge,
                        color = Emerald,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        meal.foodNames,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        meal.createdAt.asDate("yyyy年M月d日 HH:mm"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            meal.imageUri?.let { imageUri ->
                item {
                    Box(
                        Modifier.fillMaxWidth().height(270.dp).padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(24.dp)).clickable { showLargeImage = true },
                    ) {
                        AsyncImage(
                            imageUri,
                            meal.foodNames,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Surface(
                            Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            color = Color.Black.copy(alpha = 0.66f),
                            contentColor = Color.White,
                            shape = CircleShape,
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Photo, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("查看大图", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            item { MealNutritionSummary(meal) }
            item {
                Text(
                    "食物明细",
                    Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (foods.isEmpty()) {
                item {
                    Text(
                        meal.foodNames,
                        Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(foods, key = MealFoodItem::id) { food ->
                    FoodLedgerRow(food)
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5DE)),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("这一餐的建议", fontWeight = FontWeight.Bold, color = Color(0xFF72510D))
                        Spacer(Modifier.height(5.dp))
                        Text(meal.advice, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF594719))
                    }
                }
            }
            if (onDelete != null) {
                item {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, null)
                        Spacer(Modifier.width(7.dp))
                        Text("删除这条记录")
                    }
                }
            }
        }
    }

    if (showLargeImage && meal.imageUri != null) {
        Dialog(
            onDismissRequest = { showLargeImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                ZoomableMealImage(meal.imageUri, meal.foodNames)
                if (showZoomHint) {
                    Surface(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        contentColor = Color.White,
                        shape = CircleShape,
                    ) {
                        Text(
                            "双击放大或还原 · 双指缩放 · 拖动查看",
                            Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                IconButton(
                    onClick = { showLargeImage = false },
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(Icons.Outlined.Close, "关闭大图", tint = Color.White)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条饮食记录？") },
            text = { Text("照片引用、食物明细和统计次数会一起移除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete?.invoke()
                    onDismiss()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ZoomableMealImage(imageUri: String, contentDescription: String) {
    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var offset by remember(imageUri) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember(imageUri) { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val previousScale = scale
        val nextScale = (previousScale * zoomChange).coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
        if (nextScale <= MIN_IMAGE_SCALE + 0.01f) {
            scale = MIN_IMAGE_SCALE
            offset = Offset.Zero
        } else {
            val scaleRatio = nextScale / previousScale
            scale = nextScale
            offset = constrainImageOffset(
                candidate = Offset(
                    x = offset.x * scaleRatio + panChange.x,
                    y = offset.y * scaleRatio + panChange.y,
                ),
                scale = nextScale,
                viewportSize = viewportSize,
            )
        }
    }

    Box(
        Modifier.fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                viewportSize = it
                offset = constrainImageOffset(offset, scale, it)
            }
            .pointerInput(imageUri, viewportSize) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > MIN_IMAGE_SCALE + 0.01f) {
                            scale = MIN_IMAGE_SCALE
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_IMAGE_SCALE
                            val centerX = viewportSize.width / 2f
                            val centerY = viewportSize.height / 2f
                            offset = constrainImageOffset(
                                candidate = Offset(
                                    x = (centerX - tap.x) * (DOUBLE_TAP_IMAGE_SCALE - 1f),
                                    y = (centerY - tap.y) * (DOUBLE_TAP_IMAGE_SCALE - 1f),
                                ),
                                scale = DOUBLE_TAP_IMAGE_SCALE,
                                viewportSize = viewportSize,
                            )
                        }
                    },
                )
            }
            .transformable(transformState),
    ) {
        AsyncImage(
            imageUri,
            contentDescription,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            contentScale = ContentScale.Fit,
        )
    }
}

internal fun constrainImageOffset(candidate: Offset, scale: Float, viewportSize: IntSize): Offset {
    if (scale <= MIN_IMAGE_SCALE || viewportSize == IntSize.Zero) return Offset.Zero
    val maximumX = viewportSize.width * (scale - 1f) / 2f
    val maximumY = viewportSize.height * (scale - 1f) / 2f
    return Offset(
        x = candidate.x.coerceIn(-maximumX, maximumX),
        y = candidate.y.coerceIn(-maximumY, maximumY),
    )
}

private const val MIN_IMAGE_SCALE = 1f
private const val DOUBLE_TAP_IMAGE_SCALE = 2.5f
private const val MAX_IMAGE_SCALE = 5f

@Composable
private fun MealNutritionSummary(meal: MealRecord) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("估算热量", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    calorieRange(meal.calorieLow, meal.calorieHigh),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(5.dp))
                Text("kcal", Modifier.padding(bottom = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroPill("蛋白质", meal.proteinGrams, Color(0xFF3189C9), Modifier.weight(1f))
                MacroPill("碳水", meal.carbsGrams, Color(0xFF7C65C1), Modifier.weight(1f))
                MacroPill("脂肪", meal.fatGrams, Color(0xFFE07A45), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MacroPill(label: String, grams: Double?, accent: Color, modifier: Modifier = Modifier) {
    Surface(modifier, color = accent.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                grams?.let { "${formatMealNumber(it)} g" } ?: "--",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun FoodLedgerRow(food: MealFoodItem) {
    val accent = mealCategoryColor(food.category)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp)).background(accent.copy(alpha = 0.11f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).height(42.dp).background(accent, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(food.canonicalName, fontWeight = FontWeight.Bold)
            Text(
                food.displayName.takeIf { it != food.canonicalName } ?: food.category,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${formatMealRange(food.estimatedGramsLow, food.estimatedGramsHigh)} g",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${formatMealRange(food.calorieLow, food.calorieHigh)} kcal",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

internal fun mealCategoryColor(category: String): Color = when (category) {
    "主食" -> Color(0xFFE5A11A)
    "肉蛋水产" -> Color(0xFFE06C5D)
    "蔬菜" -> Color(0xFF169B6B)
    "水果" -> Color(0xFFF08A3C)
    "奶豆" -> Color(0xFF3D83C5)
    "坚果" -> Color(0xFFA36C38)
    "饮品" -> Color(0xFF278DA0)
    else -> Color(0xFF7868B2)
}

internal fun calorieRange(low: Int, high: Int): String =
    if (low == high) low.toString() else "$low–$high"

private fun formatMealRange(low: Double, high: Double): String =
    if (kotlin.math.abs(low - high) < 0.05) formatMealNumber(low)
    else "${formatMealNumber(low)}–${formatMealNumber(high)}"

private fun formatMealNumber(value: Double): String =
    if (kotlin.math.abs(value - value.roundToInt()) < 0.05) value.roundToInt().toString()
    else String.format("%.1f", value)
