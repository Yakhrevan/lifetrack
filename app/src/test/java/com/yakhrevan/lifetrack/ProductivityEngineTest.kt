package com.yakhrevan.lifetrack

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ProductivityEngineTest {
    @Test fun repeatingCalendarEventsRespectAnchorDates() {
        val first = LocalDate.of(2026, 1, 31)
        val monthly = CalendarItem(title = "Rent", type = ActivityType.EVENT, date = first.toString(),
            repeatOption = RepeatOption.MONTHLY)
        assertTrue(ProductivityEngine.calendarOccurs(monthly, first))
        assertTrue(ProductivityEngine.calendarOccurs(monthly, LocalDate.of(2026, 2, 28)))
        assertTrue(ProductivityEngine.calendarOccurs(monthly, LocalDate.of(2026, 3, 31)))
        assertFalse(ProductivityEngine.calendarOccurs(monthly, LocalDate.of(2026, 3, 28)))
    }

    @Test fun plannerIncludesAllFiveRealModuleSourcesAndSortsByClock() {
        val date = LocalDate.of(2026, 11, 26)
        val tasks = listOf(TaskItem(title = "Fix bot", dueDate = date.toString(), dueTime = "09:00"))
        val events = listOf(CalendarItem(title = "Standup", type = ActivityType.EVENT,
            date = date.toString(), startTime = "08:30"))
        val habits = listOf(Habit(name = "Walk", startDate = date.minusDays(1).toString()))
        val routines = listOf(Routine(name = "Morning", startDate = date.minusDays(1).toString(), startTime = "07:00"))
        val studies = listOf(StudySessionRecord(topicName = "ROS 2", date = date.toString()))
        val entries = ProductivityEngine.entries(date, tasks, events, habits, emptyList(), routines, emptyList(), studies)
        assertEquals(setOf("Tasks", "Calendar", "Habits", "Routines", "Study"), entries.map { it.kind }.toSet())
        assertTrue(entries.indexOfFirst { it.title == "Standup" } < entries.indexOfFirst { it.title == "Fix bot" })
    }

    @Test fun weeklySummaryDoesNotInventProgress() {
        val today = LocalDate.of(2026, 11, 26)
        val empty = ProductivityEngine.weeklySummary(today, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(0, empty.planned)
        assertEquals(0, empty.completionPercent)
        val task = TaskItem(title = "Done", dueDate = today.toString(), isCompleted = true)
        val actual = ProductivityEngine.weeklySummary(today, listOf(task), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(1, actual.planned)
        assertEquals(100, actual.completionPercent)
    }
}
