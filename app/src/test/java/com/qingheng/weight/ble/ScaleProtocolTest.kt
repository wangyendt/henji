package com.qingheng.weight.ble

import com.qingheng.weight.data.UserProfile
import org.junit.Assert.*
import org.junit.Test

class ScaleProtocolTest {
    private val profile = UserProfile(heightCm = 175, birthYear = 1990)

    @Test fun parsesXiaomiStableWeight() {
        val value = ScaleProtocol.parseXiaomiWeight(byteArrayOf(0x20, 0xB0.toByte(), 0x36), profile)
        assertNotNull(value)
        assertEquals(70.0, value!!.weightKg, 0.001)
        assertTrue(value.isStable)
    }

    @Test fun parsesStandardWeight() {
        val value = ScaleProtocol.parseStandardWeight(byteArrayOf(0x00, 0xB0.toByte(), 0x36), profile)
        assertEquals(70.0, value!!.weightKg, 0.001)
        assertTrue(value.isStable)
    }

    @Test fun parsesQnWeightAndImpedance() {
        val packet = byteArrayOf(0x10, 0x0B, 0x01, 0x1B, 0x58, 0x01, 0x01, 0xF4.toByte(), 0x02, 0x08)
        val value = ScaleProtocol.parseQn(packet, 100.0, profile)
        assertEquals(70.0, value!!.weightKg, 0.001)
        assertEquals(510.0, value.impedanceOhm!!, 0.001)
        assertTrue(value.isStable)
        assertNotNull(value.bodyFatPercent)
    }

    @Test fun reassemblesIcomonWeightFrame() {
        val payload = byteArrayOf(0xA2.toByte(), 0x03, 0x19, 0x00, 0xFD.toByte(), 0x84.toByte(), 0x00)
        val frame = ByteArray(20)
        frame[0] = 1; frame[1] = payload.size.toByte(); frame[2] = 0
        payload.copyInto(frame, destinationOffset = 3)
        frame[19] = (frame.sliceArray(3..18).sumOf { it.u() } and 0x1F).toByte()
        val value = IcomonFrameAssembler().accept(frame, profile)
        assertEquals(64.9, value!!.weightKg, 0.001)
        assertTrue(value.isStable)
    }

    @Test fun parsesCompactIcomonFrameCapturedFromFitdaysScale() {
        val frame = hexBytes("BF000700A203000244DC0007")
        val value = IcomonFrameAssembler().accept(frame, profile)
        assertNotNull(value)
        assertEquals(148.7, value!!.weightKg, 0.001)
        assertTrue(value.isStable)
    }

    @Test fun combinesScaleNewWeightAndImpedanceFrames() {
        val assembler = IcomonFrameAssembler()
        val weight = assembler.accept(hexBytes("BF000700A203000244DC0007"), profile)
        val body = assembler.accept(hexBytes("C0000500A60200000008"), profile)

        assertEquals(148.7, weight!!.weightKg, 0.001)
        assertNotNull(body)
        assertEquals(148.7, body!!.weightKg, 0.001)
        assertEquals(512.0, body.impedanceOhm!!, 0.001)
        assertTrue(body.isStable)
        assertFalse(body.isEstimated)
    }

    @Test fun buildsIcomonAckFrameUsedToStartTheSession() {
        val frame = IcomonCommandSession().ack().single()

        assertEquals("000300B000000000000000000000000000000010", frame.hex())
    }

    @Test fun buildsScaleNewSetupFramesCapturedFromOfficialFitdaysEncoder() {
        val frames = IcomonCommandSession().scaleNewSetup(unixTimeSeconds = 0)

        assertEquals(listOf("AC20FE060000CCD0", "AC2000000000C0C0"), frames.map(ByteArray::hex))
        frames.forEach { frame ->
            assertEquals(frame.sliceArray(2..6).sumOf { it.u() } and 0xFF, frame.last().u())
        }
    }

    @Test fun buildsIcomonProfileHeartbeatFromTheCurrentUser() {
        val encoder = IcomonCommandSession()
        val frame = encoder.sync(
            profile = profile,
            weightKg = 70.5,
            unixTimeSeconds = 0x12345678,
            stabilized = true,
        ).single()

        assertEquals(20, frame.size)
        assertEquals(0xBA, frame[3].u())
        assertEquals("12345678", frame.sliceArray(4..7).hex())
        assertEquals("0078", frame.sliceArray(8..9).hex())
        assertEquals(175, frame[14].u())
        assertEquals(0x9B8A, frame.u16be(15))
        assertEquals(0xA4, frame[17].u()) // 36 years old, male
        assertEquals(0x2F, frame[18].u())
        assertEquals(frame.sliceArray(3..18).sumOf { it.u() } and 0x1F, frame[19].u())
    }

    @Test fun detectsIcomonControlFramesThatRequireAnAck() {
        assertTrue(IcomonFrameAssembler.requiresAck(hexBytes("000300A000000000000000000000000000000000")))
        assertTrue(IcomonFrameAssembler.requiresAck(hexBytes("000600A3190000FD84000000000000000000001D")))
        assertFalse(IcomonFrameAssembler.requiresAck(hexBytes("BF000700A203000244DC0007")))
    }

    @Test fun rejectsImplausibleValues() {
        assertNull(ScaleProtocol.parseStandardWeight(byteArrayOf(0x00, 0x01, 0x00), profile))
    }

    private fun hexBytes(value: String) = ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
