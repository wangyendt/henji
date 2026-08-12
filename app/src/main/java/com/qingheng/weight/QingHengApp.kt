package com.qingheng.weight

import android.app.Application
import androidx.room.Room
import com.qingheng.weight.data.AppDatabase
import com.qingheng.weight.data.AppRepository
import com.qingheng.weight.settings.SettingsStore

class QingHengApp : Application() {
    lateinit var repository: AppRepository
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "qingheng.db")
            .fallbackToDestructiveMigration()
            .build()
        repository = AppRepository(database.weightDao(), database.mealDao())
        settings = SettingsStore(this)
    }
}

