package com.qingheng.weight.sync

data class CloudSyncEvent(
    val cursor: Long? = null,
    val eventId: String,
    val deviceId: String? = null,
    val entityType: String,
    val entityId: String,
    val dedupeKey: String? = null,
    val operation: String,
    val schemaVersion: Int,
    val occurredAt: Long,
    val payloadJson: String,
)

data class PushResult(
    val acknowledged: List<String>,
    val serverCursor: Long,
)

data class PullResult(
    val events: List<CloudSyncEvent>,
    val nextCursor: Long,
    val serverCursor: Long,
    val hasMore: Boolean,
)

data class PersonalSyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val pending: Int,
    val completedAt: Long,
)

sealed class PersonalSyncException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Configuration(message: String) : PersonalSyncException(message)
    class Http(message: String, val statusCode: Int) : PersonalSyncException(message)
    class Protocol(message: String, cause: Throwable? = null) : PersonalSyncException(message, cause)
    class Network(message: String, cause: Throwable? = null) : PersonalSyncException(message, cause)
}
