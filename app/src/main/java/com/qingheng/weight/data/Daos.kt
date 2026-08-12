package com.qingheng.weight.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_records ORDER BY measuredAt DESC")
    fun observeAll(): Flow<List<WeightRecord>>

    @Query("SELECT * FROM weight_records ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): WeightRecord?

    @Query("SELECT * FROM weight_records WHERE source = 'bluetooth' AND measuredAt >= :after AND ABS(weightKg - :weightKg) < 0.05 ORDER BY measuredAt DESC")
    suspend fun recentBluetoothMeasurements(weightKg: Double, after: Long): List<WeightRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WeightRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(records: List<WeightRecord>)

    @Query("SELECT id FROM weight_records WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>

    @Delete suspend fun delete(record: WeightRecord)

    @Delete suspend fun delete(records: List<WeightRecord>)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MealRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MealRecord)

    @Delete suspend fun delete(record: MealRecord)
}
