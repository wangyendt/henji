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

class HengJiApp : Application() {
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
            )
            .build()
        repository = AppRepository(database.weightDao(), database.mealDao(), database.syncDao(), database)
        settings = SettingsStore(this)
        healthConnectSync = HealthConnectSync(this, repository)
        personalSync = PersonalSyncEngine(database)
        HealthSyncScheduler.schedule(this)
        PersonalSyncScheduler.schedule(this)
    }
}
