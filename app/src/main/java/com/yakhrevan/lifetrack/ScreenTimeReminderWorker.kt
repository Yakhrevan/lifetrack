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
        if (!NotificationPolicy.canNotify(applicationContext, NotificationPolicy.Module.WELLBEING)) return Result.success()
        val config = ScreenTimeRepository.loadRemindersConfig(applicationContext)
        if (!config.breakRemindersEnabled) return Result.success()

        val notification = NotificationPolicy.builder(applicationContext, NotificationPolicy.Module.WELLBEING, "Screen time and breaks")
            .setContentTitle("LifeTrack · Time for a Break")
            .setContentText("You've been active on your screen. Take a short ${config.breakDurationMinutes}-minute breather!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext).notify(4105, notification)
            NotificationHistoryRepository.record(applicationContext, "Digital Wellbeing", "screen_break", "Time for a break", "DELIVERED")
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }
}

class FocusCompletionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!NotificationPolicy.canNotify(applicationContext, NotificationPolicy.Module.STUDY)) return Result.success()
        val notification = NotificationPolicy.builder(applicationContext, NotificationPolicy.Module.STUDY, "Focus completion", NotificationManager.IMPORTANCE_HIGH)
            .setContentTitle("LifeTrack · Focus Complete! \uD83C\uDF31")
            .setContentText("Great job! Your focus session has ended successfully.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext).notify(4106, notification)
            NotificationHistoryRepository.record(applicationContext, "Study", "focus_complete", "Focus complete", "DELIVERED")
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
