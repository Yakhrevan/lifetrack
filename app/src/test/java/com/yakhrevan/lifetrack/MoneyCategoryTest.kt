package com.yakhrevan.lifetrack

import org.junit.Assert.*
import org.junit.Test

class MoneyCategoryTest {
    @Test fun rejectsBlankLongAndUnsafeNames() {
        assertNotNull(MoneyCategory.nameError("  ", emptyList()))
        assertNotNull(MoneyCategory.nameError("x".repeat(31), emptyList()))
        assertNotNull(MoneyCategory.nameError("a,b", emptyList()))
    }

    @Test fun rejectsCaseInsensitiveDuplicates() {
        assertNotNull(MoneyCategory.nameError("transport", MoneyCategory.expenses))
        assertNotNull(MoneyCategory.nameError("Pets", MoneyCategory.expenses))
        assertNull(MoneyCategory.nameError("Pets Plus", MoneyCategory.expenses))
    }

    @Test fun normalizeStillMapsKnownWords() {
        assertEquals("Transport", MoneyCategory.normalize("Petrol for bike"))
        assertEquals("Other", MoneyCategory.normalize("random"))
    }
}