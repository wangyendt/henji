package com.qingheng.weight

import android.app.NotificationManager
import android.app.Application
import android.os.Build
import androidx.room.Room
import com.qingheng.weight.data.AppDatabase
import com.qingheng.weight.data.AppRepository
import com.qingheng.weight.health.HealthConnectSync
import com.qingheng.weight.health.HealthSyncScheduler
import com.qingheng.weight.settings.SettingsStore
import com.qingheng.weight.sync.PersonalSyncEngine
import com.qingheng.weight.sync.PersonalSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HengJiApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: AppRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var healthConnectSync: HealthConnectSync
        private set
    lateinit var personalSync: PersonalSyncEngine
        private set

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).deleteNotificationChannel("daily_briefing")
        }
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "qingheng.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_10,
                AppDatabase.MIGRATION_9_10,
            )
            .build()
        repository = AppRepository(
            weights = database.weightDao(),
            meals = database.mealDao(),
            sync = database.syncDao(),
            database = database,
            workouts = database.workoutDao(),
        )
        settings = SettingsStore(this)
        applicationScope.launch { settings.migratePersonalSyncUrl() }
        healthConnectSync = HealthConnectSync(this, repository)
        personalSync = PersonalSyncEngine(database)
        HealthSyncScheduler.schedule(this)
        PersonalSyncScheduler.schedule(this)
    }
}
