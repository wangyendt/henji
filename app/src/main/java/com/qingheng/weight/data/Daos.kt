package com.qingheng.weight.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_records WHERE source != 'bluetooth' ORDER BY measuredAt DESC")
    fun observeAll(): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records WHERE source != 'bluetooth' ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): WeightRecord?

    @Query("SELECT * FROM weight_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): WeightRecord?

    @Query("SELECT * FROM weight_records WHERE source != 'manual' AND source != 'bluetooth' AND measuredAt BETWEEN :from AND :to AND ABS(weightKg - :weightKg) < 0.05 ORDER BY ABS(measuredAt - :measuredAt) LIMIT 1")
    suspend fun findNearest(weightKg: Double, measuredAt: Long, from: Long, to: Long): WeightRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WeightRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(records: List<WeightRecord>)

    @Query("SELECT id FROM weight_records WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>

    @Query("SELECT * FROM weight_records WHERE source != 'bluetooth' ORDER BY measuredAt")
    suspend fun allForSync(): List<WeightRecord>

    @Query("DELETE FROM weight_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM weight_records WHERE measuredAt / 60000 = :epochMinute AND CAST(ROUND(weightKg * 100.0) AS INTEGER) = :centiKg")
    suspend fun deleteByDedupeIdentity(epochMinute: Long, centiKg: Long)

    @Delete suspend fun delete(record: WeightRecord)

}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MealRecord>>

    @Query("SELECT * FROM meal_food_items ORDER BY mealId, id")
    fun observeAllFoodItems(): Flow<List<MealFoodItem>>

    @Query(
        """
        SELECT canonicalName,
               MIN(category) AS category,
               COUNT(DISTINCT mealId) AS mealCount,
               SUM((estimatedGramsLow + estimatedGramsHigh) / 2.0) AS estimatedGrams
        FROM meal_food_items
        GROUP BY canonicalName
        ORDER BY mealCount DESC, canonicalName COLLATE NOCASE ASC
        """,
    )
    fun observeFoodFrequencies(): Flow<List<FoodMealFrequency>>

    @Query("SELECT * FROM meal_records ORDER BY createdAt")
    suspend fun allForSync(): List<MealRecord>

    @Query("SELECT * FROM meal_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): MealRecord?

    @Query("SELECT * FROM meal_food_items WHERE mealId = :mealId ORDER BY id")
    suspend fun foodItemsForMeal(mealId: String): List<MealFoodItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MealRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItems(items: List<MealFoodItem>)

    @Query("DELETE FROM meal_food_items WHERE mealId = :mealId")
    suspend fun deleteFoodItems(mealId: String)

    @Transaction
    suspend fun insert(record: MealRecord, foodItems: List<MealFoodItem>) {
        insert(record)
        deleteFoodItems(record.id)
        if (foodItems.isNotEmpty()) insertFoodItems(foodItems)
    }

    @Delete suspend fun delete(record: MealRecord)

    @Delete suspend fun delete(records: List<MealRecord>)

    @Query("DELETE FROM meal_records WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_records ORDER BY startAt DESC")
    fun observeAll(): Flow<List<WorkoutRecord>>

    @Query("SELECT * FROM workout_records ORDER BY startAt")
    suspend fun allForSync(): List<WorkoutRecord>

    @Query("SELECT * FROM workout_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): WorkoutRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WorkoutRecord)

    @Query("DELETE FROM workout_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(record: WorkoutRecord)
}

@Dao
interface SyncDao {
    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun pendingCount(): Int

    @Query("SELECT * FROM sync_outbox ORDER BY createdAt, eventId LIMIT :limit")
    suspend fun pending(limit: Int): List<SyncOutboxEvent>

    @Query("SELECT * FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun pendingForEntity(entityType: String, entityId: String): SyncOutboxEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(event: SyncOutboxEvent)

    @Query("DELETE FROM sync_outbox WHERE eventId IN (:eventIds)")
    suspend fun acknowledge(eventIds: List<String>)

    @Query("DELETE FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun removePending(entityType: String, entityId: String)

    @Query("DELETE FROM sync_outbox WHERE entityType = :entityType AND dedupeKey = :dedupeKey")
    suspend fun removePendingByDedupeKey(entityType: String, dedupeKey: String)

    @Query("UPDATE sync_outbox SET attempts = attempts + 1, lastAttemptAt = :at WHERE eventId IN (:eventIds)")
    suspend fun markAttempted(eventIds: List<String>, at: Long)

    @Query("SELECT * FROM sync_tombstones WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun tombstone(entityType: String, entityId: String): SyncTombstone?

    @Query("SELECT * FROM sync_tombstones WHERE entityType = :entityType AND dedupeKey = :dedupeKey LIMIT 1")
    suspend fun tombstoneByDedupeKey(entityType: String, dedupeKey: String): SyncTombstone?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTombstone(tombstone: SyncTombstone)

    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    suspend fun metadata(): SyncMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMetadata(metadata: SyncMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun defer(event: DeferredSyncEvent)
}
