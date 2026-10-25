package com.yakhrevan.lifetrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ScreenTimeReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val config = ScreenTimeRepository.loadRemindersConfig(applicationContext)
        if (!config.breakRemindersEnabled) return Result.success()

        val channelId = "lifetrack_screentime"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Screen Time & Breaks", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Break and digital wellbeing reminders from LifeTrack"
                }
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LifeTrack · Time for a Break")
            .setContentText("You've been active on your screen. Take a short ${config.breakDurationMinutes}-minute breather!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext).notify(4105, notification)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }
}

class FocusCompletionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val channelId = "lifetrack_focus"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Focus Mode", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Focus session completion alerts"
                }
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LifeTrack · Focus Complete! \uD83C\uDF31")
            .setContentText("Great job! Your focus session has ended successfully.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext).notify(4106, notification)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }
}

fun scheduleScreenTimeReminders(context: Context, intervalMinutes: Int, enabled: Boolean) {
    val config = ScreenTimeRepository.loadRemindersConfig(context)
    ScreenTimeRepository.saveRemindersConfig(context, config.copy(
        breakRemindersEnabled = enabled,
        reminderIntervalMinutes = intervalMinutes.coerceIn(15, 240)
    ))

    if (!enabled) {
        WorkManager.getInstance(context).cancelUniqueWork("lifetrack-screentime-reminders")
        return
    }

    val request = PeriodicWorkRequestBuilder<ScreenTimeReminderWorker>(
        intervalMinutes.coerceIn(15, 240).toLong(),
        TimeUnit.MINUTES
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "lifetrack-screentime-reminders",
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}

fun scheduleFocusCompletionAlert(context: Context, durationMinutes: Int) {
    val request = OneTimeWorkRequestBuilder<FocusCompletionWorker>()
        .setInitialDelay(durationMinutes.toLong(), TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "lifetrack-focus-timer",
        ExistingWorkPolicy.REPLACE,
        request
    )
}

fun cancelFocusCompletionAlert(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork("lifetrack-focus-timer")
}
