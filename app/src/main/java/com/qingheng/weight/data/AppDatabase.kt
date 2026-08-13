package com.qingheng.weight.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WeightRecord::class,
        MealRecord::class,
        MealFoodItem::class,
    ],
    version = 4,
    exportSchema = false,
)
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_wellness` (
                        `dateEpochDay` INTEGER NOT NULL,
                        `sleepMinutes` INTEGER,
                        `deepSleepMinutes` INTEGER,
                        `remSleepMinutes` INTEGER,
                        `steps` INTEGER,
                        `exerciseMinutes` INTEGER,
                        `activeCaloriesKcal` INTEGER,
                        `exerciseTypes` TEXT NOT NULL,
                        `sleepSource` TEXT,
                        `activitySource` TEXT,
                        `syncedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`dateEpochDay`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_briefings` (
                        `dateEpochDay` INTEGER NOT NULL,
                        `generatedAt` INTEGER NOT NULL,
                        `mealCount` INTEGER NOT NULL,
                        `calorieLow` INTEGER,
                        `calorieHigh` INTEGER,
                        `foodNames` TEXT NOT NULL,
                        `sleepMinutes` INTEGER,
                        `steps` INTEGER,
                        `exerciseMinutes` INTEGER,
                        `activeCaloriesKcal` INTEGER,
                        `advice` TEXT NOT NULL,
                        PRIMARY KEY(`dateEpochDay`)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `daily_wellness`")
                db.execSQL("DROP TABLE IF EXISTS `daily_briefings`")
            }
        }
    }
}
