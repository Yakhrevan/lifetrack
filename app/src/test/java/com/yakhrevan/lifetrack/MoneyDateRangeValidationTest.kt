package com.yakhrevan.lifetrack

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyDateRangeValidationTest {
    @Test fun acceptsRealIsoCalendarDates() {
        assertTrue(isValidIsoDate("2026-10-20"))
        assertTrue(isValidIsoDate("2024-02-29"))
    }

    @Test fun acceptsOnlyOrderedDateRanges() {
        assertTrue(isValidIsoDateRange("2026-10-01", "2026-10-20"))
        assertTrue(isValidIsoDateRange("2026-10-20", "2026-10-20"))
        assertFalse(isValidIsoDateRange("2026-10-21", "2026-10-20"))
        assertFalse(isValidIsoDateRange("bad", "2026-10-20"))
    }

    @Test fun rejectsMalformedAndImpossibleDates() {
        assertFalse(isValidIsoDate(""))
        assertFalse(isValidIsoDate("20-10-2026"))
        assertFalse(isValidIsoDate("2026-2-01"))
        assertFalse(isValidIsoDate("2026-02-29"))
        assertFalse(isValidIsoDate("2026-13-01"))
        assertFalse(isValidIsoDate("2026-10-32"))
    }
}
