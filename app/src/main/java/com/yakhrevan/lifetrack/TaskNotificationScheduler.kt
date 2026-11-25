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
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

// ==========================================
// 1. TASK REMINDER WORKER
// ==========================================

class TaskReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = deliver(applicationContext, inputData)

    companion object {
      internal fun deliver(applicationContext: Context, inputData: Data): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val expectedDate = inputData.getString(KEY_EXPECTED_DATE)
        val expectedTime = inputData.getString(KEY_EXPECTED_TIME)
        // A queued reminder can outlive an edited, completed, or deleted task.
        val currentTask = TaskRepository.getTaskById(applicationContext, taskId)
            ?: return Result.success()
        if (currentTask.isArchived || !currentTask.reminderEnabled) return Result.success()
        if (currentTask.reminderTime.isNullOrBlank()) return Result.success()
        if (expectedDate != null && currentTask.dueDate != expectedDate) return Result.success()
        if (expectedTime != null && currentTask.reminderTime != expectedTime) return Result.success()
        val isSnooze = inputData.getBoolean(KEY_IS_SNOOZE, false)
        val occurrenceDate = inputData.getString(KEY_OCCURRENCE_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val shouldDeliver = TaskReminderSchedule.isScheduledFor(currentTask, occurrenceDate) &&
            !TaskReminderSchedule.completedFor(currentTask, occurrenceDate)
        // A fired recurring reminder must advance exactly once, regardless of the OS notification permission.
        if (!isSnooze) TaskNotificationScheduler.scheduleFollowingOccurrence(applicationContext, currentTask)
        if (!shouldDeliver || !NotificationPolicy.canNotify(applicationContext, NotificationPolicy.Module.TASKS)) return Result.success()
        val title = "Task: ${currentTask.title}"
        val message = currentTask.notificationMessage.takeIf { it.isNotBlank() }
            ?: "Due: ${currentTask.title}"

        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
        val snoozePendingIntent = LifeTrackNotificationReceiver.createTaskSnoozePendingIntent(
            applicationContext, taskId, title, taskId.hashCode(), taskId.hashCode() + 2
        )

        val snoozeMinutes = NotificationPolicy.snoozeMinutes(applicationContext, currentTask)
        val notification = NotificationPolicy.builder(applicationContext, NotificationPolicy.Module.TASKS, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Mark Done", donePendingIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze ${snoozeMinutes}m", snoozePendingIntent)
            .build()

        manager.notify(taskId.hashCode(), notification)
        NotificationHistoryRepository.record(applicationContext, "Tasks", taskId, currentTask.title, "DELIVERED")

        val followUpStage = inputData.getInt(KEY_FOLLOW_UP_STAGE, 0)
        if (followUpStage > 0) {
            TaskNotificationScheduler.scheduleFollowUp(applicationContext, currentTask, followUpStage + 1, occurrenceDate)
        } else if (!isSnooze) {
            TaskNotificationScheduler.scheduleFollowUp(applicationContext, currentTask, 1, occurrenceDate)
        }

        return Result.success()
    }

        const val KEY_TASK_ID = "task_id"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_EXPECTED_DATE = "expected_date"
        const val KEY_EXPECTED_TIME = "expected_time"
        const val KEY_OCCURRENCE_DATE = "occurrence_date"
        const val KEY_IS_SNOOZE = "is_snooze"
        const val KEY_FOLLOW_UP_STAGE = "follow_up_stage"
    }
}

// ==========================================
// 2. TASK SCHEDULER ENGINE
// ==========================================

object TaskNotificationScheduler {

    fun reconcileTaskReminder(context: Context, task: TaskItem) {
        WorkManager.getInstance(context).cancelUniqueWork("task_reminder_${task.id}")
        PreciseTaskAlarmScheduler.cancel(context, task.id)
        WorkManager.getInstance(context).cancelUniqueWork("task_follow_up_${task.id}")
        if (task.isArchived || !task.reminderEnabled || (!TaskReminderSchedule.repeating(task) && task.isCompleted)) {
            WorkManager.getInstance(context).cancelUniqueWork("snooze_task_${task.id}")
        }
        enqueueNext(context, task, LocalDateTime.now(), ExistingWorkPolicy.REPLACE)
    }

    /** Append behind the currently running work so we never cancel our own worker. */
    fun scheduleFollowingOccurrence(context: Context, task: TaskItem) {
        if (TaskReminderSchedule.repeating(task)) {
            enqueueNext(context, task, LocalDateTime.now().plusSeconds(1), ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
    }

    private fun enqueueNext(context: Context, task: TaskItem, after: LocalDateTime, policy: ExistingWorkPolicy) {

        if (task.isArchived || !task.reminderEnabled || task.reminderTime.isNullOrBlank()) {
            return
        }
        val scheduledDateTime = TaskReminderSchedule.nextOccurrence(task, after) ?: return

        val delayMillis = ChronoUnit.MILLIS.between(LocalDateTime.now(), scheduledDateTime).coerceAtLeast(1000L)
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
            .putString(TaskReminderWorker.KEY_EXPECTED_DATE, task.dueDate)
            .putString(TaskReminderWorker.KEY_EXPECTED_TIME, task.reminderTime)
            .putString(TaskReminderWorker.KEY_OCCURRENCE_DATE, scheduledDateTime.toLocalDate().toString())
            .build()

        if (PreciseTaskAlarmScheduler.schedule(context, task.id, scheduledDateTime, data)) return
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workTag,
            policy,
            request
        )
    }

    fun cancelTaskReminder(context: Context, task: TaskItem) {
        WorkManager.getInstance(context).cancelUniqueWork("task_reminder_${task.id}")
        WorkManager.getInstance(context).cancelUniqueWork("snooze_task_${task.id}")
        WorkManager.getInstance(context).cancelUniqueWork("task_follow_up_${task.id}")
        PreciseTaskAlarmScheduler.cancel(context, task.id)
    }

    /** Bounded optional escalation: at most three follow-ups for a live task. */
    fun scheduleFollowUp(context: Context, task: TaskItem, stage: Int, occurrence: LocalDate) {
        val settings = UserProfileRepository.getSettings(context)
        if (!ReminderFollowUpPolicy.shouldSchedule(settings, task, stage)) return
        val data = Data.Builder()
            .putString(TaskReminderWorker.KEY_TASK_ID, task.id)
            .putString(TaskReminderWorker.KEY_EXPECTED_DATE, task.dueDate)
            .putString(TaskReminderWorker.KEY_EXPECTED_TIME, task.reminderTime)
            .putString(TaskReminderWorker.KEY_OCCURRENCE_DATE, occurrence.toString())
            .putBoolean(TaskReminderWorker.KEY_IS_SNOOZE, true)
            .putInt(TaskReminderWorker.KEY_FOLLOW_UP_STAGE, stage)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("task_follow_up_${task.id}",
            if (stage == 1) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInitialDelay(ReminderFollowUpPolicy.delayMinutes(settings.taskFollowUpMinutes, stage), TimeUnit.MINUTES)
                .setInputData(data).build())
    }

    fun snoozeTask(context: Context, taskId: String, title: String, minutes: Int) {
        val task = TaskRepository.getTaskById(context, taskId) ?: return
        if (task.isArchived || !task.reminderEnabled || task.reminderTime.isNullOrBlank()) return
        if (TaskReminderSchedule.completedFor(task, LocalDate.now())) return
        val data = Data.Builder()
            .putString(TaskReminderWorker.KEY_TASK_ID, taskId)
            .putString(TaskReminderWorker.KEY_TITLE, "Task: ${task.title}")
            .putString(TaskReminderWorker.KEY_MESSAGE, task.notificationMessage.ifBlank { "Due: ${task.title}" })
            .putString(TaskReminderWorker.KEY_EXPECTED_DATE, task.dueDate)
            .putString(TaskReminderWorker.KEY_EXPECTED_TIME, task.reminderTime)
            .putString(TaskReminderWorker.KEY_OCCURRENCE_DATE, LocalDate.now().toString())
            .putBoolean(TaskReminderWorker.KEY_IS_SNOOZE, true)
            .build()

        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "snooze_task_${taskId}",
            ExistingWorkPolicy.REPLACE,
            request
        )
        NotificationHistoryRepository.record(context, "Tasks", taskId, task.title, "SNOOZED",
            System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L)
    }
}

/** Pure, explicit and capped escalation; never schedules when notifications are disabled. */
internal object ReminderFollowUpPolicy {
    fun shouldSchedule(settings: AppSettings, task: TaskItem, stage: Int): Boolean =
        settings.notificationsEnabled && settings.notifyTasks && settings.taskFollowUpEnabled &&
        stage in 1..settings.taskFollowUpCount.coerceIn(1, 3) && !task.isArchived &&
        task.reminderEnabled && !task.isCompleted &&
        (!settings.taskFollowUpHighPriorityOnly || task.priority == "High")

    fun delayMinutes(base: Int, stage: Int): Long =
        base.coerceIn(5, 120).toLong() * stage.coerceIn(1, 3)
}

/** Pure recurrence functions, kept testable without Android or WorkManager. */
object TaskReminderSchedule {
    fun repeating(task: TaskItem): Boolean = task.repeatType in setOf("Daily", "Weekly", "Monthly")

    fun isScheduledFor(task: TaskItem, date: LocalDate): Boolean {
        val first = runCatching { LocalDate.parse(task.dueDate) }.getOrNull() ?: return false
        if (date.isBefore(first)) return false
        return when (task.repeatType) {
            "Daily" -> true
            "Weekly" -> if (task.reminderDays.isNotEmpty()) date.dayOfWeek.value in task.reminderDays
                else date.dayOfWeek == first.dayOfWeek
            "Monthly" -> (0L..120L).asSequence().map { first.plusMonths(it) }
                .takeWhile { !it.isAfter(date) }.any { it == date }
            else -> date == first
        }
    }

    fun completedFor(task: TaskItem, date: LocalDate): Boolean {
        if (!repeating(task)) return task.isCompleted
        if (date.toString() in task.completedDates) return true
        if (!task.isCompleted) return false
        val completedOn = task.completedAtMs?.let {
            java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        return completedOn == date
    }

    fun nextOccurrence(task: TaskItem, after: LocalDateTime): LocalDateTime? {
        val first = runCatching { LocalDate.parse(task.dueDate) }.getOrNull() ?: return null
        val time = runCatching { LocalTime.parse(task.reminderTime) }.getOrNull() ?: return null
        val start = if (first.isAfter(after.toLocalDate())) first else after.toLocalDate()
        return (0L..366L).asSequence().map { start.plusDays(it) }
            .filter { isScheduledFor(task, it) && !completedFor(task, it) }
            .map { it.atTime(time) }
            .firstOrNull { it.isAfter(after) }
    }
}
