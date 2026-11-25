package com.yakhrevan.lifetrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** One local, bounded audit trail of app notifications, never uploaded. */
data class ReminderRecord(
    val module: String,
    val itemId: String,
    val title: String,
    val state: String,
    val timestampMs: Long,
    val snoozeUntilMs: Long = 0L
)

internal object ReminderRecordCodec {
    fun encode(records: List<ReminderRecord>): String = JSONArray().apply {
        records.forEach { r ->
            put(JSONObject().put("module", r.module).put("itemId", r.itemId)
                .put("title", r.title).put("state", r.state)
                .put("at", r.timestampMs).put("snoozeUntil", r.snoozeUntilMs))
        }
    }.toString()

    fun decode(raw: String?): List<ReminderRecord> = runCatching {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).mapNotNull { index ->
            val obj = arr.optJSONObject(index) ?: return@mapNotNull null
            val module = obj.optString("module")
            val id = obj.optString("itemId")
            if (module.isBlank() || id.isBlank()) return@mapNotNull null
            ReminderRecord(module, id, obj.optString("title"), obj.optString("state"),
                obj.optLong("at"), obj.optLong("snoozeUntil"))
        }
    }.getOrDefault(emptyList())
}

object NotificationHistoryRepository {
    private const val PREFS = "lifetrack_notification_history_v1"
    private const val KEY = "reminders"
    private const val MAX = 200

    @Synchronized fun read(context: Context): List<ReminderRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return ReminderRecordCodec.decode(raw)
    }

    @Synchronized fun record(
        context: Context, module: String, itemId: String, title: String,
        state: String, snoozeUntilMs: Long = 0L
    ) {
        if (itemId.isBlank()) return
        val now = System.currentTimeMillis()
        val recent = read(context).filterNot {
            it.module == module && it.itemId == itemId && it.state == state && now - it.timestampMs < 30_000L
        }
        val next = (listOf(ReminderRecord(module, itemId, title.take(120), state, now, snoozeUntilMs)) + recent)
            .take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, ReminderRecordCodec.encode(next)).apply()
    }
}

/** Build the queue from authoritative saved schedules, never from stale worker requests. */
internal object ReminderQueueProjector {
    fun project(
        now: LocalDateTime,
        tasks: List<TaskItem>, events: List<CalendarItem>,
        habits: List<Habit>, routines: List<Routine>,
        completions: List<HabitCompletion>, sessions: List<RoutineSession>
    ): List<ReminderRecord> {
        val today = now.toLocalDate()
        val todayString = today.toString()
        fun record(module: String, id: String, title: String, dateTime: LocalDateTime): ReminderRecord {
            val timestamp = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return ReminderRecord(module, id, title, if (dateTime.isBefore(now)) "MISSED" else "UPCOMING", timestamp)
        }
        val taskRecords = tasks.filter { it.reminderEnabled && !it.isArchived &&
            (TaskReminderSchedule.repeating(it) || !it.isCompleted) }
            .mapNotNull { task ->
                val time = runCatching { LocalTime.parse(task.reminderTime) }.getOrNull() ?: return@mapNotNull null
                val dateTime = if (TaskReminderSchedule.repeating(task)) {
                    if (TaskReminderSchedule.isScheduledFor(task, today) &&
                        !TaskReminderSchedule.completedFor(task, today)) today.atTime(time)
                    else TaskReminderSchedule.nextOccurrence(task, now)
                } else runCatching { LocalDate.parse(task.dueDate).atTime(time) }.getOrNull()
                dateTime?.let { record("Tasks", task.id, task.title, it) }
            }
        val eventRecords = events.filter { it.type == ActivityType.EVENT && !it.isCompleted && it.reminder.isNotBlank() }
            .mapNotNull { event ->
                val time = if (event.isAllDay) LocalTime.MIDNIGHT
                    else CalendarEventNotificationScheduler.parseTime(event.startTime) ?: return@mapNotNull null
                val lead = CalendarEventNotificationScheduler.parseLeadMinutes(event.reminder) ?: 0L
                val candidate = (0L..30L).asSequence().map { today.plusDays(it) }
                    .firstOrNull { ProductivityEngine.calendarOccurs(event, it) }
                    ?: return@mapNotNull null
                record("Calendar", event.id, event.title, candidate.atTime(time).minusMinutes(lead))
            }
        val habitRecords = habits.filter { !it.isPaused && !it.isArchived }
            .flatMap { habit -> habit.reminders.filter { it.enabled }.mapNotNull { reminder ->
                val time = runCatching { LocalTime.parse(reminder.time) }.getOrNull() ?: return@mapNotNull null
                val date = (0L..14L).asSequence().map { today.plusDays(it) }
                    .firstOrNull { day ->
                        HabitRoutineRepository.isHabitScheduled(habit, day) &&
                        (reminder.weekdays.isEmpty() || day.dayOfWeek.value in reminder.weekdays) &&
                        !(day == today && completions.any { it.habitId == habit.id && it.date == todayString && it.status == "completed" })
                    } ?: return@mapNotNull null
                record("Habits", habit.id, habit.name, date.atTime(time))
            } }
        val routineRecords = routines.filter { !it.isPaused && !it.isArchived && it.reminderEnabled }
            .mapNotNull { routine ->
                val time = runCatching { LocalTime.parse(routine.reminderTime) }.getOrNull() ?: return@mapNotNull null
                val date = (0L..14L).asSequence().map { today.plusDays(it) }
                    .firstOrNull { day ->
                        HabitRoutineRepository.isRoutineScheduled(routine, day) &&
                        !(day == today && sessions.any { it.routineId == routine.id && it.date == todayString && it.isCompleted })
                    } ?: return@mapNotNull null
                record("Routines", routine.id, routine.name, date.atTime(time))
            }
        return (taskRecords + eventRecords + habitRecords + routineRecords).sortedBy { it.timestampMs }
    }
}

data class PlannerEntry(
    val kind: String,
    val title: String,
    val time: String,
    val itemId: String,
    val completed: Boolean,
    val priority: String = "Medium"
)

data class ProductivitySummary(
    val completed: Int,
    val planned: Int,
    val studyMinutes: Int,
    val overdue: Int,
    val missedAlerts: Int
) {
    val completionPercent: Int get() = if (planned == 0) 0 else completed * 100 / planned
}

/** Pure projections used by Today and Insights; no synthetic demonstration data. */
internal object ProductivityEngine {
    fun calendarOccurs(item: CalendarItem, day: LocalDate): Boolean {
        val first = runCatching { LocalDate.parse(item.date) }.getOrNull() ?: return false
        if (day.isBefore(first)) return false
        return when (item.repeatOption) {
            RepeatOption.DAILY -> true
            RepeatOption.WEEKLY -> java.time.temporal.ChronoUnit.DAYS.between(first, day) % 7L == 0L
            RepeatOption.MONTHLY -> {
                val months = java.time.temporal.ChronoUnit.MONTHS.between(
                    java.time.YearMonth.from(first), java.time.YearMonth.from(day))
                months >= 0L && first.plusMonths(months) == day
            }
            else -> first == day
        }
    }

    fun entries(
        day: LocalDate, tasks: List<TaskItem>, events: List<CalendarItem>,
        habits: List<Habit>, completions: List<HabitCompletion>,
        routines: List<Routine>, routineSessions: List<RoutineSession>,
        studies: List<StudySessionRecord>, studyTopics: List<Topic> = emptyList()
    ): List<PlannerEntry> {
        val dayString = day.toString()
        val taskEntries = tasks.filter { !it.isArchived && TaskReminderSchedule.isScheduledFor(it, day) }
            .map { PlannerEntry("Tasks", it.title, it.dueTime ?: it.reminderTime.orEmpty(),
                it.id, TaskReminderSchedule.completedFor(it, day), it.priority) }
        val eventEntries = events.filter { calendarOccurs(it, day) }
            .map { PlannerEntry("Calendar", it.title, if (it.isAllDay) "" else it.startTime,
                it.id, it.isCompleted) }
        val habitEntries = habits.filter { !it.isArchived && !it.isPaused && HabitRoutineRepository.isHabitScheduled(it, day) }
            .map { habit -> PlannerEntry("Habits", habit.name, habit.reminders.firstOrNull()?.time.orEmpty(),
                habit.id, completions.any { it.habitId == habit.id && it.date == dayString && it.status == "completed" }) }
        val routineEntries = routines.filter { !it.isArchived && !it.isPaused && HabitRoutineRepository.isRoutineScheduled(it, day) }
            .map { routine -> PlannerEntry("Routines", routine.name, routine.startTime, routine.id,
                routineSessions.any { it.routineId == routine.id && it.date == dayString && it.isCompleted }) }
        val studyEntries = studies.filter { it.date == dayString }
            .map { PlannerEntry("Study", it.topicName.ifBlank { it.subjectName.ifBlank { "Focus session" } },
                if (it.startTimeMs <= 0) "" else Instant.ofEpochMilli(it.startTimeMs).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5),
                it.id, it.isCompleted) }
        val topicEntries = studyTopics.filter { it.dueDate == dayString || it.revisionScheduledDate == dayString }
            .map { PlannerEntry("Study", it.title, "", it.id, it.status == TopicStatus.COMPLETED) }
        return (taskEntries + eventEntries + habitEntries + routineEntries + studyEntries + topicEntries)
            .sortedWith(compareBy<PlannerEntry> { LocalTime.parse(it.time.take(5).ifBlank { "23:59" }.let { t ->
                if (Regex("\\d{2}:\\d{2}").matches(t)) t else "23:59"
            }) }.thenBy { it.completed }.thenBy { it.title })
    }

    fun weeklySummary(
        today: LocalDate, tasks: List<TaskItem>, habits: List<Habit>,
        completions: List<HabitCompletion>, studies: List<StudySessionRecord>,
        records: List<ReminderRecord>
    ): ProductivitySummary {
        val days = (0L..6L).map { today.minusDays(it) }
        val taskOccurrences = days.flatMap { day -> tasks.filter { !it.isArchived && TaskReminderSchedule.isScheduledFor(it, day) }
            .map { TaskReminderSchedule.completedFor(it, day) } }
        val habitOccurrences = days.flatMap { day -> habits.filter { !it.isArchived && !it.isPaused && HabitRoutineRepository.isHabitScheduled(it, day) }
            .map { habit -> completions.any { it.habitId == habit.id && it.date == day.toString() && it.status == "completed" } } }
        val statuses = taskOccurrences + habitOccurrences
        val weekDates = days.mapTo(mutableSetOf()) { it.toString() }
        val overdue = tasks.count { !it.isArchived && !it.isCompleted && !TaskReminderSchedule.repeating(it) &&
            runCatching { LocalDate.parse(it.dueDate).isBefore(today) }.getOrDefault(false) }
        val oldest = today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val unacknowledgedScheduledReminders = days.sumOf { day ->
            tasks.count { task ->
                task.reminderEnabled && !task.isArchived &&
                TaskReminderSchedule.isScheduledFor(task, day) &&
                !TaskReminderSchedule.completedFor(task, day) &&
                runCatching {
                    day.atTime(LocalTime.parse(task.reminderTime)).isBefore(LocalDateTime.now())
                }.getOrDefault(false)
            }
        }
        return ProductivitySummary(statuses.count { it }, statuses.size,
            studies.filter { it.date in weekDates && it.isCompleted }.sumOf { it.focusMinutes }, overdue,
            unacknowledgedScheduledReminders + records.count { it.state == "MISSED" && it.timestampMs >= oldest })
    }
}
