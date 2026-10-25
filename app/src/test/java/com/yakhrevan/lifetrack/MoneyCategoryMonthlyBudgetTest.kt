package com.yakhrevan.lifetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class MoneyCategoryMonthlyBudgetTest {
    private fun resetBudgetState() {
        MoneyCategory.monthlyCategoryBudgets.clear()
        MoneyCategory.categoryBudgets.clear()
    }

    @Test
    fun budgetsAreIsolatedByMonthAndCategory() {
        resetBudgetState()
        try {
            MoneyCategory.monthlyCategoryBudgets["2026-10::Food"] = 5000.0
            MoneyCategory.monthlyCategoryBudgets["2026-11::Food"] = 3000.0
            MoneyCategory.monthlyCategoryBudgets["2026-10::Transport"] = 1500.0

            assertEquals(5000.0, MoneyCategory.categoryBudgetForMonth("Food", "2026-10")!!, 0.0)
            assertEquals(3000.0, MoneyCategory.categoryBudgetForMonth("Food", "2026-11")!!, 0.0)
            assertEquals(1500.0, MoneyCategory.categoryBudgetForMonth("Transport", "2026-10")!!, 0.0)
            assertNull(MoneyCategory.categoryBudgetForMonth("Food", "2026-12"))
        } finally {
            resetBudgetState()
        }
    }

    @Test
    fun monthListingContainsOnlyBudgetsForThatMonth() {
        resetBudgetState()
        try {
            MoneyCategory.monthlyCategoryBudgets["2026-10::Food"] = 5000.0
            MoneyCategory.monthlyCategoryBudgets["2026-11::Food"] = 3000.0
            MoneyCategory.monthlyCategoryBudgets["2026-10::Travel"] = 1200.0

            assertEquals(mapOf("Food" to 5000.0, "Travel" to 1200.0), MoneyCategory.categoryBudgetsForMonth("2026-10"))
            assertEquals(mapOf("Food" to 3000.0), MoneyCategory.categoryBudgetsForMonth("2026-11"))
            assertEquals(mapOf("2026-10" to 5000.0, "2026-11" to 3000.0), MoneyCategory.categoryBudgetHistory("Food"))
        } finally {
            resetBudgetState()
        }
    }

    @Test
    fun legacyBudgetIsUsedOnlyAsCurrentMonthFallback() {
        resetBudgetState()
        try {
            val currentMonth = YearMonth.now().toString()
            MoneyCategory.categoryBudgets["Food"] = 2200.0

            assertEquals(2200.0, MoneyCategory.categoryBudgetForMonth("Food", currentMonth)!!, 0.0)
            assertEquals(mapOf("Food" to 2200.0), MoneyCategory.categoryBudgetsForMonth(currentMonth))
            assertNull(MoneyCategory.categoryBudgetForMonth("Food", "2026-01"))
        } finally {
            resetBudgetState()
        }
    }
}
