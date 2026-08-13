package com.qingheng.weight.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qingheng.weight.HengJiApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class PersonalSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HengJiApp
        val settings = app.settings.values.first()
        if (settings.personalSyncToken.isBlank() || settings.personalSyncUrl.isBlank()) return Result.success()
        return runCatching {
            app.personalSync.sync(settings.personalSyncUrl, settings.personalSyncToken)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

object PersonalSyncScheduler {
    private const val PERIODIC_WORK = "personal_data_sync"
    private const val IMMEDIATE_WORK = "personal_data_sync_now"
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PersonalSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PersonalSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
