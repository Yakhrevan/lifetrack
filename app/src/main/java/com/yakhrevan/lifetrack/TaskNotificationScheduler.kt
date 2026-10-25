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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

// ==========================================
// 1. TASK REMINDER WORKER
// ==========================================

class TaskReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Task Reminder"
        val message = inputData.getString(KEY_MESSAGE) ?: "You have a task scheduled."

        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val channelId = "lifetrack_tasks_channel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Tasks Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for scheduled user tasks and to-do deadlines"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "Tasks")
            putExtra("task_id", taskId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            taskId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val donePendingIntent = LifeTrackNotificationReceiver.createTaskDonePendingIntent(
            applicationContext,
            taskId,
            taskId.hashCode(),
            taskId.hashCode() + 1
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Mark Done", donePendingIntent)
            .build()

        manager.notify(taskId.hashCode(), notification)

        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
    }
}

// ==========================================
// 2. TASK SCHEDULER ENGINE
// ==========================================

object TaskNotificationScheduler {

    fun reconcileTaskReminder(context: Context, task: TaskItem) {
        cancelTaskReminder(context, task)

        if (task.isCompleted || task.isArchived || !task.reminderEnabled || task.reminderTime.isNullOrBlank()) {
            return
        }

        val parsedTime = runCatching { LocalTime.parse(task.reminderTime) }.getOrNull() ?: return
        val parsedDueDate = runCatching { LocalDate.parse(task.dueDate) }.getOrNull() ?: LocalDate.now()
        val scheduledDateTime = parsedDueDate.atTime(parsedTime)
        val now = LocalDateTime.now()

        if (!scheduledDateTime.isAfter(now)) {
            // Already in the past
            return
        }

        val delayMillis = ChronoUnit.MILLIS.between(now, scheduledDateTime).coerceAtLeast(1000L)
        val workTag = "task_reminder_${task.id}"

        val messageText = if (task.notificationMessage.isNotBlank()) {
            task.notificationMessage
        } else {
            "Due: ${task.title}"
        }

        val data = Data.Builder()
            .putString(TaskReminderWorker.KEY_TASK_ID, task.id)
            .putString(TaskReminderWorker.KEY_TITLE, "Task: ${task.title}")
            .putString(TaskReminderWorker.KEY_MESSAGE, messageText)
            .build()

        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workTag,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelTaskReminder(context: Context, task: TaskItem) {
        WorkManager.getInstance(context).cancelUniqueWork("task_reminder_${task.id}")
    }

    fun snoozeTask(context: Context, taskId: String, title: String, minutes: Int = 10) {
        val data = Data.Builder()
            .putString(TaskReminderWorker.KEY_TASK_ID, taskId)
            .putString(TaskReminderWorker.KEY_TITLE, title)
            .putString(TaskReminderWorker.KEY_MESSAGE, "Snoozed task reminder")
            .build()

        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "snooze_task_${taskId}_${System.currentTimeMillis()}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
