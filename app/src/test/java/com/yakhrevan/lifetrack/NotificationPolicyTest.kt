package com.yakhrevan.lifetrack

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class NotificationPolicyTest {
    @Test fun masterSwitchDisablesEveryModule() {
        NotificationPolicy.Module.entries.forEach { module ->
            assertFalse(NotificationPolicy.enabled(AppSettings(notificationsEnabled = false), module))
        }
    }

    @Test fun moduleSwitchesAreIndependent() {
        val settings = AppSettings(notifyTasks = false, notifyCalendar = true, notifyHabits = false)
        assertFalse(NotificationPolicy.enabled(settings, NotificationPolicy.Module.TASKS))
        assertTrue(NotificationPolicy.enabled(settings, NotificationPolicy.Module.CALENDAR))
        assertFalse(NotificationPolicy.enabled(settings, NotificationPolicy.Module.HABITS))
        assertTrue(NotificationPolicy.enabled(settings, NotificationPolicy.Module.WATER))
    }

    @Test fun quietHoursWorkAcrossMidnight() {
        val settings = AppSettings(quietHoursStart = "22:00", quietHoursEnd = "06:00")
        assertTrue(NotificationPolicy.inQuietHours(settings, LocalTime.of(23, 0)))
        assertTrue(NotificationPolicy.inQuietHours(settings, LocalTime.of(5, 59)))
        assertFalse(NotificationPolicy.inQuietHours(settings, LocalTime.of(6, 0)))
        assertFalse(NotificationPolicy.inQuietHours(settings.copy(quietHoursEnabled = false), LocalTime.of(23, 0)))
    }
}
