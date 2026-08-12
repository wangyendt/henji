package com.qingheng.weight.data

enum class WeightUnit(
    val symbol: String,
    private val kilogramsFactor: Double,
) {
    KILOGRAM("kg", 1.0),
    JIN("斤", 2.0),
    ;

    fun fromKilograms(valueKg: Double): Double = valueKg * kilogramsFactor
    fun toKilograms(value: Double): Double = value / kilogramsFactor
    fun other(): WeightUnit = if (this == KILOGRAM) JIN else KILOGRAM
}
