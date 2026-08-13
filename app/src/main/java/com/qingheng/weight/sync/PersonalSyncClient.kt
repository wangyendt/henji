package com.qingheng.weight.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class PersonalSyncClient(
    serviceBaseUrl: String,
    private val token: String,
    private val httpClient: OkHttpClient = defaultPersonalSyncHttpClient(),
) {
    private val baseUrl: HttpUrl = serviceBaseUrl.trimEnd('/').toHttpUrlOrNull()
        ?.let { parsed ->
            parsed.newBuilder()
                .encodedPath(parsed.encodedPath.trimEnd('/') + "/")
                .query(null)
                .fragment(null)
                .build()
        }
        ?: throw PersonalSyncException.Configuration("个人数据同步地址格式无效")
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    init {
        if (token.isBlank()) throw PersonalSyncException.Configuration("个人数据同步 Token 不能为空")
    }

    suspend fun push(deviceId: String, events: List<CloudSyncEvent>): PushResult {
        require(events.isNotEmpty())
        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("events", JSONArray().apply {
                events.forEach { event ->
                    put(JSONObject().apply {
                        put("eventId", event.eventId)
                        put("entityType", event.entityType)
                        put("entityId", event.entityId)
                        event.dedupeKey?.let { put("dedupeKey", it) }
                        put("operation", event.operation)
                        put("schemaVersion", event.schemaVersion)
                        put("occurredAt", event.occurredAt)
                        put("payload", JSONObject(event.payloadJson))
                    })
                }
            })
        }
        val json = executeJson(
            Request.Builder()
                .url(resolve("v1/sync/push"))
                .post(body.toString().toRequestBody(jsonMediaType))
                .build(),
        )
        val acknowledged = json.optJSONArray("acknowledged")?.let { array ->
            buildList { for (index in 0 until array.length()) add(array.getString(index)) }
        }.orEmpty()
        return PushResult(acknowledged, json.getLong("serverCursor"))
    }

    suspend fun pull(after: Long, limit: Int = 200): PullResult {
        val url = resolve("v1/sync/pull").newBuilder()
            .addQueryParameter("after", after.toString())
            .addQueryParameter("limit", limit.toString())
            .build()
        val json = executeJson(Request.Builder().url(url).get().build())
        val events = buildList {
            val array = json.getJSONArray("events")
            for (index in 0 until array.length()) {
                val event = array.getJSONObject(index)
                add(
                    CloudSyncEvent(
                        cursor = event.getLong("cursor"),
                        eventId = event.getString("eventId"),
                        deviceId = event.getString("deviceId"),
                        entityType = event.getString("entityType"),
                        entityId = event.getString("entityId"),
                        dedupeKey = if (!event.has("dedupeKey") || event.isNull("dedupeKey")) {
                            null
                        } else event.getString("dedupeKey").takeIf(String::isNotBlank),
                        operation = event.getString("operation"),
                        schemaVersion = event.getInt("schemaVersion"),
                        occurredAt = event.getLong("occurredAt"),
                        payloadJson = event.getJSONObject("payload").toString(),
                    ),
                )
            }
        }
        return PullResult(
            events = events,
            nextCursor = json.getLong("nextCursor"),
            serverCursor = json.getLong("serverCursor"),
            hasMore = json.getBoolean("hasMore"),
        )
    }

    private fun resolve(relativePath: String): HttpUrl = baseUrl.resolve(relativePath)
        ?: throw PersonalSyncException.Configuration("个人数据同步接口地址无效")

    private suspend fun executeJson(original: Request): JSONObject = withContext(Dispatchers.IO) {
        val request = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                        .orEmpty().ifBlank { "同步服务请求失败" }
                    val message = if (response.code == 401) "同步服务 Token 无效" else "同步服务返回 HTTP ${response.code}：$detail"
                    throw PersonalSyncException.Http(message, response.code)
                }
                try {
                    JSONObject(text)
                } catch (error: JSONException) {
                    throw PersonalSyncException.Protocol("同步服务返回了无效 JSON", error)
                }
            }
        } catch (error: PersonalSyncException) {
            throw error
        } catch (error: IOException) {
            throw PersonalSyncException.Network("连接个人数据同步服务失败：${error.message ?: "网络异常"}", error)
        }
    }
}

internal fun defaultPersonalSyncHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
