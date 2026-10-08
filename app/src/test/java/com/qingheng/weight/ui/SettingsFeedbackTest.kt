package com.qingheng.weight.ui

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsFeedbackTest {
    @Test fun `success waits for persistence and repeated saves each notify`() = runTest {
        val messages = mutableListOf<String>()
        val persisted = CompletableDeferred<Unit>()
        val job = launch {
            assertTrue(saveSettingsWithFeedback("身体资料已保存", { messages.add(it) }) { persisted.await() })
        }
        runCurrent()
        assertTrue(messages.isEmpty())
        persisted.complete(Unit)
        job.join()
        assertTrue(saveSettingsWithFeedback("身体资料已保存", { messages.add(it) }) {})
        assertEquals(listOf("身体资料已保存", "身体资料已保存"), messages)
    }

    @Test fun `failed persistence reports failure without leaking exception details`() = runTest {
        val messages = mutableListOf<String>()
        assertFalse(saveSettingsWithFeedback("服务配置已保存", { messages.add(it) }) {
            throw IOException("private token or file path")
        })
        assertEquals(listOf("保存失败，请重试"), messages)
    }

    @Test fun `cancellation is propagated without success or failure toast`() = runTest {
        val messages = mutableListOf<String>()
        try {
            saveSettingsWithFeedback("同步配置已保存", { messages.add(it) }) {
                throw CancellationException("screen closed")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(messages.isEmpty())
        }
    }
}
