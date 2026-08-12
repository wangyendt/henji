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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WeightRecord)

    @Delete suspend fun delete(record: WeightRecord)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MealRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MealRecord)

    @Delete suspend fun delete(record: MealRecord)
}

