package com.qingheng.weight.ble

import com.qingheng.weight.data.BodyCompositionCalculator
import com.qingheng.weight.data.BodyMetrics
import com.qingheng.weight.data.UserProfile
import java.util.Locale
import kotlin.math.round

object ScaleProtocol {
    fun parseXiaomiWeight(data: ByteArray, profile: UserProfile): BodyMetrics? {
        if (data.size < 3) return null
        val flags = data[0].u()
        if (flags and 0x80 != 0) return null
        val raw = data.u16le(1)
        val kg = if (flags and 0x04 != 0) raw / 100.0 * 0.45359237 else raw / 200.0
        return valid(kg) { BodyCompositionCalculator.estimate(kg, null, profile).copy(isStable = flags and 0x20 != 0) }
    }

    fun parseXiaomiBody(data: ByteArray, profile: UserProfile): BodyMetrics? {
        if (data.size < 13) return null
        val status = data[1].u()
        if (status and 0x80 != 0) return null
        val impedance = data.u16le(9).takeIf { it in 80..3000 }?.toDouble()
        val kg = data.u16le(11) / 200.0
        return valid(kg) {
            BodyCompositionCalculator.estimate(kg, impedance, profile)
                .copy(isStable = status and 0x20 != 0, rawPacketHex = data.hex())
        }
    }

    fun parseStandardWeight(data: ByteArray, profile: UserProfile): BodyMetrics? {
        if (data.size < 3) return null
        val imperial = data[0].u() and 1 != 0
        val kg = if (imperial) data.u16le(1) * 0.01 * 0.45359237 else data.u16le(1) * 0.005
        return valid(kg) { BodyCompositionCalculator.estimate(kg, null, profile).copy(isStable = true, rawPacketHex = data.hex()) }
    }

    /** Bluetooth SIG Body Composition Measurement 0x2A9C. */
    fun parseStandardBody(data: ByteArray, profile: UserProfile): BodyMetrics? {
        if (data.size < 4) return null
        val flags = data.u16le(0)
        val imperial = flags and 1 != 0
        var i = 2
        val fatPercent = data.u16le(i) / 10.0; i += 2
        if (flags and (1 shl 1) != 0) i += 7
        if (flags and (1 shl 2) != 0) i += 1
        val bmr = if (flags and (1 shl 3) != 0 && i + 1 < data.size) data.u16le(i).also { i += 2 } else null
        val musclePct = if (flags and (1 shl 4) != 0 && i + 1 < data.size) data.u16le(i).div(10.0).also { i += 2 } else null
        val muscleMass = if (flags and (1 shl 5) != 0 && i + 1 < data.size) {
            (if (imperial) data.u16le(i) * 0.01 * 0.45359237 else data.u16le(i) * 0.005).also { i += 2 }
        } else null
        val fatFree = if (flags and (1 shl 6) != 0 && i + 1 < data.size) {
            (if (imperial) data.u16le(i) * 0.01 * 0.45359237 else data.u16le(i) * 0.005).also { i += 2 }
        } else null
        if (flags and (1 shl 7) != 0) i += 2
        val waterMass = if (flags and (1 shl 8) != 0 && i + 1 < data.size) {
            (if (imperial) data.u16le(i) * 0.01 * 0.45359237 else data.u16le(i) * 0.005).also { i += 2 }
        } else null
        val impedance = if (flags and (1 shl 9) != 0 && i + 1 < data.size) data.u16le(i).div(10.0).also { i += 2 } else null
        if (flags and (1 shl 10) == 0 || i + 1 >= data.size) return null
        val kg = if (imperial) data.u16le(i) * 0.01 * 0.45359237 else data.u16le(i) * 0.005
        return valid(kg) {
            val estimated = BodyCompositionCalculator.estimate(kg, impedance, profile)
            estimated.copy(
                bodyFatPercent = fatPercent, skeletalMusclePercent = musclePct ?: estimated.skeletalMusclePercent,
                muscleMassKg = muscleMass ?: estimated.muscleMassKg, fatFreeMassKg = fatFree ?: estimated.fatFreeMassKg,
                bodyWaterPercent = waterMass?.div(kg)?.times(100) ?: estimated.bodyWaterPercent,
                bmrKcal = bmr ?: estimated.bmrKcal, isStable = true, isEstimated = false, rawPacketHex = data.hex(),
            )
        }
    }

    /** QN/Fitdays family on FFE1 or FFF1. */
    fun parseQn(data: ByteArray, scaleFactor: Double, profile: UserProfile): BodyMetrics? {
        if (data.size < 10 || data[0].u() != 0x10) return null
        val es30 = data.size >= 11 && data[4].u() <= 2 && scaleFactor == 10.0
        val stable: Boolean
        val rawWeight: Int
        val r1: Int
        val r2: Int
        if (es30) {
            stable = data[4].u() in 1..2; rawWeight = data.u16be(5)
            r1 = data.u16be(7); r2 = data.u16be(9)
        } else {
            stable = data[5].u() == 1; rawWeight = data.u16be(3)
            r1 = data.u16be(6); r2 = data.u16be(8)
        }
        val kg = rawWeight / scaleFactor
        val impedance = listOf(r1, r2).filter { it in 80..3000 }.average().takeIf { !it.isNaN() }
        return valid(kg) {
            BodyCompositionCalculator.estimate(kg, impedance, profile)
                .copy(isStable = stable, rawPacketHex = data.hex())
        }
    }

    fun parseQnBroadcast(data: ByteArray, profile: UserProfile): BodyMetrics? {
        if (data.size < 19 || data[0].u() != 0xAA || data[1].u() != 0xBB) return null
        val kg = data.u16le(17) / 100.0
        return valid(kg) {
            BodyCompositionCalculator.estimate(kg, null, profile)
                .copy(isStable = data[15].u() and 0x20 != 0, rawPacketHex = data.hex())
        }
    }

    private inline fun valid(kg: Double, block: () -> BodyMetrics): BodyMetrics? =
        if (kg in 5.0..350.0 && kg.isFinite()) block() else null
}

class IcomonFrameAssembler {
    private var sequence = -1
    private var expected = 0
    private val payload = mutableListOf<Byte>()

    fun accept(frame: ByteArray, profile: UserProfile): BodyMetrics? {
        if (frame.size != 20 || (frame.sliceArray(3..18).sumOf { it.u() } and 0x1F) != frame[19].u()) return null
        val seq = frame[0].u(); val total = frame[1].u(); val fragment = frame[2].u()
        if (fragment == 0 || seq != sequence) { sequence = seq; expected = total; payload.clear() }
        if (fragment * 16 != payload.size) return null
        payload += frame.sliceArray(3..18).toList()
        if (payload.size < expected) return null
        val msg = payload.take(expected).toByteArray()
        payload.clear()
        return parseMessage(msg, profile)?.copy(rawPacketHex = frame.hex())
    }

    private fun parseMessage(msg: ByteArray, profile: UserProfile): BodyMetrics? {
        if (msg.isEmpty()) return null
        return when (msg[0].u()) {
            0xA2 -> if (msg.size >= 6) {
                val kg = msg.u16be(4) / 1000.0
                BodyCompositionCalculator.estimate(kg, null, profile).copy(isStable = msg.getOrNull(1)?.u() == 3)
            } else null
            0xA3 -> if (msg.size >= 7) {
                val kg = msg.u16be(3) / 1000.0
                val impedances = buildList {
                    var i = 6
                    while (i + 1 < msg.size) { add(msg.u16be(i) / 10.0); i += 2 }
                }.filter { it in 80.0..3000.0 }
                BodyCompositionCalculator.estimate(kg, impedances.firstOrNull(), profile).copy(isStable = true)
            } else null
            else -> null
        }
    }
}

internal fun Byte.u() = toInt() and 0xFF
internal fun ByteArray.u16le(i: Int) = this[i].u() or (this[i + 1].u() shl 8)
internal fun ByteArray.u16be(i: Int) = (this[i].u() shl 8) or this[i + 1].u()
internal fun ByteArray.hex() = joinToString("") { String.format(Locale.US, "%02X", it.u()) }

