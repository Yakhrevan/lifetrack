package com.yakhrevan.lifetrack

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal object HabitRoutineEngine {
    data class Habit(
        val id: String,
        val name: String,
        val frequency: String,
        val weekdays: Set<Int> = emptySet(),
        val weeklyTarget: Int = 3
    )

    fun isScheduled(habit: Habit, date: LocalDate): Boolean = when (habit.frequency) {
        "Daily" -> true
        "Selected weekdays" -> date.dayOfWeek.value in habit.weekdays
        "Weekly frequency" -> true
        else -> false
    }

    fun scheduledDates(habit: Habit, start: LocalDate, end: LocalDate): List<LocalDate> {
        if (end.isBefore(start)) return emptyList()
        return (0..(java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()))
            .map { start.plusDays(it.toLong()) }
            .filter { isScheduled(habit, it) }
    }

    fun completionRate(habit: Habit, history: Map<LocalDate, String>, start: LocalDate, end: LocalDate): Double {
        if (end.isBefore(start)) return 0.0
        if (habit.frequency == "Weekly frequency") {
            val target = habit.weeklyTarget.coerceIn(1, 7)
            var week = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            var successful = 0
            var totalWeeks = 0
            while (!week.isAfter(end)) {
                val candidateWeekEnd = week.plusDays(6)
                val weekEnd = if (candidateWeekEnd.isBefore(end)) candidateWeekEnd else end
                val completed = (0..6).map { week.plusDays(it.toLong()) }
                    .filter { !it.isBefore(start) && !it.isAfter(weekEnd) && history[it] == "completed" }
                    .size
                val candidateStart = if (start.isAfter(week)) start else week
                val expected = minOf(target, (java.time.temporal.ChronoUnit.DAYS.between(candidateStart, weekEnd) + 1).toInt())
                if (expected > 0) {
                    totalWeeks++
                    if (completed >= expected) successful++
                }
                week = week.plusWeeks(1)
            }
            return if (totalWeeks == 0) 0.0 else successful.toDouble() / totalWeeks * 100.0
        }
        val days = scheduledDates(habit, start, end)
        if (days.isEmpty()) return 0.0
        val completed = days.count { history[it] == "completed" }
        return completed.toDouble() / days.size * 100.0
    }

    fun streak(habit: Habit, completedDates: Set<LocalDate>, today: LocalDate): Int {
        if (habit.frequency == "Weekly frequency") {
            val target = habit.weeklyTarget.coerceIn(1, 7)
            var weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            var count = 0
            while (true) {
                val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }.filter { !it.isAfter(today) }
                if (weekDays.count { it in completedDates } >= target) {
                    count++
                    weekStart = weekStart.minusWeeks(1)
                } else break
            }
            return count
        }
        var day = if (today in completedDates || !isScheduled(habit, today)) today else today.minusDays(1)
        var count = 0
        while (!day.isBefore(LocalDate.MIN.plusDays(2))) {
            if (isScheduled(habit, day)) {
                if (day in completedDates) count++ else break
            }
            day = day.minusDays(1)
        }
        return count
    }

    fun longestStreak(habit: Habit, completedDates: Set<LocalDate>): Int {
        if (completedDates.isEmpty()) return 0
        var day = completedDates.minOrNull()!!
        val end = completedDates.maxOrNull()!!
        var current = 0
        var best = 0
        while (!day.isAfter(end)) {
            if (isScheduled(habit, day)) {
                if (day in completedDates) current++ else current = 0
                best = maxOf(best, current)
            }
            day = day.plusDays(1)
        }
        return best
    }

    fun decodeHabits(raw: String?): List<Habit> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val days = item.optJSONArray("days")
            Habit(
                id = item.optString("id"),
                name = item.optString("name"),
                frequency = item.optString("frequency", "Daily"),
                weekdays = if (days == null) emptySet() else (0 until days.length()).map { days.optInt(it) }.toSet(),
                weeklyTarget = item.optInt("weeklyTarget", 3).coerceIn(1, 7)
            )
        }
    }.getOrDefault(emptyList())

    fun encodeHabit(habit: Habit, reminderEnabled: Boolean, reminderTime: String): JSONObject =
        JSONObject().put("id", habit.id).put("name", habit.name).put("frequency", habit.frequency)
            .put("days", JSONArray(habit.weekdays.sorted())).put("weeklyTarget", habit.weeklyTarget)
            .put("reminderEnabled", reminderEnabled).put("reminderTime", reminderTime)
}
