package com.yakhrevan.lifetrack

import org.junit.Assert.*
import org.junit.Test

class MoneyAmountsTest {
    @Test fun acceptsPositiveFiniteAmountsWithinLimit() {
        assertEquals(25000.0, MoneyAmounts.parse("25,000".replace(",", ""))!!, 0.0)
        assertEquals(12.5, MoneyAmounts.parse(" 12.5 ")!!, 0.0)
    }

    @Test fun rejectsInvalidNonPositiveAndUnsupportedAmountsWithoutChangingInput() {
        val values = listOf("", "NaN", "Infinity", "-1", "0", "1,000", "1000000001")
        values.forEach { assertNull("Expected invalid: $it", MoneyAmounts.parse(it)) }
        assertEquals("1,000", values[6 - 1])
    }

    @Test fun sameAmountEntriesRemainIndependentlyCountable() {
        val entries = listOf(
            LogEntry("Income", "Salary", "25000", "2026-08-01", ""),
            LogEntry("Income", "Salary", "25000", "2026-08-01", "")
        )
        assertEquals(50000.0, entries.sumOf { MoneyAmounts.parse(it.amount) ?: 0.0 }, 0.0)
        assertEquals(2, entries.size)
    }
}
