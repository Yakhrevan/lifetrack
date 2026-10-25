package com.yakhrevan.lifetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryValidatorTest {
    @Test fun rejectsBlankAndNonNumericAmounts() {
        assertEquals("Enter a valid amount greater than zero.", EntryValidator.amountError("Expense", ""))
        assertEquals("Enter a valid amount greater than zero.", EntryValidator.amountError("Income", "abc"))
    }

    @Test fun rejectsZeroNegativeAndNonFiniteAmounts() {
        assertEquals("Enter a valid amount greater than zero.", EntryValidator.amountError("Expense", "0"))
        assertEquals("Enter a valid amount greater than zero.", EntryValidator.amountError("Expense", "-12"))
        assertEquals("Enter a valid amount greater than zero.", EntryValidator.amountError("Expense", "NaN"))
        assertEquals("Enter a valid amount greater than zero.", EntryValidator.amountError("Expense", "Infinity"))
    }

    @Test fun wholeNumberCategoriesRejectDecimals() {
        assertEquals("Enter a whole number within a reasonable range.", EntryValidator.amountError("Water", "250.5"))
        assertEquals("Enter a whole number within a reasonable range.", EntryValidator.amountError("Study", "25.5"))
        assertNull(EntryValidator.amountError("Water", "250"))
    }

    @Test fun waterAndDurationLimitsAreEnforced() {
        assertEquals("Water entry must be 10,000 ml or less.", EntryValidator.amountError("Water", "10001"))
        assertEquals("Duration must be 1–1440 minutes.", EntryValidator.amountError("Study", "1441"))
        assertNull(EntryValidator.amountError("Art", "1440"))
    }

    @Test fun moneyAmountsHaveAnUpperBound() {
        assertEquals("Amount is too large. Check the value and try again.", EntryValidator.amountError("Expense", "1000000001"))
        assertNull(EntryValidator.amountError("Income", "25000"))
    }

    @Test fun normalizesWhitespaceWithoutChangingValue() {
        assertEquals("125.50", EntryValidator.normalizeAmount(" 125.50 "))
    }
}