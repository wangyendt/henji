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
        WorkoutRecord::class,
        SyncOutboxEvent::class,
        SyncTombstone::class,
        SyncMetadata::class,
        DeferredSyncEvent::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun mealDao(): MealDao
    abstract fun workoutDao(): WorkoutDao
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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_wellness_records` (
                        `id` TEXT NOT NULL,
                        `dateEpochDay` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `screenType` TEXT NOT NULL,
                        `sleepStartAt` INTEGER,
                        `sleepEndAt` INTEGER,
                        `sleepMinutes` INTEGER,
                        `deepSleepMinutes` INTEGER,
                        `lightSleepMinutes` INTEGER,
                        `remSleepMinutes` INTEGER,
                        `awakeMinutes` INTEGER,
                        `sleepScore` REAL,
                        `steps` INTEGER,
                        `distanceMeters` REAL,
                        `activeCaloriesKcal` REAL,
                        `exerciseMinutes` INTEGER,
                        `exerciseCaloriesKcal` REAL,
                        `restingHeartRateBpm` REAL,
                        `averageHeartRateBpm` REAL,
                        `workoutsJson` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `rawAnalysis` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_wellness_records_source_dateEpochDay` ON `daily_wellness_records` (`source`, `dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_wellness_records_dateEpochDay` ON `daily_wellness_records` (`dateEpochDay`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workout_records` (
                        `id` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `workoutType` TEXT NOT NULL,
                        `workoutCategory` TEXT NOT NULL,
                        `startAt` INTEGER NOT NULL,
                        `durationSeconds` INTEGER,
                        `distanceMeters` REAL,
                        `caloriesKcal` REAL,
                        `averageHeartRateBpm` REAL,
                        `maximumHeartRateBpm` REAL,
                        `averagePaceSecondsPerKm` REAL,
                        `averagePaceSecondsPer100Meters` REAL,
                        `averageCadencePerMinute` REAL,
                        `steps` INTEGER,
                        `averageStrideCentimeters` REAL,
                        `elevationGainMeters` REAL,
                        `poolLengthMeters` REAL,
                        `lengths` INTEGER,
                        `strokes` INTEGER,
                        `averageSwolf` REAL,
                        `averageStrokeRatePerMinute` REAL,
                        `mainStroke` TEXT,
                        `confidence` REAL NOT NULL,
                        `rawAnalysis` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `workout_records` (
                        `id`, `updatedAt`, `source`, `workoutType`, `workoutCategory`, `startAt`,
                        `durationSeconds`, `distanceMeters`, `caloriesKcal`,
                        `averageHeartRateBpm`, `maximumHeartRateBpm`,
                        `averagePaceSecondsPerKm`, `averagePaceSecondsPer100Meters`,
                        `averageCadencePerMinute`, `steps`, `averageStrideCentimeters`,
                        `elevationGainMeters`, `poolLengthMeters`, `lengths`, `strokes`,
                        `averageSwolf`, `averageStrokeRatePerMinute`, `mainStroke`,
                        `confidence`, `rawAnalysis`
                    )
                    SELECT
                        'vivo-workout-' ||
                            CAST(CAST(strftime('%s', json_extract(workout.value, '$.startAt')) AS INTEGER) * 1000 AS TEXT) ||
                            '-' || json_extract(workout.value, '$.type'),
                        legacy.updatedAt,
                        'vivo_health_share',
                        json_extract(workout.value, '$.type'),
                        CASE
                            WHEN json_extract(workout.value, '$.type') LIKE '%游泳%' THEN 'swimming'
                            WHEN json_extract(workout.value, '$.type') LIKE '%跑%' THEN 'running'
                            WHEN json_extract(workout.value, '$.type') LIKE '%步行%'
                              OR json_extract(workout.value, '$.type') LIKE '%健走%' THEN 'walking'
                            ELSE 'other'
                        END,
                        CAST(strftime('%s', json_extract(workout.value, '$.startAt')) AS INTEGER) * 1000,
                        CAST(round(json_extract(workout.value, '$.durationMinutes') * 60.0) AS INTEGER),
                        json_extract(workout.value, '$.distanceKm') * 1000.0,
                        json_extract(workout.value, '$.caloriesKcal'),
                        json_extract(workout.value, '$.averageHeartRateBpm'),
                        json_extract(workout.value, '$.maximumHeartRateBpm'),
                        NULL, NULL, NULL, NULL, NULL, NULL,
                        NULL, NULL, NULL, NULL, NULL, NULL,
                        legacy.confidence,
                        legacy.rawAnalysis
                    FROM `daily_wellness_records` AS legacy,
                         json_each(legacy.workoutsJson) AS workout
                    WHERE json_valid(legacy.workoutsJson)
                      AND json_extract(workout.value, '$.startAt') IS NOT NULL
                      AND length(trim(json_extract(workout.value, '$.type'))) > 0
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_records_source_startAt_workoutType` ON `workout_records` (`source`, `startAt`, `workoutType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_records_startAt` ON `workout_records` (`startAt`)")
                db.execSQL("DELETE FROM `sync_outbox` WHERE `entityType` = 'daily_wellness_record'")
                db.execSQL("UPDATE `sync_metadata` SET `initialized` = 0")
                db.execSQL("DROP TABLE IF EXISTS `daily_wellness_records`")
            }
        }

        // Version 9 was a short-lived local build that experimented with cloud image fields.
        // Fresh v8 installs can skip it because the durable sync contract remains data-only.
        val MIGRATION_8_10 = object : Migration(8, 10) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE `sync_outbox` SET `payloadJson` = json_remove(`payloadJson`, '$.imageMediaId', '$.imageMimeType') WHERE json_valid(`payloadJson`)",
                )
                db.execSQL(
                    "UPDATE `sync_deferred_events` SET `payloadJson` = json_remove(`payloadJson`, '$.imageMediaId', '$.imageMimeType') WHERE json_valid(`payloadJson`)",
                )

                db.execSQL("ALTER TABLE `meal_records` RENAME TO `meal_records_v9`")
                db.execSQL(
                    """
                    CREATE TABLE `meal_records` (
                        `id` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `mealType` TEXT NOT NULL,
                        `imageUri` TEXT,
                        `foodNames` TEXT NOT NULL,
                        `calorieLow` INTEGER NOT NULL,
                        `calorieHigh` INTEGER NOT NULL,
                        `proteinGrams` REAL,
                        `carbsGrams` REAL,
                        `fatGrams` REAL,
                        `advice` TEXT NOT NULL,
                        `confidence` REAL,
                        `rawAnalysis` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `meal_records`
                    SELECT `id`, `createdAt`, `mealType`, `imageUri`, `foodNames`, `calorieLow`, `calorieHigh`,
                           `proteinGrams`, `carbsGrams`, `fatGrams`, `advice`, `confidence`, `rawAnalysis`
                    FROM `meal_records_v9`
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE `meal_food_items_v10` (
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
                db.execSQL("INSERT INTO `meal_food_items_v10` SELECT * FROM `meal_food_items`")
                db.execSQL("DROP TABLE `meal_food_items`")
                db.execSQL("DROP TABLE `meal_records_v9`")
                db.execSQL("ALTER TABLE `meal_food_items_v10` RENAME TO `meal_food_items`")
                db.execSQL("CREATE INDEX `index_meal_food_items_mealId` ON `meal_food_items` (`mealId`)")
                db.execSQL("CREATE INDEX `index_meal_food_items_canonicalName` ON `meal_food_items` (`canonicalName`)")

                db.execSQL("ALTER TABLE `workout_records` RENAME TO `workout_records_v9`")
                db.execSQL(
                    """
                    CREATE TABLE `workout_records` (
                        `id` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `workoutType` TEXT NOT NULL,
                        `workoutCategory` TEXT NOT NULL,
                        `startAt` INTEGER NOT NULL,
                        `durationSeconds` INTEGER,
                        `distanceMeters` REAL,
                        `caloriesKcal` REAL,
                        `averageHeartRateBpm` REAL,
                        `maximumHeartRateBpm` REAL,
                        `averagePaceSecondsPerKm` REAL,
                        `averagePaceSecondsPer100Meters` REAL,
                        `averageCadencePerMinute` REAL,
                        `steps` INTEGER,
                        `averageStrideCentimeters` REAL,
                        `elevationGainMeters` REAL,
                        `poolLengthMeters` REAL,
                        `lengths` INTEGER,
                        `strokes` INTEGER,
                        `averageSwolf` REAL,
                        `averageStrokeRatePerMinute` REAL,
                        `mainStroke` TEXT,
                        `confidence` REAL NOT NULL,
                        `rawAnalysis` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `workout_records`
                    SELECT `id`, `updatedAt`, `source`, `workoutType`, `workoutCategory`, `startAt`,
                           `durationSeconds`, `distanceMeters`, `caloriesKcal`, `averageHeartRateBpm`,
                           `maximumHeartRateBpm`, `averagePaceSecondsPerKm`, `averagePaceSecondsPer100Meters`,
                           `averageCadencePerMinute`, `steps`, `averageStrideCentimeters`, `elevationGainMeters`,
                           `poolLengthMeters`, `lengths`, `strokes`, `averageSwolf`,
                           `averageStrokeRatePerMinute`, `mainStroke`, `confidence`, `rawAnalysis`
                    FROM `workout_records_v9`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `workout_records_v9`")
                db.execSQL("CREATE UNIQUE INDEX `index_workout_records_source_startAt_workoutType` ON `workout_records` (`source`, `startAt`, `workoutType`)")
                db.execSQL("CREATE INDEX `index_workout_records_startAt` ON `workout_records` (`startAt`)")
            }
        }
    }
}
