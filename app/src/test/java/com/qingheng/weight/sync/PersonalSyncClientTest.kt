package com.qingheng.weight.sync

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalSyncClientTest {
    @Test
    fun `preserves reverse proxy prefix and authenticates push and pull`() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        val interceptor = Interceptor { chain ->
            requests += chain.request()
            val json = if (chain.request().method == "POST") {
                """{"acknowledged":["event-1"],"serverCursor":3}"""
            } else {
                """{"events":[{"cursor":3,"eventId":"33333333-3333-3333-3333-333333333333","deviceId":"11111111-1111-1111-1111-111111111111","entityType":"weight_record","entityId":"weight-1","dedupeKey":"1000:8000","operation":"delete","schemaVersion":1,"occurredAt":1000,"payload":{}}],"nextCursor":3,"serverCursor":3,"hasMore":false}"""
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("test")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = PersonalSyncClient(
            "https://example.com/services/henji-sync",
            "test-token",
            OkHttpClient.Builder().addInterceptor(interceptor).build(),
        )
        client.push(
            "11111111-1111-1111-1111-111111111111",
            listOf(
                CloudSyncEvent(
                    eventId = "22222222-2222-2222-2222-222222222222",
                    entityType = "weight_record",
                    entityId = "weight-1",
                    operation = "upsert",
                    schemaVersion = 1,
                    occurredAt = 1,
                    payloadJson = "{\"weightKg\":80}",
                ),
            ),
        )
        val pulled = client.pull(3)

        assertEquals("/services/henji-sync/v1/sync/push", requests[0].url.encodedPath)
        assertEquals("/services/henji-sync/v1/sync/pull", requests[1].url.encodedPath)
        assertEquals("Bearer test-token", requests[0].header("Authorization"))
        assertEquals("3", requests[1].url.queryParameter("after"))
        assertEquals("1000:8000", pulled.events.single().dedupeKey)
    }
}
