package com.qingheng.weight.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qingheng.weight.HengJiApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class HealthSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HengJiApp
        val permission = runCatching { app.healthConnectSync.permissionState() }.getOrNull()
            ?: return Result.retry()
        if (!permission.coreGranted || !permission.backgroundGranted) return Result.success()
        return runCatching {
            app.healthConnectSync.sync(app.settings.values.first().profile)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

object HealthSyncScheduler {
    private const val WORK_NAME = "fitdays_health_connect_sync"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
