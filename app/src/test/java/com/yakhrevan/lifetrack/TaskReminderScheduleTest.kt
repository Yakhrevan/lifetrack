package com.yakhrevan.lifetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TaskReminderScheduleTest {
    private fun task(repeat: String = "Does not repeat", date: String = "2026-11-23") = TaskItem(
        title = "Daily review", dueDate = date, repeatType = repeat,
        reminderEnabled = true, reminderTime = "09:00"
    )

    @Test fun dailyRepeatsOnNextDate() {
        assertEquals(LocalDateTime.parse("2026-11-24T09:00"),
            TaskReminderSchedule.nextOccurrence(task("Daily"), LocalDateTime.parse("2026-11-23T09:01")))
    }

    @Test fun weeklySelectedDaysSkipOffDays() {
        val schedule = task("Weekly").copy(reminderDays = setOf(1, 3, 5))
        assertEquals(LocalDateTime.parse("2026-11-25T09:00"),
            TaskReminderSchedule.nextOccurrence(schedule, LocalDateTime.parse("2026-11-23T10:00")))
    }

    @Test fun monthlyEndOfMonthDoesNotDrift() {
        val schedule = task("Monthly", "2026-01-31")
        assertTrue(TaskReminderSchedule.isScheduledFor(schedule, LocalDate.parse("2026-02-28")))
        assertTrue(TaskReminderSchedule.isScheduledFor(schedule, LocalDate.parse("2026-03-31")))
        assertFalse(TaskReminderSchedule.isScheduledFor(schedule, LocalDate.parse("2026-03-28")))
    }

    @Test fun monthlyReminderContinuesBeyondTenYears() {
        val schedule = task("Monthly", "2015-01-31")
        assertTrue(TaskReminderSchedule.isScheduledFor(schedule, LocalDate.parse("2030-02-28")))
        assertFalse(TaskReminderSchedule.isScheduledFor(schedule, LocalDate.parse("2030-02-27")))
    }

    @Test fun completionOnlySkipsItsOwnDayForRecurringTask() {
        val finishedOn = LocalDate.parse("2026-11-23").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val schedule = task("Daily").copy(isCompleted = true, completedAtMs = finishedOn)
        assertTrue(TaskReminderSchedule.completedFor(schedule, LocalDate.parse("2026-11-23")))
        assertFalse(TaskReminderSchedule.completedFor(schedule, LocalDate.parse("2026-11-24")))
        assertEquals(LocalDateTime.parse("2026-11-24T09:00"),
            TaskReminderSchedule.nextOccurrence(schedule, LocalDateTime.parse("2026-11-23T08:00")))
    }

    @Test fun completedOneOffTaskDoesNotScheduleAgain() {
        assertNull(TaskReminderSchedule.nextOccurrence(task().copy(isCompleted = true),
            LocalDateTime.parse("2026-11-22T09:00")))
    }
}
