package com.qingheng.weight.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class ShareCardRenderer(private val context: Context) {
    fun render(content: ShareCardContent): Uri {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(BACKGROUND)
            drawHeader(this, content)
            when (content) {
                is TrendShareCard -> drawTrend(this, content)
                is MealShareCard -> drawMeal(this, content)
                is DailyMealsShareCard -> drawMeals(this, content)
                is DailyWorkoutsShareCard -> drawWorkouts(this, content)
            }
            drawFooter(this)
        }
        val directory = File(context.cacheDir, "share_cards").apply { mkdirs() }
        directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - CACHE_TTL_MILLIS }
            ?.forEach(File::delete)
        val output = File(directory, "henji-${content.fileStem}-${System.currentTimeMillis()}.png")
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "生成分享图片失败" }
        }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", output)
    }

    private fun drawHeader(canvas: Canvas, content: ShareCardContent) {
        canvas.roundedRect(64f, 58f, 1016f, 1292f, 42f, Color.WHITE)
        canvas.text("衡迹", 104f, 132f, 42f, EMERALD, bold = true)
        val tag = when (content) {
            is TrendShareCard -> "身体趋势"
            is MealShareCard -> "饮食记录"
            is DailyMealsShareCard -> "每日饮食"
            is DailyWorkoutsShareCard -> "运动记录"
        }
        canvas.roundedRect(818f, 88f, 968f, 142f, 27f, MINT)
        canvas.centeredText(tag, 893f, 125f, 25f, EMERALD, bold = true)
    }

    private fun drawMeal(canvas: Canvas, content: MealShareCard) {
        canvas.text(content.mealType, 104f, 236f, 56f, INK, bold = true)
        canvas.text(content.dateTitle, 104f, 284f, 28f, MUTED)

        val photoBounds = RectF(104f, 330f, 976f, 804f)
        val photoDrawn = content.imageUri?.let { canvas.drawMealPhoto(it, photoBounds) } == true
        if (!photoDrawn) {
            canvas.roundedRect(
                photoBounds.left,
                photoBounds.top,
                photoBounds.right,
                photoBounds.bottom,
                32f,
                MEAL_PANEL,
            )
            canvas.centeredText("今日好好吃饭", photoBounds.centerX(), photoBounds.centerY() + 10f, 38f, MEAL_INK, bold = true)
        }

        canvas.roundedRect(104f, 834f, 976f, 1138f, 30f, PANEL)
        canvas.text("本餐摄入", 140f, 892f, 25f, MUTED)
        canvas.rightText("${content.calorieRange} kcal", 940f, 898f, 42f, MEAL_INK, bold = true)
        canvas.ellipsizedText(content.foods, 140f, 970f, 800f, 35f, INK, bold = true)

        val nutrients = listOfNotNull(
            content.protein?.let { "蛋白质  $it" },
            content.carbohydrates?.let { "碳水  $it" },
            content.fat?.let { "脂肪  $it" },
        )
        if (nutrients.isNotEmpty()) {
            canvas.text(nutrients.joinToString("     "), 140f, 1050f, 27f, EMERALD, bold = true)
        } else {
            canvas.text("记录每一餐，看见每一次坚持", 140f, 1050f, 27f, MUTED)
        }
    }

    private fun Canvas.drawMealPhoto(uriString: String, destination: RectF): Boolean {
        val bitmap = runCatching {
            decodeSampledBitmap(uriString, destination.width().toInt(), destination.height().toInt())
        }.getOrNull() ?: return false
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height
        val destinationAspect = destination.width() / destination.height()
        val source = if (bitmapAspect > destinationAspect) {
            val sourceWidth = (bitmap.height * destinationAspect).toInt()
            val left = (bitmap.width - sourceWidth) / 2
            Rect(left, 0, left + sourceWidth, bitmap.height)
        } else {
            val sourceHeight = (bitmap.width / destinationAspect).toInt()
            val top = (bitmap.height - sourceHeight) / 2
            Rect(0, top, bitmap.width, top + sourceHeight)
        }
        save()
        clipPath(Path().apply { addRoundRect(destination, 32f, 32f, Path.Direction.CW) })
        drawBitmap(bitmap, source, destination, paint(color = Color.WHITE).apply { isFilterBitmap = true })
        restore()
        bitmap.recycle()
        return true
    }

    private fun decodeSampledBitmap(uriString: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > targetWidth * 2 && bounds.outHeight / sampleSize > targetHeight * 2) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun drawTrend(canvas: Canvas, content: TrendShareCard) {
        canvas.text("${content.metricTitle}趋势", 104f, 236f, 56f, INK, bold = true)
        canvas.text(content.rangeTitle, 104f, 284f, 28f, MUTED)
        canvas.text("最新", 104f, 358f, 27f, MUTED)
        canvas.text(content.latestValue, 104f, 426f, 60f, EMERALD, bold = true)
        content.changeValue?.let {
            canvas.text("区间变化", 624f, 358f, 27f, MUTED)
            canvas.text(it, 624f, 418f, 43f, if (it.startsWith("-")) EMERALD else AMBER, bold = true)
        }

        val chart = RectF(110f, 516f, 958f, 970f)
        canvas.roundedRect(chart.left, chart.top, chart.right, chart.bottom, 30f, PANEL)
        repeat(4) { index ->
            val y = chart.top + 55f + index * 105f
            canvas.drawLine(chart.left + 42f, y, chart.right - 42f, y, paint(2f, GRID))
        }
        val points = content.points
        if (points.isNotEmpty()) {
            val values = points.map(ShareTrendPoint::value)
            val minimum = values.minOrNull() ?: 0.0
            val maximum = values.maxOrNull() ?: minimum
            val span = max(maximum - minimum, 0.1)
            val firstDay = points.first().epochDay
            val daySpan = (points.last().epochDay - firstDay).coerceAtLeast(1L)
            val coordinates = points.map { point ->
                val x = if (points.size == 1) chart.centerX()
                else chart.left + 48f + (chart.width() - 96f) *
                    (point.epochDay - firstDay).toFloat() / daySpan.toFloat()
                val y = chart.bottom - 76f - ((point.value - minimum) / span * (chart.height() - 146f)).toFloat()
                x to y
            }
            if (coordinates.size > 1) {
                val path = Path().apply {
                    moveTo(coordinates.first().first, coordinates.first().second)
                    coordinates.drop(1).forEach { lineTo(it.first, it.second) }
                }
                canvas.drawPath(path, paint(10f, EMERALD, Paint.Style.STROKE).apply {
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                })
            }
            coordinates.forEach { (x, y) -> canvas.drawCircle(x, y, 10f, paint(color = EMERALD)) }
            canvas.text(points.first().label, chart.left + 42f, chart.bottom - 25f, 24f, MUTED)
            canvas.rightText(points.last().label, chart.right - 42f, chart.bottom - 25f, 24f, MUTED)
        } else {
            canvas.centeredText("暂无趋势数据", chart.centerX(), chart.centerY(), 32f, MUTED)
        }
        content.trendValue?.let {
            canvas.roundedRect(110f, 1015f, 958f, 1123f, 25f, MINT)
            canvas.text("稳健趋势", 148f, 1082f, 28f, EMERALD, bold = true)
            canvas.rightText(it, 920f, 1082f, 31f, EMERALD, bold = true)
        }
    }

    private fun drawMeals(canvas: Canvas, content: DailyMealsShareCard) {
        canvas.text("每日饮食", 104f, 236f, 56f, INK, bold = true)
        canvas.text(content.dateTitle, 104f, 284f, 28f, MUTED)
        canvas.roundedRect(104f, 330f, 976f, 492f, 32f, MEAL_PANEL)
        canvas.text("当日总摄入", 144f, 390f, 27f, MEAL_INK)
        canvas.text("${content.calorieRange} kcal", 144f, 458f, 49f, MEAL_INK, bold = true)
        canvas.rightText("${content.entries.size} 餐", 930f, 438f, 32f, MEAL_INK, bold = true)

        var y = 550f
        content.entries.take(MAX_VISIBLE_ENTRIES).forEach { entry ->
            canvas.roundedRect(104f, y, 976f, y + 132f, 26f, PANEL)
            canvas.text(entry.mealTypeAndTime, 140f, y + 43f, 26f, EMERALD, bold = true)
            canvas.rightText("${entry.calories} kcal", 940f, y + 43f, 25f, MEAL_INK, bold = true)
            canvas.ellipsizedText(entry.foods, 140f, y + 94f, 790f, 29f, INK)
            y += 148f
        }
        if (content.entries.size > MAX_VISIBLE_ENTRIES) {
            canvas.text("另有 ${content.entries.size - MAX_VISIBLE_ENTRIES} 餐", 118f, 1172f, 25f, MUTED)
        }
    }

    private fun drawWorkouts(canvas: Canvas, content: DailyWorkoutsShareCard) {
        canvas.text("运动记录", 104f, 236f, 56f, INK, bold = true)
        canvas.text(content.dateTitle, 104f, 284f, 28f, MUTED)
        canvas.roundedRect(104f, 330f, 976f, 470f, 32f, SPORT_PANEL)
        canvas.text("${content.entries.size} 次运动", 144f, 408f, 40f, SPORT_INK, bold = true)
        content.totalCalories?.let { canvas.rightText("$it kcal", 930f, 408f, 35f, SPORT_INK, bold = true) }

        var y = 528f
        content.entries.take(MAX_VISIBLE_ENTRIES).forEach { entry ->
            canvas.roundedRect(104f, y, 976f, y + 146f, 26f, PANEL)
            canvas.text(entry.title, 140f, y + 45f, 30f, SPORT_INK, bold = true)
            canvas.rightText(entry.time, 940f, y + 45f, 25f, MUTED)
            canvas.ellipsizedText(entry.summary, 140f, y + 91f, 790f, 27f, INK)
            entry.details?.let { canvas.ellipsizedText(it, 140f, y + 126f, 790f, 23f, SPORT_INK) }
            y += 162f
        }
        if (content.entries.size > MAX_VISIBLE_ENTRIES) {
            canvas.text("另有 ${content.entries.size - MAX_VISIBLE_ENTRIES} 次运动", 118f, 1195f, 25f, MUTED)
        }
    }

    private fun drawFooter(canvas: Canvas) {
        canvas.text("记录变化，也记录坚持", 104f, 1216f, 28f, MUTED)
        canvas.rightText("由 衡迹 生成", 976f, 1216f, 25f, MUTED)
    }

    private fun Canvas.text(value: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean = false) {
        drawText(value, x, baseline, textPaint(size, color, bold))
    }

    private fun Canvas.centeredText(value: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean = false) {
        val paint = textPaint(size, color, bold).apply { textAlign = Paint.Align.CENTER }
        drawText(value, x, baseline, paint)
    }

    private fun Canvas.rightText(value: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean = false) {
        val paint = textPaint(size, color, bold).apply { textAlign = Paint.Align.RIGHT }
        drawText(value, x, baseline, paint)
    }

    private fun Canvas.ellipsizedText(
        value: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ) {
        val paint = textPaint(size, color, bold)
        if (paint.measureText(value) <= maxWidth) {
            drawText(value, x, baseline, paint)
            return
        }
        var end = value.length
        while (end > 0 && paint.measureText(value.substring(0, end) + "…") > maxWidth) end--
        drawText(value.substring(0, end) + "…", x, baseline, paint)
    }

    private fun Canvas.roundedRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        drawRoundRect(left, top, right, bottom, radius, radius, paint(color = color))
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = paint(color = color).apply {
        textSize = size
        typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }

    private fun paint(
        strokeWidth: Float = 1f,
        color: Int = Color.BLACK,
        style: Paint.Style = Paint.Style.FILL,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.strokeWidth = strokeWidth
        this.style = style
    }

    companion object {
        private const val WIDTH = 1080
        private const val HEIGHT = 1350
        private const val MAX_VISIBLE_ENTRIES = 4
        private const val CACHE_TTL_MILLIS = 3L * 24 * 60 * 60 * 1_000
        private val BACKGROUND = Color.rgb(232, 243, 238)
        private val EMERALD = Color.rgb(0, 119, 85)
        private val MINT = Color.rgb(223, 243, 234)
        private val INK = Color.rgb(28, 40, 35)
        private val MUTED = Color.rgb(105, 119, 112)
        private val PANEL = Color.rgb(247, 249, 248)
        private val GRID = Color.rgb(216, 224, 220)
        private val AMBER = Color.rgb(213, 139, 0)
        private val MEAL_PANEL = Color.rgb(255, 244, 218)
        private val MEAL_INK = Color.rgb(139, 87, 0)
        private val SPORT_PANEL = Color.rgb(232, 243, 252)
        private val SPORT_INK = Color.rgb(42, 99, 145)
    }
}
