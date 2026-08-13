package com.qingheng.weight.health

import com.qingheng.weight.data.DailyBriefingRecord
import com.qingheng.weight.data.DailyWellnessRecord
import com.qingheng.weight.data.MealRecord
import java.time.LocalDate
import java.time.ZoneId

fun buildDailyBriefing(
    date: LocalDate,
    meals: List<MealRecord>,
    wellness: DailyWellnessRecord?,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): DailyBriefingRecord {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val dayMeals = meals.filter { it.createdAt in start until end }
    val calorieLow = dayMeals.takeIf { it.isNotEmpty() }?.sumOf { it.calorieLow }
    val calorieHigh = dayMeals.takeIf { it.isNotEmpty() }?.sumOf { it.calorieHigh }
    val foodNames = dayMeals.flatMap { it.foodNames.split('、', ',', '，') }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .take(8)
        .joinToString("、")

    return DailyBriefingRecord(
        dateEpochDay = date.toEpochDay(),
        generatedAt = nowMillis,
        mealCount = dayMeals.size,
        calorieLow = calorieLow,
        calorieHigh = calorieHigh,
        foodNames = foodNames,
        sleepMinutes = wellness?.sleepMinutes,
        steps = wellness?.steps,
        exerciseMinutes = wellness?.exerciseMinutes,
        activeCaloriesKcal = wellness?.activeCaloriesKcal,
        advice = dailyAdvice(dayMeals.size, calorieHigh, wellness),
    )
}

internal fun dailyAdvice(
    mealCount: Int,
    calorieHigh: Int?,
    wellness: DailyWellnessRecord?,
): String {
    val advice = mutableListOf<String>()
    val sleep = wellness?.sleepMinutes
    when {
        sleep == null -> advice += "昨晚睡眠还没有同步。可先打开 vivo 健康刷新；若仍为空，表示当前版本没有向 Health Connect 共享。"
        sleep < 360 -> advice += "昨晚睡眠偏少，今天把高强度运动降一级，午后小憩控制在 20 分钟内。"
        sleep < 420 -> advice += "睡眠略少，今晚尽量提前半小时上床。"
        sleep >= 540 -> advice += "睡眠时间较长；若仍觉得疲倦，留意连续几天的状态。"
        else -> advice += "睡眠时长处于比较合适的区间，今天可以按计划活动。"
    }
    val steps = wellness?.steps
    val exerciseMinutes = wellness?.exerciseMinutes
    when {
        steps == null && exerciseMinutes == null -> advice += "运动数据还没有同步；今天先安排一段 20–30 分钟的步行。"
        (steps ?: 0L) < 4_000 && (exerciseMinutes ?: 0) < 20 -> advice += "昨天活动偏少，今天分两次多走 15 分钟会更容易完成。"
        (steps ?: 0L) >= 8_000 || (exerciseMinutes ?: 0) >= 30 -> advice += "昨天活动量不错，今天注意补水并给身体留一点恢复时间。"
        else -> advice += "昨天完成了基础活动，今天再增加一次短步行就很好。"
    }
    when {
        mealCount == 0 -> advice += "昨天没有饮食记录，营养判断会留空；今天随手记一餐即可开始积累。"
        mealCount < 2 -> advice += "昨天记录的餐次较少，建议补齐主要餐次，后续建议会更准确。"
        calorieHigh != null && calorieHigh < 1_000 -> advice += "已记录热量偏低，可能有漏记；先补全餐次，不要据此刻意少吃。"
        else -> advice += "饮食已形成记录，今天继续优先保证蛋白质和蔬菜。"
    }
    return advice.joinToString("\n")
}
