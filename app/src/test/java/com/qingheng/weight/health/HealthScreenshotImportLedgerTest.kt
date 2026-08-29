package com.qingheng.weight.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class HealthScreenshotImportLedgerTest {
    @Test
    fun `fingerprint is stable for identical screenshot bytes`() {
        val screenshot = "same screenshot".toByteArray()

        assertEquals(
            sha256Hex(ByteArrayInputStream(screenshot)),
            sha256Hex(ByteArrayInputStream(screenshot)),
        )
    }

    @Test
    fun `fingerprint changes when screenshot bytes change`() {
        assertNotEquals(
            sha256Hex(ByteArrayInputStream("screenshot one".toByteArray())),
            sha256Hex(ByteArrayInputStream("screenshot two".toByteArray())),
        )
    }
}
