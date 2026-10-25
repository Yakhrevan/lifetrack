package com.yakhrevan.lifetrack

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.concurrent.TimeUnit

// ==========================================
// 1. WORKER FOR HABIT & ROUTINE REMINDERS
// ==========================================

class HabitRoutineReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "LifeTrack Reminder"
        val message = inputData.getString(KEY_MESSAGE) ?: "Time for your scheduled habit or routine."
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        val type = inputData.getString(KEY_TYPE) ?: "habit" // "habit" or "routine"

        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val channelId = "lifetrack_habits_routines_channel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Habits & Routines Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "User-scheduled reminders for personal habits and routines"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        // Tap opens MainActivity
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", if (type == "routine") "Routines" else "Habits")
            putExtra("item_id", itemId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            itemId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val donePendingIntent = LifeTrackNotificationReceiver.createHabitDonePendingIntent(
            applicationContext,
            itemId,
            itemId.hashCode(),
            itemId.hashCode() + 1
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Mark Done", donePendingIntent)
            .build()

        manager.notify(itemId.hashCode(), notification)

        return Result.success()
    }

    companion object {
        const val KEY_ITEM_ID = "item_id"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_TYPE = "type"
    }
}

// ==========================================
// 2. SCHEDULER ENGINE
// ==========================================

object HabitRoutineNotificationScheduler {

    fun reconcileHabitReminders(context: Context, habit: Habit) {
        cancelHabitReminders(context, habit)

        if (habit.isPaused || habit.isArchived) return

        habit.reminders.forEachIndexed { index, reminder ->
            if (reminder.enabled) {
                scheduleHabitReminder(context, habit, reminder, index)
            }
        }
    }

    private fun scheduleHabitReminder(
        context: Context,
        habit: Habit,
        reminder: HabitReminder,
        index: Int
    ) {
        val parsedTime = runCatching { LocalTime.parse(reminder.time) }.getOrNull() ?: return
        val now = LocalDateTime.now()
        var nextTime = now.toLocalDate().atTime(parsedTime)
        if (!nextTime.isAfter(now)) {
            nextTime = nextTime.plusDays(1)
        }

        val delayMillis = ChronoUnit.MILLIS.between(now, nextTime).coerceAtLeast(1000L)
        val workTag = "habit_reminder_${habit.id}_$index"

        val data = Data.Builder()
            .putString(HabitRoutineReminderWorker.KEY_ITEM_ID, habit.id)
            .putString(HabitRoutineReminderWorker.KEY_TITLE, "Habit: ${habit.name}")
            .putString(
                HabitRoutineReminderWorker.KEY_MESSAGE,
                if (reminder.notificationMessage.isNotBlank()) reminder.notificationMessage else "Time to complete: ${habit.name}"
            )
            .putString(HabitRoutineReminderWorker.KEY_TYPE, "habit")
            .build()

        val request = PeriodicWorkRequestBuilder<HabitRoutineReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workTag,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelHabitReminders(context: Context, habit: Habit) {
        for (i in 0..10) {
            WorkManager.getInstance(context).cancelUniqueWork("habit_reminder_${habit.id}_$i")
        }
    }

    fun reconcileRoutineReminders(context: Context, routine: Routine) {
        cancelRoutineReminders(context, routine)

        if (routine.isPaused || routine.isArchived || !routine.reminderEnabled) return

        val parsedTime = runCatching { LocalTime.parse(routine.reminderTime) }.getOrNull() ?: return
        val now = LocalDateTime.now()
        var nextTime = now.toLocalDate().atTime(parsedTime)
        if (!nextTime.isAfter(now)) {
            nextTime = nextTime.plusDays(1)
        }

        val delayMillis = ChronoUnit.MILLIS.between(now, nextTime).coerceAtLeast(1000L)
        val workTag = "routine_reminder_${routine.id}"

        val data = Data.Builder()
            .putString(HabitRoutineReminderWorker.KEY_ITEM_ID, routine.id)
            .putString(HabitRoutineReminderWorker.KEY_TITLE, "Routine: ${routine.name}")
            .putString(HabitRoutineReminderWorker.KEY_MESSAGE, "Ready to start your ${routine.name}?")
            .putString(HabitRoutineReminderWorker.KEY_TYPE, "routine")
            .build()

        val request = PeriodicWorkRequestBuilder<HabitRoutineReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workTag,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelRoutineReminders(context: Context, routine: Routine) {
        WorkManager.getInstance(context).cancelUniqueWork("routine_reminder_${routine.id}")
    }

    fun snoozeReminder(context: Context, itemId: String, title: String, message: String, minutes: Int = 10) {
        val data = Data.Builder()
            .putString(HabitRoutineReminderWorker.KEY_ITEM_ID, itemId)
            .putString(HabitRoutineReminderWorker.KEY_TITLE, title)
            .putString(HabitRoutineReminderWorker.KEY_MESSAGE, message)
            .build()

        val request = OneTimeWorkRequestBuilder<HabitRoutineReminderWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "snooze_${itemId}_${System.currentTimeMillis()}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
