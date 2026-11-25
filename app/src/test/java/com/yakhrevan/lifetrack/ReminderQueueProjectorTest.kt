package com.yakhrevan.lifetrack

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ReminderQueueProjectorTest {
    @Test fun allFourUpcomingSourcesAppearAndPastIsMarkedMissed() {
        val now = LocalDateTime.of(2026, 11, 26, 10, 0)
        val date = now.toLocalDate().toString()
        val records = ReminderQueueProjector.project(now,
            tasks = listOf(TaskItem(title = "Late task", dueDate = date, reminderEnabled = true, reminderTime = "09:00")),
            events = listOf(CalendarItem(title = "Meeting", type = ActivityType.EVENT,
                date = date, startTime = "12:00", reminder = "10 minutes before")),
            habits = listOf(Habit(name = "Read", startDate = date,
                reminders = listOf(HabitReminder(time = "11:00")))),
            routines = listOf(Routine(name = "Morning", startDate = date,
                reminderEnabled = true, reminderTime = "08:30")),
            completions = emptyList(), sessions = emptyList())
        assertEquals(setOf("Tasks", "Calendar", "Habits", "Routines"), records.map { it.module }.toSet())
        assertEquals("MISSED", records.first { it.title == "Late task" }.state)
        assertEquals("UPCOMING", records.first { it.title == "Meeting" }.state)
    }
}
