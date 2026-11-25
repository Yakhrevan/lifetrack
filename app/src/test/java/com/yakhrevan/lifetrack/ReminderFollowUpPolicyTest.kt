package com.yakhrevan.lifetrack

import org.junit.Assert.*
import org.junit.Test

class ReminderFollowUpPolicyTest {
    private val settings = AppSettings(taskFollowUpEnabled = true, taskFollowUpCount = 2)

    @Test fun strictCapsAndMasterSwitch() {
        val task = TaskItem(title = "Important", priority = "High", reminderEnabled = true, reminderTime = "08:30")
        assertTrue(ReminderFollowUpPolicy.shouldSchedule(settings, task, 1))
        assertTrue(ReminderFollowUpPolicy.shouldSchedule(settings, task, 2))
        assertFalse(ReminderFollowUpPolicy.shouldSchedule(settings, task, 3))
        assertFalse(ReminderFollowUpPolicy.shouldSchedule(settings.copy(notificationsEnabled = false), task, 1))
        assertFalse(ReminderFollowUpPolicy.shouldSchedule(settings.copy(notifyTasks = false), task, 1))
    }

    @Test fun completedOrLowPriorityTasksNeverEscalateByDefault() {
        val task = TaskItem(title = "Low", priority = "Low", reminderEnabled = true)
        assertFalse(ReminderFollowUpPolicy.shouldSchedule(settings, task, 1))
        assertTrue(ReminderFollowUpPolicy.shouldSchedule(settings.copy(taskFollowUpHighPriorityOnly = false), task, 1))
        assertFalse(ReminderFollowUpPolicy.shouldSchedule(settings.copy(taskFollowUpHighPriorityOnly = false), task.copy(isCompleted = true), 1))
    }

    @Test fun delaysGrowAndStayBounded() {
        assertEquals(15L, ReminderFollowUpPolicy.delayMinutes(15, 1))
        assertEquals(30L, ReminderFollowUpPolicy.delayMinutes(15, 2))
        assertEquals(360L, ReminderFollowUpPolicy.delayMinutes(1000, 7))
    }
}
