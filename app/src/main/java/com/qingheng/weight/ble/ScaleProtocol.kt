package com.qingheng.weight.ble

import com.qingheng.weight.data.BodyCompositionCalculator
import com.qingheng.weight.data.BodyMetrics
import com.qingheng.weight.data.ActivityLevel
import com.qingheng.weight.data.Sex
import com.qingheng.weight.data.UserProfile
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt

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
        var kg = rawWeight / scaleFactor
        if (kg !in 5.0..350.0) kg /= 10.0
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
    private var expected = 0
    private val payload = mutableListOf<Byte>()
    private var latestWeightKg: Double? = null

    fun accept(frame: ByteArray, profile: UserProfile, fallbackWeightKg: Double? = null): BodyMetrics? {
        if (latestWeightKg == null) latestWeightKg = fallbackWeightKg
        val decoded = decodeTransportFrame(frame) ?: return null
        val (fragment, total, chunk) = decoded

        if (fragment == 0) {
            payload.clear()
            expected = total
        } else if (expected == 0) {
            return null
        }
        payload += chunk.toList()
        if (payload.size < expected) return null

        val msg = payload.take(expected).toByteArray()
        payload.clear(); expected = 0
        return parseMessage(msg, profile)?.copy(rawPacketHex = frame.hex())
    }

    /**
     * ICOMON firmware in the field uses two envelope variants:
     *
     * 1. 20 bytes: `[seq][total][fragment][16-byte chunk][checksum]`
     * 2. Compact: `[seq][fragment][total][00][chunk][checksum]`
     *
     * The connected Fitdays scale uses the compact 12-byte A2 form. Both checksums are the
     * low five bits of the payload sum.
     */
    private fun decodeTransportFrame(frame: ByteArray): TransportFrame? {
        if (frame.size == 20) {
            val chunk = frame.sliceArray(3..18)
            if ((chunk.sumOf { it.u() } and 0x1F) != frame[19].u()) return null
            return TransportFrame(frame[2].u(), frame[1].u(), chunk)
        }

        if (frame.size >= 6 && frame[3].u() == 0) {
            val total = frame[2].u()
            val available = (frame.size - 5).coerceAtLeast(0)
            val chunkSize = minOf(total, available)
            if (chunkSize <= 0) return null
            val chunk = frame.sliceArray(4 until 4 + chunkSize)
            if ((chunk.sumOf { it.u() } and 0x1F) != frame.last().u()) return null
            return TransportFrame(frame[1].u(), total, chunk)
        }
        return null
    }

    private fun parseMessage(msg: ByteArray, profile: UserProfile): BodyMetrics? {
        if (msg.isEmpty()) return null
        return when (msg[0].u()) {
            0xA2 -> if (msg.size >= 7) {
                val kg = msg.u24be(3) / 1000.0
                if (kg !in 5.0..350.0) null else {
                    latestWeightKg = kg
                    BodyCompositionCalculator.estimate(kg, null, profile)
                        .copy(isStable = msg[1].u() in 2..3)
                }
            } else null
            // ScaleNew sends the impedance/ADC result separately after the stable A2 weight.
            // Payload: A6 + ADC1 (big-endian) + optional ADC2..5 + unit + BFA type.
            0xA6 -> if (msg.size >= 5) {
                val kg = latestWeightKg
                val rawAdc = msg.u16be(1).toDouble()
                val impedance = when {
                    rawAdc <= 0.0 -> null
                    rawAdc >= 1500.0 && kg != null ->
                        (((rawAdc - 1000.0) - kg * 10.0 * 0.4) / 0.6 / 10.0)
                            .takeIf { it in 80.0..3000.0 }
                    else -> rawAdc.takeIf { it in 80.0..3000.0 }
                }
                if (kg == null || impedance == null) null else {
                    BodyCompositionCalculator.estimate(kg, impedance, profile)
                        .copy(isStable = true, isEstimated = false)
                }
            } else null
            0xA3 -> if (msg.size >= 6) {
                val kg = msg.u24be(2) / 1000.0
                val impedances = buildList {
                    var i = 6
                    while (i + 1 < msg.size) { add(msg.u16be(i) / 10.0); i += 2 }
                }.filter { it in 80.0..3000.0 }
                if (kg !in 5.0..350.0) null else BodyCompositionCalculator.estimate(kg, impedances.firstOrNull(), profile)
                    .copy(isStable = true)
            } else null
            else -> null
        }
    }

    private data class TransportFrame(val fragment: Int, val total: Int, val chunk: ByteArray)

    companion object {
        /** A0 counters and A3 results must be acknowledged with a B0 frame. */
        fun requiresAck(frame: ByteArray): Boolean {
            val type = when {
                frame.size == 20 && frame[2].u() == 0 -> frame[3].u()
                frame.size >= 6 && frame[1].u() == 0 && frame[3].u() == 0 -> frame[4].u()
                else -> return false
            }
            return type == 0xA0 || type == 0xA3
        }
    }
}

/** Stateful encoder for app -> scale commands written to Fitdays characteristic FFB1. */
class IcomonCommandSession {
    private var sequence = 0
    private var replyIndex = 0

    fun reset() {
        sequence = 0
        replyIndex = 0
    }

    fun ack(): List<ByteArray> {
        val frames = emit(byteArrayOf(0xB0.toByte(), replyIndex.toByte(), 0))
        replyIndex = (replyIndex + 1) and 0xFF
        return frames
    }

    /** ICOMON ScaleNew (protocol 105) unit/time initialization written to FFB1. */
    fun scaleNewSetup(unixTimeSeconds: Int = (System.currentTimeMillis() / 1000L).toInt()): List<ByteArray> {
        val unit = byteArrayOf(
            0xAC.toByte(), 0x20, 0xFE.toByte(), 0x06, 0x00, 0x00, 0xCC.toByte(), 0,
        ).withScaleNewChecksum()
        val time = byteArrayOf(
            0xAC.toByte(), 0x20,
            (unixTimeSeconds ushr 24).toByte(), (unixTimeSeconds ushr 16).toByte(),
            (unixTimeSeconds ushr 8).toByte(), unixTimeSeconds.toByte(),
            0xC0.toByte(), 0,
        ).withScaleNewChecksum()
        return listOf(unit, time)
    }

    fun sync(
        profile: UserProfile,
        weightKg: Double,
        userId: Long = 0,
        unixTimeSeconds: Int = (System.currentTimeMillis() / 1000L).toInt(),
        stabilized: Boolean = true,
    ): List<ByteArray> {
        val record = profileRecord(profile, weightKg, userId, stabilized)
        return emit(buildList<Byte> {
            add(0xBA.toByte())
            addAll(unixTimeSeconds.be32().toList())
            add(0); add(0x78)
            addAll(record.toList())
            add((if (profile.activityLevel == ActivityLevel.HIGH) 0x0F else 0x2F).toByte())
        }.toByteArray())
    }

    fun userList(
        profile: UserProfile,
        weightKg: Double,
        userId: Long = 0,
        stabilized: Boolean = true,
    ): List<ByteArray> = emit(
        byteArrayOf(0xBB.toByte(), 1) + profileRecord(profile, weightKg, userId, stabilized),
    )

    fun other(subCommand: Int = 0x09): List<ByteArray> =
        emit(byteArrayOf(0xBD.toByte(), subCommand.toByte()))

    private fun profileRecord(
        profile: UserProfile,
        weightKg: Double,
        userId: Long,
        stabilized: Boolean,
    ): ByteArray {
        var rawWeight = (weightKg.coerceIn(0.0, 327.67) * 100.0).roundToInt() and 0x7FFF
        if (stabilized && rawWeight > 0) rawWeight = rawWeight or 0x8000
        val ageAndSex = (profile.age and 0x7F) or if (profile.sex == Sex.MALE) 0x80 else 0
        return byteArrayOf(
            (userId ushr 24).toByte(), (userId ushr 16).toByte(),
            (userId ushr 8).toByte(), userId.toByte(),
            profile.heightCm.coerceIn(80, 255).toByte(),
            (rawWeight ushr 8).toByte(), rawWeight.toByte(), ageAndSex.toByte(),
        )
    }

    private fun emit(payload: ByteArray): List<ByteArray> {
        val frames = buildFrames(sequence, payload)
        sequence = (sequence + 1) and 0xFF
        return frames
    }

    private fun buildFrames(sequence: Int, payload: ByteArray): List<ByteArray> = buildList {
        var offset = 0
        var fragment = 0
        do {
            val frame = ByteArray(20)
            frame[0] = sequence.toByte()
            frame[1] = payload.size.toByte()
            frame[2] = fragment.toByte()
            val length = minOf(16, payload.size - offset)
            payload.copyInto(frame, destinationOffset = 3, startIndex = offset, endIndex = offset + length)
            frame[19] = (frame.sliceArray(3..18).sumOf { it.u() } and 0x1F).toByte()
            add(frame)
            offset += length
            fragment += 1
        } while (offset < payload.size)
    }
}

private fun ByteArray.withScaleNewChecksum(): ByteArray = apply {
    this[lastIndex] = sliceArray(2 until lastIndex).sumOf { it.u() }.toByte()
}

internal fun Byte.u() = toInt() and 0xFF
internal fun ByteArray.u16le(i: Int) = this[i].u() or (this[i + 1].u() shl 8)
internal fun ByteArray.u16be(i: Int) = (this[i].u() shl 8) or this[i + 1].u()
internal fun ByteArray.u24be(i: Int) = (this[i].u() shl 16) or (this[i + 1].u() shl 8) or this[i + 2].u()
internal fun ByteArray.hex() = joinToString("") { String.format(Locale.US, "%02X", it.u()) }
private fun Int.be32() = byteArrayOf(
    (this ushr 24).toByte(), (this ushr 16).toByte(), (this ushr 8).toByte(), toByte(),
)
