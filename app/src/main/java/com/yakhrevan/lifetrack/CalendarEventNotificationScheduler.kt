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
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Schedules reminders for user-created calendar events without changing Calendar UI.
 * Work is uniquely keyed by event ID so editing/deleting an event replaces/cancels
 * the previous pending reminder instead of producing duplicates.
 */
object CalendarEventNotificationScheduler {
    private const val WORK_PREFIX = "calendar_event_reminder_"

    fun reconcile(context: Context, items: List<CalendarItem>) {
        val workManager = WorkManager.getInstance(context)
        val activeIds = items.filter { it.type == ActivityType.EVENT && !it.isCompleted }
            .map { it.id }.toSet()

        // Cancel stale scheduled event work after edits/deletions.
        val prefs = context.getSharedPreferences("lifetrack_calendar_reminders", Context.MODE_PRIVATE)
        val previousIds = prefs.getStringSet("scheduled_event_ids", emptySet()).orEmpty()
        (previousIds - activeIds).forEach { workManager.cancelUniqueWork(WORK_PREFIX + it) }

        items.filter { it.type == ActivityType.EVENT }.forEach { schedule(context, it) }
        prefs.edit().putStringSet("scheduled_event_ids", activeIds).apply()
    }

    fun schedule(context: Context, item: CalendarItem) {
        val workName = WORK_PREFIX + item.id
        WorkManager.getInstance(context).cancelUniqueWork(workName)
        scheduleNext(context, item, LocalDateTime.now(), ExistingWorkPolicy.REPLACE)
    }

    internal fun scheduleNext(
        context: Context,
        item: CalendarItem,
        after: LocalDateTime,
        policy: ExistingWorkPolicy
    ) {
        if (item.type != ActivityType.EVENT || item.isCompleted || item.reminder.isBlank()) return
        val eventDate = runCatching { LocalDate.parse(item.date) }.getOrNull() ?: return
        val eventTime = parseTime(item.startTime) ?: return
        val leadMinutes = parseLeadMinutes(item.reminder) ?: return
        val nextEvent = nextOccurrence(item, eventDate, eventTime, after) ?: return
        var notifyAt = nextEvent.minusMinutes(leadMinutes)
        if (!notifyAt.isAfter(after)) {
            val later = nextOccurrence(item, eventDate, eventTime, nextEvent.plusSeconds(1)) ?: return
            notifyAt = later.minusMinutes(leadMinutes)
        }
        if (notifyAt.isAfter(after)) enqueue(context, item, notifyAt, WORK_PREFIX + item.id, policy)
    }

    private fun enqueue(context: Context, item: CalendarItem, notifyAt: LocalDateTime, workName: String, policy: ExistingWorkPolicy) {
        val delay = ChronoUnit.MILLIS.between(LocalDateTime.now(), notifyAt).coerceAtLeast(1_000L)
        val data = Data.Builder().putString(KEY_EVENT_ID, item.id).build()
        val request = OneTimeWorkRequestBuilder<CalendarEventReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName, policy, request)
    }

    internal fun nextOccurrence(
        item: CalendarItem,
        firstDate: LocalDate,
        time: LocalTime,
        after: LocalDateTime
    ): LocalDateTime? {
        repeat(3_660) { occurrenceIndex ->
            val date = when (item.repeatOption) {
                RepeatOption.NONE, RepeatOption.CUSTOM -> if (occurrenceIndex == 0) firstDate else return null
                RepeatOption.DAILY -> firstDate.plusDays(occurrenceIndex.toLong())
                RepeatOption.WEEKLY -> firstDate.plusWeeks(occurrenceIndex.toLong())
                // Calculate each month from the original date to avoid Jan 31 -> Feb 28 -> Mar 28 drift.
                RepeatOption.MONTHLY -> runCatching { firstDate.plusMonths(occurrenceIndex.toLong()) }.getOrNull() ?: return null
            }
            val candidate = date.atTime(time)
            if (candidate.isAfter(after)) return candidate
        }
        return null
    }

    internal fun parseLeadMinutes(value: String): Long? {
        val match = Regex("(\\d+)\\s*(minute|min|hour|hr|day)", RegexOption.IGNORE_CASE)
            .find(value) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2].lowercase(Locale.ROOT)
        val minutes = when {
            unit.startsWith("min") -> amount
            unit.startsWith("hour") || unit == "hr" -> amount * 60L
            unit == "day" -> amount * 1_440L
            else -> return null
        }
        return minutes.coerceAtLeast(0L)
    }

    internal fun parseTime(value: String): LocalTime? {
        val normalized = value.trim().uppercase(Locale.ROOT)
        val formatters = listOf(
            DateTimeFormatter.ofPattern("h:mm a", Locale.US),
            DateTimeFormatter.ofPattern("hh:mm a", Locale.US),
            DateTimeFormatter.ofPattern("H:mm", Locale.US),
            DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        )
        return formatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalTime.parse(normalized, formatter) }.getOrNull()
        }
    }

    private const val KEY_EVENT_ID = "calendar_event_id"

    internal fun eventIdKey() = KEY_EVENT_ID
}

class CalendarEventReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val eventId = inputData.getString(CalendarEventNotificationScheduler.eventIdKey())
            ?: return Result.failure()
        val item = CalendarStorage.load(applicationContext).firstOrNull {
            it.id == eventId && it.type == ActivityType.EVENT && !it.isCompleted
        } ?: return Result.success()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "lifetrack_calendar_events"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Calendar event reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders for events saved in LifeTrack Calendar"
                }
            )
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "Calendar")
                putExtra("calendar_event_id", item.id)
            }
            val pending = PendingIntent.getActivity(
                applicationContext, item.id.hashCode(), openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val body = buildString {
                append(if (item.isAllDay) "All-day event" else "Starts at ${item.startTime}")
                if (item.location.isNotBlank()) append(" • ${item.location}")
                if (item.description.isNotBlank()) append("\n${item.description}")
            }
            notificationManager.notify(
                ("calendar_event_" + item.id).hashCode(),
                NotificationCompat.Builder(applicationContext, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(item.title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .build()
            )
        }

        // Append the next recurrence behind this completed work instead of cancelling
        // the currently executing unique work request.
        CalendarEventNotificationScheduler.scheduleNext(
            applicationContext, item, LocalDateTime.now(), ExistingWorkPolicy.APPEND_OR_REPLACE
        )
        return Result.success()
    }
}
