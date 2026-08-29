package com.qingheng.weight.share

data class ShareTrendPoint(
    val label: String,
    val epochDay: Long,
    val value: Double,
)

sealed interface ShareCardContent {
    val shareText: String
    val fileStem: String
}

data class TrendShareCard(
    val metricTitle: String,
    val rangeTitle: String,
    val points: List<ShareTrendPoint>,
    val latestValue: String,
    val changeValue: String?,
    val trendValue: String?,
) : ShareCardContent {
    override val shareText: String = "我的${metricTitle}趋势 · 衡迹"
    override val fileStem: String = "trend"
}

data class MealShareEntry(
    val mealTypeAndTime: String,
    val foods: String,
    val calories: String,
)

data class DailyMealsShareCard(
    val dateTitle: String,
    val calorieRange: String,
    val entries: List<MealShareEntry>,
) : ShareCardContent {
    override val shareText: String = "$dateTitle 每日饮食 · 衡迹"
    override val fileStem: String = "meals"
}

data class WorkoutShareEntry(
    val title: String,
    val time: String,
    val summary: String,
    val details: String?,
)

data class DailyWorkoutsShareCard(
    val dateTitle: String,
    val totalCalories: String?,
    val entries: List<WorkoutShareEntry>,
) : ShareCardContent {
    override val shareText: String = "$dateTitle 运动记录 · 衡迹"
    override val fileStem: String = "workouts"
}
