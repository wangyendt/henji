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
        SyncOutboxEvent::class,
        SyncTombstone::class,
        SyncMetadata::class,
        DeferredSyncEvent::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun mealDao(): MealDao
    abstract fun syncDao(): SyncDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_outbox` (
                        `eventId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `schemaVersion` INTEGER NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `lastAttemptAt` INTEGER,
                        PRIMARY KEY(`eventId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_entityType_entityId` ON `sync_outbox` (`entityType`, `entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_createdAt` ON `sync_outbox` (`createdAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_tombstones` (
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`entityType`, `entityId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_metadata` (
                        `id` INTEGER NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `pullCursor` INTEGER NOT NULL,
                        `initialized` INTEGER NOT NULL,
                        `lastSyncedAt` INTEGER,
                        `lastError` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_deferred_events` (
                        `cursor` INTEGER NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `schemaVersion` INTEGER NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        PRIMARY KEY(`cursor`)
                    )
                    """.trimIndent(),
                )
            }
        }


        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sync_outbox` ADD COLUMN `dedupeKey` TEXT")
                db.execSQL("ALTER TABLE `sync_tombstones` ADD COLUMN `dedupeKey` TEXT")
                db.execSQL("ALTER TABLE `sync_deferred_events` ADD COLUMN `dedupeKey` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_tombstones_entityType_dedupeKey` ON `sync_tombstones` (`entityType`, `dedupeKey`)")
            }
        }
    }
}
