package com.qingheng.weight.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [WeightRecord::class, MealRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun mealDao(): MealDao
}

