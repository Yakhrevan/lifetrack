package com.yakhrevan.lifetrack

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class LifeTrackV12UiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun todayPlannerHasAccessibleNavigationAndQuickAdd() {
        val quickAdd = AtomicBoolean(false)
        rule.setContent { MaterialTheme { TodayPlannerScreen(onBack = {}, onOpen = { _, _ -> },
            onQuickAdd = { quickAdd.set(true) }) } }
        rule.onNodeWithText("Today Planner").assertExists()
        rule.onNodeWithText("Quick Add").performClick()
        assertTrue(quickAdd.get())
    }

    @Test fun notificationCenterShowsStateAndRefreshControl() {
        rule.setContent { MaterialTheme { NotificationInboxScreen(onBack = {}, onOpen = { _, _ -> }) } }
        rule.onNodeWithText("Notification Center").assertExists()
        rule.onNodeWithText("Refresh").performClick()
        rule.onNodeWithText("Upcoming").assertExists()
    }

    @Test fun productivityScreenNavigatesToPlanner() {
        val clicked = AtomicBoolean(false)
        rule.setContent { MaterialTheme { ProductivityInsightsScreen(onBack = {}, onOpenPlanner = { clicked.set(true) }) } }
        rule.onNodeWithText("Productivity Insights").assertExists()
        rule.onNodeWithText("Open Today Planner").performClick()
        assertTrue(clicked.get())
    }
}
