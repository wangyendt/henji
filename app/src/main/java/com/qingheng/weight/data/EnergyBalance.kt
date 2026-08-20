package com.qingheng.weight.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

private const val DAILY_LIVING_FACTOR = 1.2
private const val KCAL_PER_KG = 7_700.0

data class DayEnergyEstimate(
    val date: LocalDate,
    val bmrKcal: Int?,
    val livingKcal: Int?,
    val intakeLowKcal: Int,
    val intakeHighKcal: Int,
    val workoutKcal: Int,
    val mealCount: Int,
    val workoutCount: Int,
    val balanceLowKcal: Int?,
    val balanceHighKcal: Int?,
    val weightChangeLowKg: Double?,
    val weightChangeHighKg: Double?,
    val hasWeightMeasurement: Boolean,
)

data class WeekEnergyEstimate(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val elapsedDays: Int,
    val recordedMealDays: Int,
    val intakeLowKcal: Int,
    val intakeHighKcal: Int,
    val livingKcal: Int?,
    val workoutKcal: Int,
    val balanceLowKcal: Int?,
    val balanceHighKcal: Int?,
    val weightChangeLowKg: Double?,
    val weightChangeHighKg: Double?,
    val measuredWeightChangeKg: Double?,
)

data class EnergyBalanceSummary(
    val today: DayEnergyEstimate,
    val week: WeekEnergyEstimate,
)

fun energyBalanceSummary(
    weights: List<WeightRecord>,
    meals: List<MealRecord>,
    workouts: List<WorkoutRecord>,
    profile: UserProfile,
    now: ZonedDateTime = ZonedDateTime.now(),
): EnergyBalanceSummary {
    val zoneId = now.zone
    val today = now.toLocalDate()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weightDays = groupWeightRecordsByDay(weights, zoneId).associateBy(DailyWeightHistory::date)
    val bmrPoints = dailyMetricPoints(weights, TrendMetric.BMR, zoneId)
    val mealsByDate = meals.groupBy { it.createdAt.localDate(zoneId) }
    val workoutsByDate = workouts.groupBy { it.startAt.localDate(zoneId) }

    fun bmrFor(date: LocalDate): Int? {
        val measured = bmrPoints.lastOrNull { !it.date.isAfter(date) }?.value?.toInt()
        if (measured != null) return measured
        val weight = weightDays.values.filter { !it.date.isAfter(date) }
            .maxByOrNull(DailyWeightHistory::date)?.latest?.weightKg
            ?: weightDays.values.maxByOrNull(DailyWeightHistory::date)?.latest?.weightKg
            ?: return null
        return BodyCompositionCalculator.estimate(weight, null, profile).bmrKcal
    }

    fun estimate(date: LocalDate): DayEnergyEstimate {
        val dayMeals = mealsByDate[date].orEmpty()
        val dayWorkouts = workoutsByDate[date].orEmpty()
        val bmr = bmrFor(date)
        val living = bmr?.let { (it * DAILY_LIVING_FACTOR).toInt() }
        val intakeLow = dayMeals.sumOf(MealRecord::calorieLow)
        val intakeHigh = dayMeals.sumOf(MealRecord::calorieHigh)
        val workout = dayWorkouts.sumOf { it.caloriesKcal?.toInt() ?: 0 }
        val balanceLow = living?.let { intakeLow - it - workout }
        val balanceHigh = living?.let { intakeHigh - it - workout }
        return DayEnergyEstimate(
            date = date,
            bmrKcal = bmr,
            livingKcal = living,
            intakeLowKcal = intakeLow,
            intakeHighKcal = intakeHigh,
            workoutKcal = workout,
            mealCount = dayMeals.size,
            workoutCount = dayWorkouts.size,
            balanceLowKcal = balanceLow,
            balanceHighKcal = balanceHigh,
            weightChangeLowKg = balanceLow?.div(KCAL_PER_KG),
            weightChangeHighKg = balanceHigh?.div(KCAL_PER_KG),
            hasWeightMeasurement = weightDays.containsKey(date),
        )
    }

    val todayEstimate = estimate(today)
    val elapsedDates = generateSequence(weekStart) { date ->
        date.plusDays(1).takeUnless { it.isAfter(today) }
    }.toList()
    val elapsedDayEstimates = elapsedDates.map(::estimate)
    val recordedDays = elapsedDayEstimates.filter { it.mealCount > 0 }
    val weekBalanceLow = recordedDays.mapNotNull(DayEnergyEstimate::balanceLowKcal)
        .takeIf { it.size == recordedDays.size && it.isNotEmpty() }?.sum()
    val weekBalanceHigh = recordedDays.mapNotNull(DayEnergyEstimate::balanceHighKcal)
        .takeIf { it.size == recordedDays.size && it.isNotEmpty() }?.sum()
    val weekWeights = weightDays.values.filter { it.date in weekStart..today }.sortedBy(DailyWeightHistory::date)

    return EnergyBalanceSummary(
        today = todayEstimate,
        week = WeekEnergyEstimate(
            startDate = weekStart,
            endDate = today,
            elapsedDays = elapsedDates.size,
            recordedMealDays = recordedDays.size,
            intakeLowKcal = recordedDays.sumOf(DayEnergyEstimate::intakeLowKcal),
            intakeHighKcal = recordedDays.sumOf(DayEnergyEstimate::intakeHighKcal),
            livingKcal = recordedDays.mapNotNull(DayEnergyEstimate::livingKcal)
                .takeIf { it.size == recordedDays.size && it.isNotEmpty() }?.sum(),
            workoutKcal = elapsedDayEstimates.sumOf(DayEnergyEstimate::workoutKcal),
            balanceLowKcal = weekBalanceLow,
            balanceHighKcal = weekBalanceHigh,
            weightChangeLowKg = weekBalanceLow?.div(KCAL_PER_KG),
            weightChangeHighKg = weekBalanceHigh?.div(KCAL_PER_KG),
            measuredWeightChangeKg = if (weekWeights.size >= 2) {
                weekWeights.last().latest.weightKg - weekWeights.first().latest.weightKg
            } else null,
        ),
    )
}

private fun Long.localDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
