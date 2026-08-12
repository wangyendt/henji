package com.qingheng.weight.meal

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class CodexTaskClientTest {
    @Test
    fun `posts documented image payload then surfaces failed polled status in Chinese`() = runBlocking {
        val callNumber = AtomicInteger()
        val testCredential = UUID.randomUUID().toString()
        val requests = mutableListOf<okhttp3.Request>()
        val bodies = mutableListOf<String>()
        val interceptor = Interceptor { chain ->
            requests += chain.request()
            bodies += Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
            val responseJson = if (callNumber.getAndIncrement() == 0) {
                """{"jobId":"00000000-0000-0000-0000-000000000001","status":"queued","statusUrl":"/v1/jobs/00000000-0000-0000-0000-000000000001"}"""
            } else {
                """{"jobId":"00000000-0000-0000-0000-000000000001","kind":"text","status":"failed","result":{"status":"failed","taskId":"task-1","backend":"direct","artifacts":[],"error":{"code":"UPSTREAM_ERROR","message":"模型服务忙","retryable":true}}}"""
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (callNumber.get() == 1) 202 else 200)
                .message("test")
                .body(responseJson.toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = CodexTaskClient(
            serviceBaseUrl = "http://127.0.0.1:7777",
            serviceToken = testCredential,
            httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            polling = CodexTaskPollingOptions(intervalMillis = 0, timeoutMillis = 2_000),
        )

        val error = try {
            client.analyzeImage(byteArrayOf(1, 2, 3), "image/jpeg")
            throw AssertionError("应抛出远端失败异常")
        } catch (error: CodexTaskException.Remote) {
            error
        }

        assertEquals("UPSTREAM_ERROR", error.code)
        assertTrue(error.message.orEmpty().contains("食物识别失败"))
        assertEquals("POST", requests[0].method)
        assertEquals("/v1/text", requests[0].url.encodedPath)
        assertEquals("Bearer $testCredential", requests[0].header("Authorization"))
        assertEquals("GET", requests[1].method)
        val submission = JSONObject(bodies[0])
        assertEquals("direct", submission.getString("backend"))
        assertTrue(submission.has("schema"))
        val image = submission.getJSONArray("images").getJSONObject(0)
        assertEquals("image/jpeg", image.getString("mimeType"))
        assertEquals("AQID", image.getString("dataBase64"))
    }
}
