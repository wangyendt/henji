package com.qingheng.weight.health

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.qingheng.weight.HengJiApp
import com.qingheng.weight.MainActivity
import com.qingheng.weight.R
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class HealthSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HengJiApp
        val permission = runCatching { app.healthConnectSync.permissionState() }.getOrNull()
            ?: return Result.retry()
        if (!permission.anyHealthGranted || !permission.backgroundGranted) return Result.success()
        return runCatching {
            app.healthConnectSync.sync(app.settings.values.first().profile)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

class DailyBriefingWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HengJiApp
        val yesterday = LocalDate.now().minusDays(1)
        val permission = runCatching { app.healthConnectSync.permissionState() }.getOrNull()
        if (permission?.backgroundGranted == true && permission.anyHealthGranted) {
            runCatching { app.healthConnectSync.sync(app.settings.values.first().profile) }
        }
        val meals = app.repository.mealRecords.first()
        var yesterdayBriefing: com.qingheng.weight.data.DailyBriefingRecord? = null
        for (daysAgo in 1L..30L) {
            val date = LocalDate.now().minusDays(daysAgo)
            val wellness = app.repository.findWellness(date.toEpochDay())
            val briefing = buildDailyBriefing(date, meals, wellness)
            app.repository.saveBriefing(briefing)
            if (date == yesterday) yesterdayBriefing = briefing
        }
        yesterdayBriefing?.let { DailyBriefingNotifier.show(applicationContext, it) }
        return Result.success()
    }
}

object HealthSyncScheduler {
    private const val SYNC_WORK_NAME = "health_connect_sync"
    private const val BRIEFING_WORK_NAME = "daily_wellness_briefing_10am"

    fun schedule(context: Context) {
        val sync = PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            sync,
        )
        scheduleDailyBriefing(context)
    }

    fun scheduleDailyBriefing(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val delay = nextBriefingDelayMillis(now)
        val request = PeriodicWorkRequestBuilder<DailyBriefingWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BRIEFING_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun runBriefingNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "daily_wellness_briefing_now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailyBriefingWorker>().build(),
        )
    }
}

internal fun nextBriefingDelayMillis(now: ZonedDateTime): Long {
    var next = now.toLocalDate().atTime(10, 0).atZone(now.zone)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return Duration.between(now, next).toMillis().coerceAtLeast(0L)
}

private object DailyBriefingNotifier {
    private const val CHANNEL_ID = "daily_briefing"
    fun show(context: Context, briefing: com.qingheng.weight.data.DailyBriefingRecord) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "每日健康小结", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "每天上午 10 点发送前一天的饮食、运动和睡眠小结"
                }
            )
        }
        val sleep = briefing.sleepMinutes?.let { "睡眠 ${it / 60}小时${it % 60}分" } ?: "睡眠待同步"
        val activity = briefing.steps?.let { "${it}步" } ?: briefing.exerciseMinutes?.let { "运动${it}分钟" } ?: "运动待同步"
        val content = "$sleep · $activity · ${briefing.mealCount}餐"
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("昨天的健康小结准备好了")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$content\n${briefing.advice.replace('\n', ' ')}"))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        val notificationsAllowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (notificationsAllowed) {
            NotificationManagerCompat.from(context).notify(briefing.dateEpochDay.hashCode(), notification)
        }
    }
}
