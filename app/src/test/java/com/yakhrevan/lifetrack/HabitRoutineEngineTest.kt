package com.yakhrevan.lifetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitRoutineEngineTest {
    @Test fun selectedWeekdaysAreRespected() {
        val habit = HabitRoutineEngine.Habit("h1", "Gym", "Selected weekdays", setOf(DayOfWeek.MONDAY.value, DayOfWeek.WEDNESDAY.value))
        assertTrue(HabitRoutineEngine.isScheduled(habit, LocalDate.parse("2026-10-19")))
        assertFalse(HabitRoutineEngine.isScheduled(habit, LocalDate.parse("2026-10-20")))
    }

    @Test fun completionRateUsesOnlyScheduledDays() {
        val habit = HabitRoutineEngine.Habit("h1", "Study", "Selected weekdays", setOf(1, 2, 3, 4, 5))
        val history = mapOf(
            LocalDate.parse("2026-10-19") to "completed",
            LocalDate.parse("2026-10-20") to "missed",
            LocalDate.parse("2026-10-21") to "completed",
            LocalDate.parse("2026-10-24") to "completed"
        )
        val rate = HabitRoutineEngine.completionRate(habit, history, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25"))
        assertEquals(40.0, rate, 0.0)
    }

    @Test fun weeklyFrequencyUsesWeeklyTargetForCompletionRate() {
        val habit = HabitRoutineEngine.Habit("h1", "Read", "Weekly frequency", weeklyTarget = 3)
        val history = mapOf(
            LocalDate.parse("2026-10-19") to "completed",
            LocalDate.parse("2026-10-20") to "completed",
            LocalDate.parse("2026-10-21") to "completed"
        )
        val rate = HabitRoutineEngine.completionRate(habit, history, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25"))
        assertEquals(100.0, rate, 0.0)
    }

    @Test fun longestStreakCountsScheduledCompletionsOnly() {
        val habit = HabitRoutineEngine.Habit("h1", "Run", "Selected weekdays", setOf(1, 2, 3, 4, 5))
        val completed = setOf(
            LocalDate.parse("2026-10-19"),
            LocalDate.parse("2026-10-20"),
            LocalDate.parse("2026-10-21"),
            LocalDate.parse("2026-10-23")
        )
        assertEquals(3, HabitRoutineEngine.longestStreak(habit, completed))
    }

    @Test fun emptyHistoryHasZeroCompletionAndStreak() {
        val habit = HabitRoutineEngine.Habit("h1", "Meditate", "Daily")
        assertEquals(0.0, HabitRoutineEngine.completionRate(habit, emptyMap(), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07")), 0.0)
        assertEquals(0, HabitRoutineEngine.longestStreak(habit, emptySet()))
    }
}
