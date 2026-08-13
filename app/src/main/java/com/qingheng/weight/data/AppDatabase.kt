package com.qingheng.weight.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WeightRecord::class, MealRecord::class, MealFoodItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun mealDao(): MealDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `meal_food_items` (
                        `id` TEXT NOT NULL,
                        `mealId` TEXT NOT NULL,
                        `canonicalName` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `estimatedGramsLow` REAL NOT NULL,
                        `estimatedGramsHigh` REAL NOT NULL,
                        `calorieLow` REAL NOT NULL,
                        `calorieHigh` REAL NOT NULL,
                        `confidence` REAL NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`mealId`) REFERENCES `meal_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_food_items_mealId` ON `meal_food_items` (`mealId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_food_items_canonicalName` ON `meal_food_items` (`canonicalName`)")
            }
        }
    }
}
