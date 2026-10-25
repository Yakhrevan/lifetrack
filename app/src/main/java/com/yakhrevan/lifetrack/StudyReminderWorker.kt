package com.yakhrevan.lifetrack

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

class StudyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val reminderType = inputData.getString(KEY_TYPE) ?: "study"
        val channelId = "lifetrack_study_reminders"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Study & Focus Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders for scheduled study sessions, break alerts, and daily goals"
                }
            )
        }

        val (title, text) = when (reminderType) {
            "break" -> Pair("Time for a Break!", "You've been focusing hard. Take a 5-minute break to recharge.")
            "goal" -> Pair("Daily Study Goal", "You're close to reaching your daily study goal today!")
            else -> Pair("Time to Study!", "Stay consistent with your learning goals. Start a focus session now.")
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()

        manager.notify(reminderType.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_TYPE = "reminder_type"
    }
}

object StudyReminderScheduler {
    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = (next.timeInMillis - now.timeInMillis).coerceAtLeast(1L)
        val request = PeriodicWorkRequestBuilder<StudyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(StudyReminderWorker.KEY_TYPE, "study").build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "study_daily_reminder",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("study_daily_reminder")
    }

    fun scheduleBreakAlert(context: Context, durationMinutes: Int) {
        val request = OneTimeWorkRequestBuilder<StudyReminderWorker>()
            .setInitialDelay(durationMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(Data.Builder().putString(StudyReminderWorker.KEY_TYPE, "break").build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "study_break_alert",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
