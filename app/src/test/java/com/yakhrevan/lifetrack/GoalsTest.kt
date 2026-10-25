package com.yakhrevan.lifetrack

import org.junit.Assert.*
import org.junit.Test

class GoalsTest {
    @Test fun parsesWaterWithinRange() {
        assertEquals(2500, Goals.parseWater(" 2500 "))
        assertNull(Goals.parseWater("100"))
        assertNull(Goals.parseWater("abc"))
        assertNull(Goals.parseWater("10001"))
    }

    @Test fun parsesStudyWithinRange() {
        assertEquals(240, Goals.parseStudy("240"))
        assertNull(Goals.parseStudy("5"))
        assertNull(Goals.parseStudy("1441"))
    }

    @Test fun storedGoalValuesAreClampedToSupportedRanges() {
        assertEquals(Goals.DEFAULT_WATER_ML, Goals.DEFAULT_WATER_ML.coerceIn(500, 10000))
        assertEquals(500, (-1).coerceIn(500, 10000))
        assertEquals(10000, 20000.coerceIn(500, 10000))
        assertEquals(15, 0.coerceIn(15, 1440))
        assertEquals(1440, 5000.coerceIn(15, 1440))
    }

    @Test fun safeMoneyAmountsRejectMalformedAndOutOfRangeLegacyValues() {
        assertEquals(125.5, MoneyAmounts.value(" 125.50 "), 0.0001)
        assertEquals(0.0, MoneyAmounts.value("NaN"), 0.0)
        assertEquals(0.0, MoneyAmounts.value("Infinity"), 0.0)
        assertEquals(0.0, MoneyAmounts.value("-12"), 0.0)
        assertEquals(0.0, MoneyAmounts.value("abc"), 0.0)
        assertEquals(0.0, MoneyAmounts.value("1000000001"), 0.0)
    }

    @Test fun formatsGoals() {
        assertEquals("2.5 L", Goals.litres(2500))
        assertEquals("4h", Goals.hours(240))
        assertEquals("1h 30m", Goals.hours(90))
        assertEquals("45m", Goals.hours(45))
    }
}