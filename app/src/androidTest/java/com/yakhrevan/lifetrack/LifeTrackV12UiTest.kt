package com.yakhrevan.lifetrack

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class LifeTrackV12UiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun todayPlannerHasAccessibleNavigationAndQuickAdd() {
        val quickAdd = AtomicBoolean(false)
        rule.setContent { MaterialTheme { TodayPlannerScreen(onBack = {}, onOpen = { _, _ -> },
            onQuickAdd = { quickAdd.set(true) }) } }
        rule.onNodeWithText("Today Planner").assertExists()
        rule.onNodeWithContentDescription("Back to previous screen").assertExists()
        rule.onNodeWithText("Quick Add").performClick()
        assertTrue(quickAdd.get())
    }

    @Test fun notificationCenterShowsStateAndRefreshControl() {
        rule.setContent { MaterialTheme { NotificationInboxScreen(onBack = {}, onOpen = { _, _ -> }) } }
        rule.onNodeWithText("Notification Center").assertExists()
        rule.onNodeWithText("Refresh").performClick()
        rule.onNodeWithText("Upcoming").assertExists()
    }

    @Test fun snoozedNotificationOpensItsActualTask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("lifetrack_notification_history_v1", Context.MODE_PRIVATE)
        val previous = prefs.getString("reminders", null)
        val opened = AtomicReference<Pair<String, String>?>(null)
        try {
            NotificationHistoryRepository.record(context, "Tasks", "phase4a-ui-snooze",
                "Phase 4A snoozed task", "SNOOZED")
            rule.setContent { MaterialTheme { NotificationInboxScreen(onBack = {},
                onOpen = { module, id -> opened.set(module to id) }) } }

            rule.onNodeWithText("Snoozed").performClick()
            rule.onNodeWithText("Phase 4A snoozed task").performClick()
            assertEquals("Tasks" to "phase4a-ui-snooze", opened.get())
        } finally {
            val editor = prefs.edit()
            if (previous == null) editor.remove("reminders") else editor.putString("reminders", previous)
            assertTrue(editor.commit())
        }
    }

    @Test fun productivityScreenNavigatesToPlanner() {
        val clicked = AtomicBoolean(false)
        rule.setContent { MaterialTheme { ProductivityInsightsScreen(onBack = {}, onOpenPlanner = { clicked.set(true) }) } }
        rule.onNodeWithText("Productivity Insights").assertExists()
        rule.onNodeWithText("Open Today Planner").performClick()
        assertTrue(clicked.get())
    }
}
