package com.yakhrevan.lifetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CalendarEventNotificationSchedulerTest {

    private fun event(repeat: RepeatOption) = CalendarItem(
        id = "event-1",
        title = "Appointment",
        type = ActivityType.EVENT,
        date = "2026-01-31",
        startTime = "09:30 AM",
        reminder = "10 minutes before",
        repeatOption = repeat
    )

    @Test
    fun parsesTwelveHourAndTwentyFourHourTimes() {
        assertEquals(LocalTime.of(9, 30), CalendarEventNotificationScheduler.parseTime("09:30 AM"))
        assertEquals(LocalTime.of(21, 15), CalendarEventNotificationScheduler.parseTime("9:15 PM"))
        assertEquals(LocalTime.of(14, 5), CalendarEventNotificationScheduler.parseTime("14:05"))
        assertNull(CalendarEventNotificationScheduler.parseTime("not-a-time"))
    }

    @Test
    fun parsesReminderLeadTimeUnits() {
        assertEquals(10L, CalendarEventNotificationScheduler.parseLeadMinutes("10 minutes before"))
        assertEquals(120L, CalendarEventNotificationScheduler.parseLeadMinutes("2 hours before"))
        assertEquals(1_440L, CalendarEventNotificationScheduler.parseLeadMinutes("1 day before"))
        assertNull(CalendarEventNotificationScheduler.parseLeadMinutes("No reminder"))
    }

    @Test
    fun dailyEventFindsNextDateAfterTheCurrentOccurrence() {
        val next = CalendarEventNotificationScheduler.nextOccurrence(
            event(RepeatOption.DAILY),
            LocalDate.parse("2026-01-31"),
            LocalTime.of(9, 30),
            LocalDateTime.parse("2026-02-02T10:00:00")
        )
        assertEquals(LocalDateTime.parse("2026-02-03T09:30:00"), next)
    }

    @Test
    fun monthlyEventKeepsOriginalDayInsteadOfDriftingAfterFebruary() {
        val next = CalendarEventNotificationScheduler.nextOccurrence(
            event(RepeatOption.MONTHLY),
            LocalDate.parse("2026-01-31"),
            LocalTime.of(9, 30),
            LocalDateTime.parse("2026-03-01T00:00:00")
        )
        assertEquals(LocalDateTime.parse("2026-03-31T09:30:00"), next)
    }

    @Test
    fun nonRepeatingEventDoesNotCreateAnotherOccurrence() {
        val next = CalendarEventNotificationScheduler.nextOccurrence(
            event(RepeatOption.NONE),
            LocalDate.parse("2026-01-31"),
            LocalTime.of(9, 30),
            LocalDateTime.parse("2026-02-01T00:00:00")
        )
        assertNull(next)
    }
}
