package com.qingheng.weight.meal

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CodexModelCatalogTest {
    @Test
    fun `catalog preserves unknown future models and effort ordering`() {
        val result = CodexModelCatalog.parse(JSONObject("""{
          "source":"codex-app-server","stale":false,"updatedAt":"2026-10-05T00:00:00Z",
          "models":[{"id":"future","displayName":"Future","reasoningLevels":["high","future-level"],"defaultReasoning":"future-level","inputModalities":["text","image"]}]
        }"""))
        assertEquals("future", result.models.single().id)
        assertEquals(listOf("high", "future-level"), result.models.single().reasoningLevels)
        assertEquals("future-level", result.models.single().defaultReasoning)
        assertFalse(result.stale)
    }

    @Test
    fun `stale empty catalog does not invent models or effort levels`() {
        val result = CodexModelCatalog.parse(JSONObject("""{"source":"codex-model-cache","updatedAt":null,"stale":true,"warning":"offline","models":[]}"""))
        assertTrue(result.models.isEmpty())
        assertTrue(result.stale)
        assertEquals("", result.updatedAt)
    }

    @Test
    fun `fetch uses authenticated GET with reverse proxy prefix and refresh flag`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            assertEquals("GET", chain.request().method)
            assertEquals("/services/codex-task/v1/models", chain.request().url.encodedPath)
            assertEquals("true", chain.request().url.queryParameter("refresh"))
            assertEquals("Bearer test-token", chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"source":"codex-app-server","stale":false,"models":[]}""".toResponseBody("application/json".toMediaType())).build()
        }).build()
        val result = CodexTaskClient("https://example.com/services/codex-task", "test-token", http).fetchModels(true)
        assertTrue(result.models.isEmpty())
    }
}
