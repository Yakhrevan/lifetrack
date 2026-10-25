package com.yakhrevan.lifetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SampleData70Test {
    @Test fun createsExactlySeventyDaysIncludingToday() {
        val today = LocalDate.parse("2026-10-21")
        val rows = SampleData70.create(today)
        assertEquals(70, rows.map { it.date }.distinct().size)
        assertEquals("2026-08-13", rows.minOf { it.date })
        assertEquals("2026-10-21", rows.maxOf { it.date })
        assertTrue(rows.all { it.id.startsWith("sample70-") })
    }

    @Test fun sampleRemovalPreservesPersonalEntries() {
        val personal = LogEntry("Study", "My own session", "30", "2026-10-21", "personal", id = "my-entry")
        val sample = SampleData70.create(LocalDate.parse("2026-10-21"))
        val remaining = SampleData70.remove(listOf(personal) + sample)
        assertEquals(listOf(personal), remaining)
        assertFalse(SampleData70.contains(remaining))
    }

    @Test fun generatesIncomeAndExpenseForMoneyCharts() {
        val rows = SampleData70.create(LocalDate.parse("2026-10-21"))
        assertTrue(rows.any { it.category == "Income" })
        assertTrue(rows.any { it.category == "Expense" })
        assertTrue(rows.filter { it.category == "Expense" }.map { it.moneyCategory }.distinct().size > 1)
    }
}
