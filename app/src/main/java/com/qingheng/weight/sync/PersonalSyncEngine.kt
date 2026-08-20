package com.qingheng.weight.sync

import androidx.room.withTransaction
import com.qingheng.weight.data.AppDatabase
import com.qingheng.weight.data.DeferredSyncEvent
import com.qingheng.weight.data.SyncMetadata
import com.qingheng.weight.data.SyncOutboxEvent
import com.qingheng.weight.data.SyncTombstone
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class PersonalSyncEngine(private val database: AppDatabase) {
    val pendingCount = database.syncDao().observePendingCount()
    private val mutex = Mutex()

    suspend fun sync(serviceUrl: String, token: String): PersonalSyncResult = mutex.withLock {
        val metadata = ensureMetadata()
        try {
            require(serviceUrl.isNotBlank()) { "个人数据同步地址不能为空" }
            require(token.isNotBlank()) { "请先填写个人数据同步 Token" }
            initializeOutbox(metadata)
            val client = PersonalSyncClient(serviceUrl, token)
            var uploaded = 0
            repeat(MAX_PUSH_BATCHES) {
                val pending = database.syncDao().pending(PUSH_BATCH_SIZE)
                if (pending.isEmpty()) return@repeat
                database.syncDao().markAttempted(pending.map(SyncOutboxEvent::eventId), System.currentTimeMillis())
                val pushed = client.push(
                    deviceId = ensureMetadata().deviceId,
                    events = pending.map { it.toCloudEvent() },
                )
                if (pushed.acknowledged.isEmpty()) {
                    throw PersonalSyncException.Protocol("同步服务没有确认已上传的数据")
                }
                database.syncDao().acknowledge(pushed.acknowledged)
                uploaded += pushed.acknowledged.size
                if (pending.size < PUSH_BATCH_SIZE) return@repeat
            }

            var downloaded = 0
            var pages = 0
            var hasMore: Boolean
            do {
                val current = ensureMetadata()
                val page = client.pull(current.pullCursor, PULL_PAGE_SIZE)
                applyPage(page)
                downloaded += page.events.size
                pages++
                if (pages >= MAX_PULL_PAGES && page.hasMore) {
                    throw PersonalSyncException.Protocol("服务器数据过多，本轮同步尚未完成，请再次同步")
                }
                hasMore = page.hasMore
            } while (hasMore)

            val completedAt = System.currentTimeMillis()
            val finalMetadata = ensureMetadata().copy(lastSyncedAt = completedAt, lastError = null)
            database.syncDao().putMetadata(finalMetadata)
            val pending = database.syncDao().pendingCount()
            PersonalSyncResult(uploaded, downloaded, pending, completedAt)
        } catch (error: Exception) {
            val message = error.message ?: "个人数据同步失败"
            database.syncDao().putMetadata(ensureMetadata().copy(lastError = message))
            throw error
        }
    }

    suspend fun metadata(): SyncMetadata = ensureMetadata()

    private suspend fun ensureMetadata(): SyncMetadata = database.withTransaction {
        database.syncDao().metadata() ?: SyncMetadata(deviceId = UUID.randomUUID().toString()).also {
            database.syncDao().putMetadata(it)
        }
    }

    private suspend fun initializeOutbox(metadata: SyncMetadata) {
        if (metadata.initialized) return
        database.withTransaction {
            val current = database.syncDao().metadata() ?: metadata
            if (current.initialized) return@withTransaction
            database.weightDao().allForSync().forEach { weight ->
                if (database.syncDao().tombstone(SyncPayloadCodec.WEIGHT, weight.id) == null) {
                    database.syncDao().enqueue(
                        SyncOutboxEvent(
                            eventId = UUID.randomUUID().toString(),
                            entityType = SyncPayloadCodec.WEIGHT,
                            entityId = weight.id,
                            dedupeKey = SyncIdentity.weight(weight),
                            operation = "upsert",
                            occurredAt = System.currentTimeMillis(),
                            payloadJson = SyncPayloadCodec.encode(weight),
                        ),
                    )
                }
            }
            database.mealDao().allForSync().forEach { meal ->
                if (database.syncDao().tombstone(SyncPayloadCodec.MEAL, meal.id) == null) {
                    val foods = database.mealDao().foodItemsForMeal(meal.id)
                    database.syncDao().enqueue(
                        SyncOutboxEvent(
                            eventId = UUID.randomUUID().toString(),
                            entityType = SyncPayloadCodec.MEAL,
                            entityId = meal.id,
                            operation = "upsert",
                            occurredAt = System.currentTimeMillis(),
                            payloadJson = SyncPayloadCodec.encode(meal, foods),
                        ),
                    )
                }
            }
            database.workoutDao().allForSync().forEach { workout ->
                if (database.syncDao().tombstone(SyncPayloadCodec.WORKOUT, workout.id) == null) {
                    database.syncDao().enqueue(
                        SyncOutboxEvent(
                            eventId = UUID.randomUUID().toString(),
                            entityType = SyncPayloadCodec.WORKOUT,
                            entityId = workout.id,
                            dedupeKey = SyncIdentity.workout(workout),
                            operation = "upsert",
                            occurredAt = System.currentTimeMillis(),
                            payloadJson = SyncPayloadCodec.encode(workout),
                        ),
                    )
                }
            }
            database.syncDao().putMetadata(current.copy(initialized = true))
        }
    }

    private suspend fun applyPage(page: PullResult) = database.withTransaction {
        page.events.forEach { event ->
            when {
                event.schemaVersion != SyncPayloadCodec.SCHEMA_VERSION -> defer(event)
                event.entityType == SyncPayloadCodec.WEIGHT -> applyWeight(event)
                event.entityType == SyncPayloadCodec.MEAL -> applyMeal(event)
                event.entityType == SyncPayloadCodec.WORKOUT -> applyWorkout(event)
                event.entityType == SyncPayloadCodec.LEGACY_WELLNESS -> Unit
                else -> defer(event)
            }
        }
        val metadata = database.syncDao().metadata() ?: SyncMetadata(deviceId = UUID.randomUUID().toString())
        database.syncDao().putMetadata(metadata.copy(pullCursor = page.nextCursor))
    }

    private suspend fun applyWeight(event: CloudSyncEvent) {
        if (event.operation == "delete") {
            deleteLocally(event, SyncPayloadCodec.WEIGHT) { database.weightDao().deleteById(event.entityId) }
            return
        }
        if (database.syncDao().tombstone(SyncPayloadCodec.WEIGHT, event.entityId) != null) return
        if (event.dedupeKey != null &&
            database.syncDao().tombstoneByDedupeKey(SyncPayloadCodec.WEIGHT, event.dedupeKey) != null
        ) return
        if (database.syncDao().pendingForEntity(SyncPayloadCodec.WEIGHT, event.entityId) != null) return
        database.weightDao().insert(SyncPayloadCodec.decodeWeight(event.entityId, event.payloadJson))
    }

    private suspend fun applyMeal(event: CloudSyncEvent) {
        if (event.operation == "delete") {
            deleteLocally(event, SyncPayloadCodec.MEAL) { database.mealDao().deleteById(event.entityId) }
            return
        }
        if (database.syncDao().tombstone(SyncPayloadCodec.MEAL, event.entityId) != null) return
        if (database.syncDao().pendingForEntity(SyncPayloadCodec.MEAL, event.entityId) != null) return
        val localImage = database.mealDao().findById(event.entityId)?.imageUri
        val (meal, foods) = SyncPayloadCodec.decodeMeal(event.entityId, event.payloadJson, localImage)
        database.mealDao().insert(meal, foods)
    }

    private suspend fun applyWorkout(event: CloudSyncEvent) {
        if (event.operation == "delete") {
            deleteLocally(event, SyncPayloadCodec.WORKOUT) {
                database.workoutDao().deleteById(event.entityId)
            }
            return
        }
        if (database.syncDao().tombstone(SyncPayloadCodec.WORKOUT, event.entityId) != null) return
        if (event.dedupeKey != null &&
            database.syncDao().tombstoneByDedupeKey(SyncPayloadCodec.WORKOUT, event.dedupeKey) != null
        ) return
        if (database.syncDao().pendingForEntity(SyncPayloadCodec.WORKOUT, event.entityId) != null) return
        database.workoutDao().insert(SyncPayloadCodec.decodeWorkout(event.entityId, event.payloadJson))
    }

    private suspend fun deleteLocally(
        event: CloudSyncEvent,
        entityType: String,
        delete: suspend () -> Unit,
    ) {
        delete()
        database.syncDao().removePending(entityType, event.entityId)
        if (event.dedupeKey != null) {
            database.syncDao().removePendingByDedupeKey(entityType, event.dedupeKey)
        }
        if (entityType == SyncPayloadCodec.WEIGHT && event.dedupeKey != null) {
            SyncIdentity.parseWeight(event.dedupeKey)?.let { identity ->
                database.weightDao().deleteByDedupeIdentity(identity.epochMinute, identity.centiKg)
            }
        }
        database.syncDao().putTombstone(
            SyncTombstone(entityType, event.entityId, event.dedupeKey, event.occurredAt),
        )
    }

    private suspend fun defer(event: CloudSyncEvent) {
        database.syncDao().defer(
            DeferredSyncEvent(
                cursor = requireNotNull(event.cursor),
                eventId = event.eventId,
                entityType = event.entityType,
                entityId = event.entityId,
                dedupeKey = event.dedupeKey,
                operation = event.operation,
                schemaVersion = event.schemaVersion,
                occurredAt = event.occurredAt,
                payloadJson = event.payloadJson,
            ),
        )
    }

    private fun SyncOutboxEvent.toCloudEvent() = CloudSyncEvent(
        eventId = eventId,
        entityType = entityType,
        entityId = entityId,
        dedupeKey = dedupeKey,
        operation = operation,
        schemaVersion = schemaVersion,
        occurredAt = occurredAt,
        payloadJson = payloadJson,
    )

    companion object {
        private const val PUSH_BATCH_SIZE = 100
        private const val PULL_PAGE_SIZE = 200
        private const val MAX_PUSH_BATCHES = 20
        private const val MAX_PULL_PAGES = 50
    }
}
