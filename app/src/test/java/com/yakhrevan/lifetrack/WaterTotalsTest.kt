package com.yakhrevan.lifetrack

import org.junit.Assert.*
import org.junit.Test

class WaterTotalsTest {
    private fun w(amount: String, date: String, id: String, cat: String = "Water") =
        LogEntry(cat, "Water", amount, date, "", id)

    @Test fun dayTotalSumsOnlyWaterForThatDay() {
        val logs = listOf(
            w("250", "2026-10-19", "1"), w("500", "2026-10-19", "2"),
            w("700", "2026-10-18", "3"), w("300", "2026-10-19", "4", "Meal")
        )
        assertEquals(750, WaterTotals.dayTotal(logs, "2026-10-19"))
        assertEquals(700, WaterTotals.dayTotal(logs, "2026-10-18"))
        assertEquals(0, WaterTotals.dayTotal(logs, "2026-10-17"))
    }

    @Test fun invalidAndNegativeAmountsCountAsZero() {
        val logs = listOf(w("abc", "2026-10-19", "1"), w("-50", "2026-10-19", "2"), w(" 200 ", "2026-10-19", "3"))
        assertEquals(200, WaterTotals.dayTotal(logs, "2026-10-19"))
    }

    @Test fun historyIsOldestFirstAndZeroFilled() {
        val logs = listOf(w("400", "2026-10-19", "1"), w("600", "2026-10-17", "2"))
        val h = WaterTotals.history(logs, "2026-10-19", 4)
        assertEquals(listOf("2026-10-16", "2026-10-17", "2026-10-18", "2026-10-19"), h.map { it.first })
        assertEquals(listOf(0, 600, 0, 400), h.map { it.second })
    }

    @Test fun historyCrossesMonthBoundary() {
        val h = WaterTotals.history(emptyList(), "2026-03-02", 3)
        assertEquals(listOf("2026-02-28", "2026-03-01", "2026-03-02"), h.map { it.first })
    }

    @Test fun badTodayGivesEmptyHistory() {
        assertTrue(WaterTotals.history(emptyList(), "not-a-date").isEmpty())
    }
}