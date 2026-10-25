package com.yakhrevan.lifetrack

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class WaterReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = WaterRepository.loadSettings(applicationContext)
        if (!settings.remindersEnabled) return Result.success()

        // 1. Check snooze
        if (settings.snoozeUntilMs > System.currentTimeMillis()) {
            return Result.success()
        }

        // 2. Check active hours & quiet hours
        val currentHour = LocalTime.now().hour
        if (settings.quietHoursEnabled) {
            if (currentHour < settings.activeStartHour || currentHour >= settings.activeEndHour) {
                return Result.success()
            }
        }

        // 3. Check if daily goal is already met today (avoid pestering user once target is completed)
        val todayStr = java.time.LocalDate.now().toString()
        val entries = WaterRepository.loadEntries(applicationContext)
        val todayTotal = entries.filter { it.date == todayStr }.sumOf { it.amountMl }
        if (todayTotal >= settings.dailyGoalMl) {
            return Result.success()
        }

        // 4. Android 13+ Notification Permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val channelId = "lifetrack_water"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Hydration Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Personalized reminders to stay hydrated throughout the day"
                }
            )
        }

        val remainingMl = (settings.dailyGoalMl - todayTotal).coerceAtLeast(0)
        val remainingFormatted = settings.unit.format(remainingMl)

        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_page", "Water")
        }
        val pendingIntent = if (intent != null) {
            android.app.PendingIntent.getActivity(
                applicationContext,
                4102,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val log250Pending = LifeTrackNotificationReceiver.createWaterActionPendingIntent(
            applicationContext,
            250,
            4102,
            5101
        )
        val log500Pending = LifeTrackNotificationReceiver.createWaterActionPendingIntent(
            applicationContext,
            500,
            4102,
            5102
        )

        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LifeTrack · Hydration Moment")
            .setContentText("Time for a refreshing drink. $remainingFormatted to reach your daily goal.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Take a mindful sip of water or herbal tea. Keeping hydrated throughout the day supports focus, energy, and overall wellbeing!"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_input_add, "+250 ml", log250Pending)
            .addAction(android.R.drawable.ic_input_add, "+500 ml", log500Pending)

        if (pendingIntent != null) {
            notificationBuilder.setContentIntent(pendingIntent)
        }

        val notification = notificationBuilder.build()

        return try {
            NotificationManagerCompat.from(applicationContext).notify(4102, notification)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }
}

fun scheduleWaterReminders(context: Context, intervalMinutes: Int, enabled: Boolean) {
    if (!enabled) {
        WorkManager.getInstance(context).cancelUniqueWork("lifetrack-water-reminders")
        return
    }
    val safeInterval = intervalMinutes.coerceIn(15, 360)
    val request = PeriodicWorkRequestBuilder<WaterReminderWorker>(safeInterval.toLong(), TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "lifetrack-water-reminders",
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}

fun snoozeWaterReminder(context: Context, snoozeMinutes: Int = 30) {
    val settings = WaterRepository.loadSettings(context)
    val snoozeUntil = System.currentTimeMillis() + (snoozeMinutes * 60_000L)
    WaterRepository.saveSettings(context, settings.copy(snoozeUntilMs = snoozeUntil))

    // Schedule a one-time reminder when snooze expires
    val snoozeRequest = OneTimeWorkRequestBuilder<WaterReminderWorker>()
        .setInitialDelay(snoozeMinutes.toLong(), TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "lifetrack-water-snooze",
        ExistingWorkPolicy.REPLACE,
        snoozeRequest
    )
}
