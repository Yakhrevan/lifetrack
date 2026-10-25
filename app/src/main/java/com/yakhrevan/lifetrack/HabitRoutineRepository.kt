package com.yakhrevan.lifetrack

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

// ==========================================
// 1. DATA MODELS
// ==========================================

data class HabitReminder(
    val id: String = UUID.randomUUID().toString(),
    val time: String = "08:00", // HH:mm
    val enabled: Boolean = true,
    val weekdays: Set<Int> = emptySet(), // 1=Mon, 7=Sun; empty means all scheduled days
    val notificationMessage: String = "",
    val snoozeMinutes: Int = 10
)

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val iconName: String = "check",
    val colorHex: String = "#8B5CF6", // Purple
    val category: String = "",
    val targetType: String = "Simple", // "Simple", "Quantity", "Duration"
    val targetAmount: Double = 1.0,
    val unit: String = "times", // e.g. "pages", "L", "min", "times"
    val frequencyType: String = "Daily", // "Daily", "Selected weekdays", "Weekly frequency", "Interval"
    val weekdays: Set<Int> = emptySet(), // 1..7 (1=Mon, 7=Sun)
    val weeklyTarget: Int = 3, // 1..7
    val intervalDays: Int = 1, // Every X days
    val startDate: String = LocalDate.now().toString(), // YYYY-MM-DD
    val endDate: String? = null,
    val reminders: List<HabitReminder> = emptyList(),
    val isPaused: Boolean = false,
    val isArchived: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class HabitCompletion(
    val id: String = UUID.randomUUID().toString(),
    val habitId: String,
    val date: String, // YYYY-MM-DD
    val status: String = "completed", // "completed", "skipped", "missed", "partial"
    val progressValue: Double = 1.0,
    val note: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

data class RoutineStep(
    val id: String = UUID.randomUUID().toString(),
    val routineId: String = "",
    val name: String,
    val description: String = "",
    val iconName: String = "check",
    val colorHex: String = "#38BDF8",
    val durationSeconds: Int = 300, // 5 min
    val orderIndex: Int = 0,
    val reminderEnabled: Boolean = false
)

data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val iconName: String = "sun",
    val colorHex: String = "#EC4899", // Pink
    val category: String = "",
    val repeatType: String = "Daily", // "Daily", "Selected weekdays", "Interval"
    val weekdays: Set<Int> = emptySet(),
    val intervalDays: Int = 1,
    val startDate: String = LocalDate.now().toString(),
    val endDate: String? = null,
    val startTime: String = "07:00", // HH:mm
    val reminderEnabled: Boolean = false,
    val reminderTime: String = "07:00",
    val steps: List<RoutineStep> = emptyList(),
    val isPaused: Boolean = false,
    val isArchived: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class RoutineSession(
    val id: String = UUID.randomUUID().toString(),
    val routineId: String,
    val routineName: String,
    val date: String, // YYYY-MM-DD
    val startedAtMs: Long,
    val completedAtMs: Long,
    val durationSeconds: Int,
    val completedStepIds: List<String>,
    val skippedStepIds: List<String>,
    val totalStepsCount: Int,
    val isCompleted: Boolean
)

data class UserCategory(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#8B5CF6"
)

// ==========================================
// 2. REPOSITORY & ENGINE
// ==========================================

object HabitRoutineRepository {
    private const val PREFS_NAME = "lifetrack_habits_routines_v2"
    private const val KEY_HABITS = "habits_v2"
    private const val KEY_COMPLETIONS = "habit_completions_v2"
    private const val KEY_ROUTINES = "routines_v2"
    private const val KEY_SESSIONS = "routine_sessions_v2"
    private const val KEY_CATEGORIES = "user_categories_v2"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Removes only records created by the old bundled 70-day sample seeder.
     * User-created habits, routines, and history are preserved.
     */
    fun removeLegacyDemoRecords(context: Context) {
        val demoHabitNames = setOf(
            "Drink enough water", "Read 10 pages", "Study / deep work", "Morning stretch",
            "Walk outdoors", "Plan the day", "Limit social media", "Sleep on time"
        )
        val demoRoutineNames = setOf(
            "Morning reset", "Focused work session", "Evening wind-down", "Health break"
        )
        val allHabits = getHabits(context, includeArchived = true)
        val legacyCutoff = LocalDate.now().minusDays(60).toString()
        val removedHabitIds = allHabits.filter { habit ->
            habit.name in demoHabitNames && habit.startDate <= legacyCutoff
        }.map { it.id }.toSet()
        val remainingHabits = allHabits.filterNot { it.id in removedHabitIds }
        val allRoutines = getRoutines(context, includeArchived = true)
        val removedRoutineIds = allRoutines.filter { it.name in demoRoutineNames }.map { it.id }.toSet()
        val remainingRoutines = allRoutines.filterNot { it.id in removedRoutineIds }
        val store = prefs(context)
        val editor = store.edit()
            .putString(KEY_HABITS, encodeHabits(remainingHabits))
            .putString(KEY_ROUTINES, encodeRoutines(remainingRoutines))

        val remainingCompletions = getCompletions(context).filterNot {
            it.habitId in removedHabitIds || it.note.contains("DEMO DATA · synthetic habit", ignoreCase = true)
        }
        editor.putString(KEY_COMPLETIONS, encodeCompletions(remainingCompletions))

        val remainingSessions = getRoutineSessions(context).filterNot {
            it.routineId in removedRoutineIds ||
                (it.routineName in demoRoutineNames && it.startedAtMs < System.currentTimeMillis() - 60L * 86_400_000L)
        }
        editor.putString(KEY_SESSIONS, encodeSessions(remainingSessions)).apply()

        removedHabitIds.forEach { id -> allHabits.firstOrNull { it.id == id }?.let { HabitRoutineNotificationScheduler.cancelHabitReminders(context, it) } }
        removedRoutineIds.forEach { id -> allRoutines.firstOrNull { it.id == id }?.let { HabitRoutineNotificationScheduler.cancelRoutineReminders(context, it) } }
    }

    // ------------------------------------------
    // HABITS CRUD
    // ------------------------------------------

    /**
     * Strictly returns only saved user habits. Never seeds default content.
     */
    fun getHabits(context: Context, includeArchived: Boolean = false): List<Habit> {
        val raw = prefs(context).getString(KEY_HABITS, null)
        val habits = if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            decodeHabits(raw)
        }
        return if (includeArchived) habits else habits.filterNot { it.isArchived }
    }

    fun getHabitById(context: Context, id: String): Habit? {
        return getHabits(context, includeArchived = true).find { it.id == id }
    }

    fun saveHabit(context: Context, habit: Habit) {
        val current = getHabits(context, includeArchived = true).toMutableList()
        val index = current.indexOfFirst { it.id == habit.id }
        if (index >= 0) {
            current[index] = habit
        } else {
            current.add(habit)
        }
        prefs(context).edit().putString(KEY_HABITS, encodeHabits(current)).apply()
        HabitRoutineNotificationScheduler.reconcileHabitReminders(context, habit)
    }

    fun duplicateHabit(context: Context, id: String): Habit? {
        val existing = getHabitById(context, id) ?: return null
        val duplicated = existing.copy(
            id = UUID.randomUUID().toString(),
            name = "${existing.name} (Copy)",
            createdAtMs = System.currentTimeMillis()
        )
        saveHabit(context, duplicated)
        return duplicated
    }

    fun togglePauseHabit(context: Context, id: String): Boolean {
        val habit = getHabitById(context, id) ?: return false
        val updated = habit.copy(isPaused = !habit.isPaused)
        saveHabit(context, updated)
        return updated.isPaused
    }

    fun toggleArchiveHabit(context: Context, id: String): Boolean {
        val habit = getHabitById(context, id) ?: return false
        val updated = habit.copy(isArchived = !habit.isArchived)
        saveHabit(context, updated)
        return updated.isArchived
    }

    fun deleteHabit(context: Context, id: String) {
        val habit = getHabitById(context, id)
        val current = getHabits(context, includeArchived = true).filterNot { it.id == id }
        prefs(context).edit().putString(KEY_HABITS, encodeHabits(current)).apply()
        if (habit != null) {
            HabitRoutineNotificationScheduler.cancelHabitReminders(context, habit)
        }
    }

    // ------------------------------------------
    // HABIT COMPLETIONS & PROGRESS
    // ------------------------------------------

    fun getCompletions(context: Context): List<HabitCompletion> {
        val raw = prefs(context).getString(KEY_COMPLETIONS, null) ?: return emptyList()
        return decodeCompletions(raw)
    }

    fun getCompletionsForDate(context: Context, date: String): List<HabitCompletion> {
        return getCompletions(context).filter { it.date == date }
    }

    fun getCompletion(context: Context, habitId: String, date: String): HabitCompletion? {
        return getCompletions(context).find { it.habitId == habitId && it.date == date }
    }

    fun saveCompletion(context: Context, completion: HabitCompletion) {
        val current = getCompletions(context).toMutableList()
        val index = current.indexOfFirst { it.habitId == completion.habitId && it.date == completion.date }
        if (index >= 0) {
            current[index] = completion
        } else {
            current.add(completion)
        }
        prefs(context).edit().putString(KEY_COMPLETIONS, encodeCompletions(current)).apply()
    }

    fun recordProgress(
        context: Context,
        habitId: String,
        date: String,
        amount: Double,
        targetAmount: Double,
        note: String = ""
    ) {
        val status = if (amount >= targetAmount && targetAmount > 0) "completed" else "partial"
        val completion = HabitCompletion(
            habitId = habitId,
            date = date,
            status = status,
            progressValue = amount,
            note = note,
            timestampMs = System.currentTimeMillis()
        )
        saveCompletion(context, completion)
    }

    fun markHabitComplete(context: Context, habitId: String, date: String) {
        val habit = getHabitById(context, habitId)
        val targetAmount = habit?.targetAmount ?: 1.0
        val completion = HabitCompletion(
            habitId = habitId,
            date = date,
            status = "completed",
            progressValue = targetAmount,
            timestampMs = System.currentTimeMillis()
        )
        saveCompletion(context, completion)
    }

    fun markHabitSkipped(context: Context, habitId: String, date: String) {
        val completion = HabitCompletion(
            habitId = habitId,
            date = date,
            status = "skipped",
            progressValue = 0.0,
            timestampMs = System.currentTimeMillis()
        )
        saveCompletion(context, completion)
    }

    fun undoHabitCompletion(context: Context, habitId: String, date: String) {
        val current = getCompletions(context).filterNot { it.habitId == habitId && it.date == date }
        prefs(context).edit().putString(KEY_COMPLETIONS, encodeCompletions(current)).apply()
    }

    // ------------------------------------------
    // ROUTINES CRUD
    // ------------------------------------------

    /**
     * Strictly returns only saved user routines. Never seeds default content.
     */
    fun getRoutines(context: Context, includeArchived: Boolean = false): List<Routine> {
        val raw = prefs(context).getString(KEY_ROUTINES, null)
        val routines = if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            decodeRoutines(raw)
        }
        return if (includeArchived) routines else routines.filterNot { it.isArchived }
    }

    fun getRoutineById(context: Context, id: String): Routine? {
        return getRoutines(context, includeArchived = true).find { it.id == id }
    }

    fun saveRoutine(context: Context, routine: Routine) {
        val current = getRoutines(context, includeArchived = true).toMutableList()
        val index = current.indexOfFirst { it.id == routine.id }
        if (index >= 0) {
            current[index] = routine
        } else {
            current.add(routine)
        }
        prefs(context).edit().putString(KEY_ROUTINES, encodeRoutines(current)).apply()
        HabitRoutineNotificationScheduler.reconcileRoutineReminders(context, routine)
    }

    fun duplicateRoutine(context: Context, id: String): Routine? {
        val existing = getRoutineById(context, id) ?: return null
        val newRoutineId = UUID.randomUUID().toString()
        val duplicatedSteps = existing.steps.map { step ->
            step.copy(id = UUID.randomUUID().toString(), routineId = newRoutineId)
        }
        val duplicated = existing.copy(
            id = newRoutineId,
            name = "${existing.name} (Copy)",
            steps = duplicatedSteps,
            createdAtMs = System.currentTimeMillis()
        )
        saveRoutine(context, duplicated)
        return duplicated
    }

    fun togglePauseRoutine(context: Context, id: String): Boolean {
        val routine = getRoutineById(context, id) ?: return false
        val updated = routine.copy(isPaused = !routine.isPaused)
        saveRoutine(context, updated)
        return updated.isPaused
    }

    fun toggleArchiveRoutine(context: Context, id: String): Boolean {
        val routine = getRoutineById(context, id) ?: return false
        val updated = routine.copy(isArchived = !routine.isArchived)
        saveRoutine(context, updated)
        return updated.isArchived
    }

    fun deleteRoutine(context: Context, id: String) {
        val routine = getRoutineById(context, id)
        val current = getRoutines(context, includeArchived = true).filterNot { it.id == id }
        prefs(context).edit().putString(KEY_ROUTINES, encodeRoutines(current)).apply()
        if (routine != null) {
            HabitRoutineNotificationScheduler.cancelRoutineReminders(context, routine)
        }
    }

    // ------------------------------------------
    // ROUTINE SESSIONS (EXECUTION HISTORY)
    // ------------------------------------------

    fun getRoutineSessions(context: Context): List<RoutineSession> {
        val raw = prefs(context).getString(KEY_SESSIONS, null) ?: return emptyList()
        return decodeSessions(raw)
    }

    fun recordRoutineSession(context: Context, session: RoutineSession) {
        val current = getRoutineSessions(context).toMutableList()
        current.add(0, session) // newest first
        prefs(context).edit().putString(KEY_SESSIONS, encodeSessions(current)).apply()
    }

    fun getSessionsForRoutine(context: Context, routineId: String): List<RoutineSession> {
        return getRoutineSessions(context).filter { it.routineId == routineId }
    }

    fun getSessionsForDate(context: Context, date: String): List<RoutineSession> {
        return getRoutineSessions(context).filter { it.date == date }
    }

    /**
     * Update the canonical routine session for a date. Calendar checkboxes must
     * not store completion only in a temporary display model.
     */
    fun setRoutineCompletion(context: Context, routineId: String, date: String, completed: Boolean) {
        val routine = getRoutineById(context, routineId) ?: return
        val sessions = getRoutineSessions(context).toMutableList()
        val index = sessions.indexOfFirst { it.routineId == routineId && it.date == date }
        val existing = sessions.getOrNull(index)
        if (!completed && existing == null) return

        val now = System.currentTimeMillis()
        val updated = (existing ?: RoutineSession(
            routineId = routineId,
            routineName = routine.name,
            date = date,
            startedAtMs = now,
            completedAtMs = 0L,
            durationSeconds = 0,
            completedStepIds = emptyList(),
            skippedStepIds = emptyList(),
            totalStepsCount = routine.steps.size,
            isCompleted = false
        )).copy(
            routineName = routine.name,
            completedAtMs = if (completed) now else 0L,
            completedStepIds = if (completed) routine.steps.map { it.id } else emptyList(),
            totalStepsCount = routine.steps.size,
            isCompleted = completed
        )

        if (index >= 0) sessions[index] = updated else sessions.add(0, updated)
        prefs(context).edit().putString(KEY_SESSIONS, encodeSessions(sessions)).apply()
    }

    // ------------------------------------------
    // USER CATEGORIES
    // ------------------------------------------

    fun getCategories(context: Context): List<UserCategory> {
        val raw = prefs(context).getString(KEY_CATEGORIES, null)
        return if (raw.isNullOrBlank()) emptyList() else decodeCategories(raw)
    }

    fun saveCategory(context: Context, category: UserCategory) {
        val current = getCategories(context).toMutableList()
        val index = current.indexOfFirst { it.id == category.id || it.name.equals(category.name, ignoreCase = true) }
        if (index >= 0) {
            current[index] = category
        } else {
            current.add(category)
        }
        prefs(context).edit().putString(KEY_CATEGORIES, encodeCategories(current)).apply()
    }

    fun deleteCategory(context: Context, id: String) {
        val current = getCategories(context).filterNot { it.id == id }
        prefs(context).edit().putString(KEY_CATEGORIES, encodeCategories(current)).apply()
    }

    // ------------------------------------------
    // CALCULATION & SCHEDULE ENGINE
    // ------------------------------------------

    fun isHabitScheduled(habit: Habit, date: LocalDate): Boolean {
        if (habit.isPaused || habit.isArchived) return false
        val start = runCatching { LocalDate.parse(habit.startDate) }.getOrNull()
        if (start != null && date.isBefore(start)) return false
        if (!habit.endDate.isNullOrBlank()) {
            val end = runCatching { LocalDate.parse(habit.endDate) }.getOrNull()
            if (end != null && date.isAfter(end)) return false
        }
        return when (habit.frequencyType) {
            "Daily" -> true
            "Selected weekdays" -> {
                val isoDay = date.dayOfWeek.value // 1=Mon .. 7=Sun
                habit.weekdays.contains(isoDay)
            }
            "Weekly frequency" -> true // Target times per week
            "Interval" -> {
                val base = start ?: LocalDate.now()
                val daysBetween = ChronoUnit.DAYS.between(base, date)
                daysBetween >= 0 && (daysBetween % habit.intervalDays.coerceAtLeast(1).toLong() == 0L)
            }
            else -> true
        }
    }

    fun isRoutineScheduled(routine: Routine, date: LocalDate): Boolean {
        if (routine.isPaused || routine.isArchived) return false
        val start = runCatching { LocalDate.parse(routine.startDate) }.getOrNull()
        if (start != null && date.isBefore(start)) return false
        if (!routine.endDate.isNullOrBlank()) {
            val end = runCatching { LocalDate.parse(routine.endDate) }.getOrNull()
            if (end != null && date.isAfter(end)) return false
        }
        return when (routine.repeatType) {
            "Daily" -> true
            "Selected weekdays" -> routine.weekdays.contains(date.dayOfWeek.value)
            "Interval" -> {
                val base = start ?: LocalDate.now()
                val daysBetween = ChronoUnit.DAYS.between(base, date)
                daysBetween >= 0 && daysBetween % routine.intervalDays.coerceAtLeast(1).toLong() == 0L
            }
            else -> true
        }
    }

    fun getNextRoutineDate(routine: Routine, from: LocalDate = LocalDate.now(), maxDays: Long = 366): LocalDate? {
        return (0L..maxDays).asSequence()
            .map { from.plusDays(it) }
            .firstOrNull { isRoutineScheduled(routine, it) }
    }

    fun getScheduledHabitsForDate(context: Context, date: LocalDate): List<Habit> {
        return getHabits(context).filter { isHabitScheduled(it, date) }
    }

    fun getScheduledRoutinesForDate(context: Context, date: LocalDate): List<Routine> {
        return getRoutines(context).filter { isRoutineScheduled(it, date) }
    }

    /**
     * Calculates current streak and longest streak for a habit from actual completion records.
     * Returns Pair(currentStreak, longestStreak).
     */
    fun calculateHabitStreak(context: Context, habitId: String, today: LocalDate = LocalDate.now()): Pair<Int, Int> {
        val habit = getHabitById(context, habitId) ?: return Pair(0, 0)
        val completions = getCompletions(context)
            .filter { it.habitId == habitId && it.status == "completed" }
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .toSet()

        if (completions.isEmpty()) return Pair(0, 0)

        if (habit.frequencyType == "Weekly frequency") {
            val target = habit.weeklyTarget.coerceIn(1, 7)
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            fun completedInWeek(start: LocalDate): Int =
                (0L..6L).count { start.plusDays(it) in completions }
            var current = 0
            var week = weekStart
            while (completedInWeek(week) >= target) {
                current++
                week = week.minusWeeks(1)
            }
            var longest = 0
            var streak = 0
            var cursor = completions.minOrNull()?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) ?: weekStart
            val lastWeek = completions.maxOrNull()?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) ?: weekStart
            while (!cursor.isAfter(lastWeek)) {
                if (completedInWeek(cursor) >= target) {
                    streak++
                    longest = maxOf(longest, streak)
                } else {
                    streak = 0
                }
                cursor = cursor.plusWeeks(1)
            }
            return Pair(current, maxOf(longest, current))
        }

        // Current streak
        var currentStreak = 0
        var day = if (today in completions || !isHabitScheduled(habit, today)) today else today.minusDays(1)
        while (!day.isBefore(LocalDate.MIN.plusDays(2))) {
            if (isHabitScheduled(habit, day)) {
                if (day in completions) {
                    currentStreak++
                } else {
                    break
                }
            }
            day = day.minusDays(1)
        }

        // Longest streak
        val sortedDates = completions.sorted()
        var longestStreak = 0
        var tempStreak = 0
        var checkDay = sortedDates.first()
        val maxDay = sortedDates.last()

        while (!checkDay.isAfter(maxDay)) {
            if (isHabitScheduled(habit, checkDay)) {
                if (checkDay in completions) {
                    tempStreak++
                    if (tempStreak > longestStreak) longestStreak = tempStreak
                } else {
                    tempStreak = 0
                }
            }
            checkDay = checkDay.plusDays(1)
        }

        return Pair(currentStreak, maxOf(longestStreak, currentStreak))
    }

    /**
     * Calculates completion rate percentage for a habit over a date window [start, end].
     */
    fun calculateHabitCompletionRate(context: Context, habitId: String, start: LocalDate, end: LocalDate): Double {
        val habit = getHabitById(context, habitId) ?: return 0.0
        if (end.isBefore(start)) return 0.0

        val completions = getCompletions(context)
            .filter { it.habitId == habitId && it.status == "completed" }
            .mapNotNull { record -> runCatching { LocalDate.parse(record.date) }.getOrNull() }
            .filter { !it.isBefore(start) && !it.isAfter(end) }
            .toSet()

        if (habit.frequencyType == "Weekly frequency") {
            val weeks = linkedSetOf<LocalDate>()
            var day = start
            while (!day.isAfter(end)) {
                weeks.add(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                day = day.plusDays(1)
            }
            if (weeks.isEmpty()) return 0.0
            val target = habit.weeklyTarget.coerceIn(1, 7)
            val expected = weeks.size * target
            val completed = weeks.sumOf { weekStart ->
                (0L..6L).count { offset ->
                    val date = weekStart.plusDays(offset)
                    date in completions && !date.isBefore(start) && !date.isAfter(end)
                }.coerceAtMost(target)
            }
            return if (expected == 0) 0.0 else completed.toDouble() / expected * 100.0
        }

        var scheduledCount = 0
        var completedCount = 0
        var day = start
        while (!day.isAfter(end)) {
            if (isHabitScheduled(habit, day)) {
                scheduledCount++
                if (day in completions) completedCount++
            }
            day = day.plusDays(1)
        }
        return if (scheduledCount == 0) 0.0 else completedCount.toDouble() / scheduledCount * 100.0
    }

    /**
     * Calculates overall completion rate across all active habits for the specified date range.
     */
    fun calculateOverallHabitsCompletionRate(context: Context, start: LocalDate, end: LocalDate): Double {
        if (end.isBefore(start)) return 0.0
        val habits = getHabits(context)
        if (habits.isEmpty()) return 0.0

        var totalExpected = 0
        var totalCompleted = 0
        val completions = getCompletions(context)
            .filter { it.status == "completed" }
            .associateBy { "${it.habitId}_${it.date}" }

        for (habit in habits) {
            if (habit.frequencyType == "Weekly frequency") {
                val weeks = linkedSetOf<LocalDate>()
                var day = start
                while (!day.isAfter(end)) {
                    weeks.add(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                    day = day.plusDays(1)
                }
                val target = habit.weeklyTarget.coerceIn(1, 7)
                totalExpected += weeks.size * target
                for (weekStart in weeks) {
                    var completedThisWeek = 0
                    for (offset in 0L..6L) {
                        val date = weekStart.plusDays(offset)
                        if (!date.isBefore(start) && !date.isAfter(end) &&
                            completions.containsKey("${habit.id}_$date")) {
                            completedThisWeek++
                        }
                    }
                    totalCompleted += completedThisWeek.coerceAtMost(target)
                }
            } else {
                var day = start
                while (!day.isAfter(end)) {
                    if (isHabitScheduled(habit, day)) {
                        totalExpected++
                        if (completions.containsKey("${habit.id}_$day")) totalCompleted++
                    }
                    day = day.plusDays(1)
                }
            }
        }
        return if (totalExpected == 0) 0.0 else (totalCompleted.toDouble() / totalExpected.toDouble()) * 100.0
    }

    /**
     * Calculates routine completion rate over a date window.
     */
    fun calculateRoutineCompletionRate(context: Context, routineId: String, start: LocalDate, end: LocalDate): Double {
        val routine = getRoutineById(context, routineId) ?: return 0.0
        var scheduledDays = 0
        var day = start
        while (!day.isAfter(end)) {
            if (isRoutineScheduled(routine, day)) {
                scheduledDays++
            }
            day = day.plusDays(1)
        }
        if (scheduledDays == 0) return 0.0

        val sessions = getSessionsForRoutine(context, routineId).filter {
            val sessionDate = runCatching { LocalDate.parse(it.date) }.getOrNull()
            sessionDate != null && !sessionDate.isBefore(start) && !sessionDate.isAfter(end) && it.isCompleted
        }

        return (sessions.size.toDouble() / scheduledDays.toDouble() * 100.0).coerceAtMost(100.0)
    }

    // ------------------------------------------
    // JSON SERIALIZATION / DESERIALIZATION
    // ------------------------------------------

    private fun encodeHabits(habits: List<Habit>): String {
        val arr = JSONArray()
        for (h in habits) {
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("name", h.name)
            obj.put("description", h.description)
            obj.put("iconName", h.iconName)
            obj.put("colorHex", h.colorHex)
            obj.put("category", h.category)
            obj.put("targetType", h.targetType)
            obj.put("targetAmount", h.targetAmount)
            obj.put("unit", h.unit)
            obj.put("frequencyType", h.frequencyType)
            obj.put("weekdays", JSONArray(h.weekdays.sorted()))
            obj.put("weeklyTarget", h.weeklyTarget)
            obj.put("intervalDays", h.intervalDays)
            obj.put("startDate", h.startDate)
            if (h.endDate != null) obj.put("endDate", h.endDate)
            obj.put("isPaused", h.isPaused)
            obj.put("isArchived", h.isArchived)
            obj.put("createdAtMs", h.createdAtMs)

            val remArr = JSONArray()
            for (r in h.reminders) {
                val rObj = JSONObject()
                rObj.put("id", r.id)
                rObj.put("time", r.time)
                rObj.put("enabled", r.enabled)
                rObj.put("weekdays", JSONArray(r.weekdays.sorted()))
                rObj.put("notificationMessage", r.notificationMessage)
                rObj.put("snoozeMinutes", r.snoozeMinutes)
                remArr.put(rObj)
            }
            obj.put("reminders", remArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeHabits(raw: String): List<Habit> = runCatching {
        val arr = JSONArray(raw)
        val list = mutableListOf<Habit>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val weekdaysArr = obj.optJSONArray("weekdays")
            val weekdays = if (weekdaysArr != null) {
                (0 until weekdaysArr.length()).map { weekdaysArr.optInt(it) }.toSet()
            } else emptySet()

            val remArr = obj.optJSONArray("reminders")
            val reminders = mutableListOf<HabitReminder>()
            if (remArr != null) {
                for (j in 0 until remArr.length()) {
                    val rObj = remArr.optJSONObject(j) ?: continue
                    val rDays = rObj.optJSONArray("weekdays")
                    reminders.add(
                        HabitReminder(
                            id = rObj.optString("id", UUID.randomUUID().toString()),
                            time = rObj.optString("time", "08:00"),
                            enabled = rObj.optBoolean("enabled", true),
                            weekdays = if (rDays != null) (0 until rDays.length()).map { rDays.optInt(it) }.toSet() else emptySet(),
                            notificationMessage = rObj.optString("notificationMessage", ""),
                            snoozeMinutes = rObj.optInt("snoozeMinutes", 10)
                        )
                    )
                }
            }

            list.add(
                Habit(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "Habit"),
                    description = obj.optString("description", ""),
                    iconName = obj.optString("iconName", "check"),
                    colorHex = obj.optString("colorHex", "#8B5CF6"),
                    category = obj.optString("category", ""),
                    targetType = obj.optString("targetType", "Simple"),
                    targetAmount = obj.optDouble("targetAmount", 1.0),
                    unit = obj.optString("unit", "times"),
                    frequencyType = obj.optString("frequencyType", "Daily"),
                    weekdays = weekdays,
                    weeklyTarget = obj.optInt("weeklyTarget", 3),
                    intervalDays = obj.optInt("intervalDays", 1),
                    startDate = obj.optString("startDate", LocalDate.now().toString()),
                    endDate = if (obj.has("endDate") && !obj.isNull("endDate")) obj.optString("endDate") else null,
                    reminders = reminders,
                    isPaused = obj.optBoolean("isPaused", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                )
            )
        }
        list
    }.getOrDefault(emptyList())

    private fun encodeCompletions(completions: List<HabitCompletion>): String {
        val arr = JSONArray()
        for (c in completions) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("habitId", c.habitId)
            obj.put("date", c.date)
            obj.put("status", c.status)
            obj.put("progressValue", c.progressValue)
            obj.put("note", c.note)
            obj.put("timestampMs", c.timestampMs)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeCompletions(raw: String): List<HabitCompletion> = runCatching {
        val arr = JSONArray(raw)
        val list = mutableListOf<HabitCompletion>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                HabitCompletion(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    habitId = obj.optString("habitId"),
                    date = obj.optString("date"),
                    status = obj.optString("status", "completed"),
                    progressValue = obj.optDouble("progressValue", 1.0),
                    note = obj.optString("note", ""),
                    timestampMs = obj.optLong("timestampMs", System.currentTimeMillis())
                )
            )
        }
        list
    }.getOrDefault(emptyList())

    private fun encodeRoutines(routines: List<Routine>): String {
        val arr = JSONArray()
        for (r in routines) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("name", r.name)
            obj.put("description", r.description)
            obj.put("iconName", r.iconName)
            obj.put("colorHex", r.colorHex)
            obj.put("category", r.category)
            obj.put("repeatType", r.repeatType)
            obj.put("weekdays", JSONArray(r.weekdays.sorted()))
            obj.put("intervalDays", r.intervalDays)
            obj.put("startDate", r.startDate)
            obj.put("endDate", r.endDate)
            obj.put("startTime", r.startTime)
            obj.put("reminderEnabled", r.reminderEnabled)
            obj.put("reminderTime", r.reminderTime)
            obj.put("isPaused", r.isPaused)
            obj.put("isArchived", r.isArchived)
            obj.put("createdAtMs", r.createdAtMs)

            val stepsArr = JSONArray()
            for (s in r.steps) {
                val sObj = JSONObject()
                sObj.put("id", s.id)
                sObj.put("routineId", s.routineId)
                sObj.put("name", s.name)
                sObj.put("description", s.description)
                sObj.put("iconName", s.iconName)
                sObj.put("colorHex", s.colorHex)
                sObj.put("durationSeconds", s.durationSeconds)
                sObj.put("orderIndex", s.orderIndex)
                sObj.put("reminderEnabled", s.reminderEnabled)
                stepsArr.put(sObj)
            }
            obj.put("steps", stepsArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeRoutines(raw: String): List<Routine> = runCatching {
        val arr = JSONArray(raw)
        val list = mutableListOf<Routine>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val weekdaysArr = obj.optJSONArray("weekdays")
            val weekdays = if (weekdaysArr != null) {
                (0 until weekdaysArr.length()).map { weekdaysArr.optInt(it) }.toSet()
            } else emptySet()

            val stepsArr = obj.optJSONArray("steps")
            val steps = mutableListOf<RoutineStep>()
            if (stepsArr != null) {
                for (j in 0 until stepsArr.length()) {
                    val sObj = stepsArr.optJSONObject(j) ?: continue
                    steps.add(
                        RoutineStep(
                            id = sObj.optString("id", UUID.randomUUID().toString()),
                            routineId = sObj.optString("routineId", obj.optString("id")),
                            name = sObj.optString("name", "Step"),
                            description = sObj.optString("description", ""),
                            iconName = sObj.optString("iconName", "check"),
                            colorHex = sObj.optString("colorHex", "#38BDF8"),
                            durationSeconds = sObj.optInt("durationSeconds", 300),
                            orderIndex = sObj.optInt("orderIndex", j),
                            reminderEnabled = sObj.optBoolean("reminderEnabled", false)
                        )
                    )
                }
            }

            list.add(
                Routine(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "Routine"),
                    description = obj.optString("description", ""),
                    iconName = obj.optString("iconName", "sun"),
                    colorHex = obj.optString("colorHex", "#EC4899"),
                    category = obj.optString("category", ""),
                    repeatType = obj.optString("repeatType", "Daily"),
                    weekdays = weekdays,
                    intervalDays = obj.optInt("intervalDays", 1),
                    startDate = obj.optString("startDate", LocalDate.now().toString()),
                    endDate = obj.optString("endDate", "").takeIf { it.isNotBlank() },
                    startTime = obj.optString("startTime", "07:00"),
                    reminderEnabled = obj.optBoolean("reminderEnabled", false),
                    reminderTime = obj.optString("reminderTime", "07:00"),
                    steps = steps.sortedBy { it.orderIndex },
                    isPaused = obj.optBoolean("isPaused", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                )
            )
        }
        list
    }.getOrDefault(emptyList())

    private fun encodeSessions(sessions: List<RoutineSession>): String {
        val arr = JSONArray()
        for (s in sessions) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("routineId", s.routineId)
            obj.put("routineName", s.routineName)
            obj.put("date", s.date)
            obj.put("startedAtMs", s.startedAtMs)
            obj.put("completedAtMs", s.completedAtMs)
            obj.put("durationSeconds", s.durationSeconds)
            obj.put("completedStepIds", JSONArray(s.completedStepIds))
            obj.put("skippedStepIds", JSONArray(s.skippedStepIds))
            obj.put("totalStepsCount", s.totalStepsCount)
            obj.put("isCompleted", s.isCompleted)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeSessions(raw: String): List<RoutineSession> = runCatching {
        val arr = JSONArray(raw)
        val list = mutableListOf<RoutineSession>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val compArr = obj.optJSONArray("completedStepIds")
            val comp = if (compArr != null) (0 until compArr.length()).map { compArr.optString(it) } else emptyList()
            val skipArr = obj.optJSONArray("skippedStepIds")
            val skip = if (skipArr != null) (0 until skipArr.length()).map { skipArr.optString(it) } else emptyList()

            list.add(
                RoutineSession(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    routineId = obj.optString("routineId"),
                    routineName = obj.optString("routineName"),
                    date = obj.optString("date"),
                    startedAtMs = obj.optLong("startedAtMs"),
                    completedAtMs = obj.optLong("completedAtMs"),
                    durationSeconds = obj.optInt("durationSeconds"),
                    completedStepIds = comp,
                    skippedStepIds = skip,
                    totalStepsCount = obj.optInt("totalStepsCount"),
                    isCompleted = obj.optBoolean("isCompleted", true)
                )
            )
        }
        list
    }.getOrDefault(emptyList())

    private fun encodeCategories(categories: List<UserCategory>): String {
        val arr = JSONArray()
        for (c in categories) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("colorHex", c.colorHex)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeCategories(raw: String): List<UserCategory> = runCatching {
        val arr = JSONArray(raw)
        val list = mutableListOf<UserCategory>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                UserCategory(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name"),
                    colorHex = obj.optString("colorHex", "#8B5CF6")
                )
            )
        }
        list
    }.getOrDefault(emptyList())
}

// ==========================================
// 3. OPTIONAL TEMPLATES (EXPLICIT USER-INVOKED ONLY)
// ==========================================

object HabitRoutineTemplates {
    data class HabitTemplate(
        val name: String,
        val description: String,
        val iconName: String,
        val colorHex: String,
        val category: String,
        val targetType: String,
        val targetAmount: Double,
        val unit: String,
        val frequencyType: String,
        val weeklyTarget: Int
    )

    data class RoutineStepTemplate(
        val name: String,
        val description: String,
        val iconName: String,
        val durationSeconds: Int
    )

    data class RoutineTemplate(
        val name: String,
        val description: String,
        val iconName: String,
        val colorHex: String,
        val category: String,
        val startTime: String,
        val steps: List<RoutineStepTemplate>
    )

    /**
     * Optional templates available in the template catalog.
     * Never automatically saved to database or loaded into lists without user confirmation!
     */
    fun getAvailableHabitTemplates(): List<HabitTemplate> = listOf(
        HabitTemplate("Read Book", "Read at least 20 pages daily", "book", "#A855F7", "Personal", "Quantity", 20.0, "pages", "Daily", 7),
        HabitTemplate("Drink Water", "Maintain daily hydration target", "water", "#06B6D4", "Health", "Quantity", 2.0, "L", "Daily", 7),
        HabitTemplate("Meditate", "Mindfulness and breathwork", "spa", "#10B981", "Mindset", "Duration", 10.0, "min", "Daily", 7),
    )

    fun getAvailableRoutineTemplates(): List<RoutineTemplate> = listOf(
        RoutineTemplate(
            name = "Morning Routine",
            description = "A calm and productive start to the day",
            iconName = "sun",
            colorHex = "#F59E0B",
            category = "Daily",
            startTime = "06:30",
            steps = listOf(
                RoutineStepTemplate("Wake up & hydrate", "Drink a large glass of water", "water", 300),
                RoutineStepTemplate("Morning stretch", "Gentle stretching and mobility", "fitness", 600),
                RoutineStepTemplate("Mindful breathing", "Calm focus before starting work", "spa", 300),
                RoutineStepTemplate("Healthy breakfast", "Nutritious breakfast and coffee", "food", 900)
            )
        ),
        RoutineTemplate(
            name = "Study & Focus Routine",
            description = "Deep focus preparation and review",
            iconName = "school",
            colorHex = "#3B82F6",
            category = "Education",
            startTime = "17:00",
            steps = listOf(
                RoutineStepTemplate("Clear desk & plan", "Review priorities and prepare notes", "check", 300),
                RoutineStepTemplate("Deep focus sprint", "Distraction-free learning block", "school", 1800),
                RoutineStepTemplate("Summary recap", "Write down core takeaways", "book", 600)
            )
        ),
        RoutineTemplate(
            name = "Night Wind-Down",
            description = "Unplug, relax and prepare for restful sleep",
            iconName = "moon",
            colorHex = "#8B5CF6",
            category = "Wellness",
            startTime = "21:30",
            steps = listOf(
                RoutineStepTemplate("Put screens away", "No digital screens before bed", "moon", 600),
                RoutineStepTemplate("Read a chapter", "Light relaxing reading", "book", 900),
                RoutineStepTemplate("Evening reflection", "Quick gratitude check-in", "spa", 300)
            )
        )
    )
}
