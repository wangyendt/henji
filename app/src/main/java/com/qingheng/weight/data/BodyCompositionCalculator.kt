package com.qingheng.weight.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Transparent local estimates for metrics not present in a BLE packet.
 * Weight and impedance remain the raw device values; derived metrics are marked estimated.
 */
object BodyCompositionCalculator {
    fun estimate(weightKg: Double, impedanceOhm: Double?, profile: UserProfile): BodyMetrics {
        val heightM = profile.heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)
        val sexTerm = if (profile.sex == Sex.MALE) 1.0 else 0.0

        // Deurenberg adult equation provides a defensible fallback. If BIA is available,
        // a small bounded impedance correction preserves direction without pretending to
        // reproduce Fitdays' proprietary coefficients.
        val baseFat = 1.20 * bmi + 0.23 * profile.age - 10.8 * sexTerm - 5.4
        val impedanceAdjustment = impedanceOhm?.let { ((it - 500.0) / 100.0).coerceIn(-3.0, 3.0) } ?: 0.0
        val fat = (baseFat + impedanceAdjustment).coerceIn(3.0, 60.0)
        val fatFree = weightKg * (1 - fat / 100.0)
        val water = ((fatFree * 0.73) / weightKg * 100).coerceIn(30.0, 75.0)
        val muscleMass = max(0.0, fatFree - estimatedBone(weightKg, profile.sex))
        val skeletalPercent = (muscleMass * 0.54 / weightKg * 100).coerceIn(15.0, 60.0)
        val bmr = if (profile.sex == Sex.MALE) {
            10 * weightKg + 6.25 * profile.heightCm - 5 * profile.age + 5
        } else {
            10 * weightKg + 6.25 * profile.heightCm - 5 * profile.age - 161
        }
        val visceral = ((fat - if (profile.sex == Sex.MALE) 10 else 18) / 2.5 + 5).coerceIn(1.0, 30.0)
        val bodyAge = (profile.age + (bmi - 22) * 0.8 + (fat - if (profile.sex == Sex.MALE) 18 else 28) * 0.25)
            .toInt().coerceIn(18, 90)

        return BodyMetrics(
            weightKg = one(weightKg), impedanceOhm = impedanceOhm?.let(::one), bmi = one(bmi),
            bodyFatPercent = one(fat), bodyWaterPercent = one(water),
            skeletalMusclePercent = one(skeletalPercent), bmrKcal = bmr.toInt(),
            fatFreeMassKg = one(fatFree), subcutaneousFatPercent = one(fat * 0.82),
            visceralFat = one(visceral), muscleMassKg = one(muscleMass),
            boneMassKg = one(estimatedBone(weightKg, profile.sex)),
            proteinPercent = one((100 - fat - water - estimatedBone(weightKg, profile.sex) / weightKg * 100).coerceIn(8.0, 25.0)),
            bodyAge = bodyAge, isEstimated = true,
        )
    }

    private fun estimatedBone(weight: Double, sex: Sex): Double =
        min(if (sex == Sex.MALE) 4.5 else 3.8, max(1.5, weight * if (sex == Sex.MALE) 0.042 else 0.038))

    private fun one(value: Double) = round(value * 10) / 10
}

