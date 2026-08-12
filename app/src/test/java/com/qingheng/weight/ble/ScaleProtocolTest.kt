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

    @Test fun rejectsImplausibleValues() {
        assertNull(ScaleProtocol.parseStandardWeight(byteArrayOf(0x00, 0x01, 0x00), profile))
    }
}

