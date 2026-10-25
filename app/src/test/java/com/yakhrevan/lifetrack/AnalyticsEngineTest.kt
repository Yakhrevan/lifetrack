package com.yakhrevan.lifetrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsEngineTest {
    private fun entry(category: String, amount: String, date: String, moneyCategory: String = "Other") =
        LogEntry(category = category, title = "test", amount = amount, date = date, note = "", moneyCategory = moneyCategory)

    @Test fun aggregatesByDateAndCategoryWithinInclusiveRange() {
        val range = AnalyticsEngine.DateRange(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-20"))
        val result = AnalyticsEngine.aggregate(listOf(
            entry("Study", "30", "2026-10-19"),
            entry("Water", "500", "2026-10-19"),
            entry("Study", "45", "2026-10-20"),
            entry("Study", "99", "2026-10-21")
        ), range)
        assertEquals(575.0, result.total, 0.0)
        assertEquals(3, result.count)
        assertEquals(75.0, result.byCategory["Study"]!!, 0.0)
        assertEquals(2, result.byDate.size)
    }

    @Test fun invalidDatesAndNonFiniteAmountsAreIgnored() {
        val range = AnalyticsEngine.DateRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        val result = AnalyticsEngine.aggregate(listOf(
            entry("Study", "10", "not-a-date"),
            entry("Study", "NaN", "2026-10-10"),
            entry("Study", "Infinity", "2026-10-11"),
            entry("Study", "20", "2026-10-12")
        ), range)
        assertEquals(20.0, result.total, 0.0)
        assertEquals(1, result.count)
    }

    @Test fun zeroBaselineHasUndefinedPercentageButRealAbsoluteChange() {
        val comparison = AnalyticsEngine.compare(12.0, 0.0)
        assertEquals(12.0, comparison.absoluteChange, 0.0)
        assertNull(comparison.percentageChange)
        assertEquals("No previous data", AnalyticsEngine.formatPercent(comparison.percentageChange))
    }

    @Test fun previousRangeHasSameLengthAndImmediatelyPrecedesCurrentRange() {
        val range = AnalyticsEngine.DateRange(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25"))
        val previous = range.previous()
        assertEquals(7L, previous.days)
        assertEquals(LocalDate.parse("2026-10-12"), previous.start)
        assertEquals(LocalDate.parse("2026-10-18"), previous.endInclusive)
    }

    @Test fun percentageChangeUsesPreviousPeriodAsBaseline() {
        val comparison = AnalyticsEngine.compare(150.0, 100.0)
        assertEquals(50.0, comparison.absoluteChange, 0.0)
        assertEquals(50.0, comparison.percentageChange!!, 0.0)
    }

    @Test fun goalProgressClampsInvalidTargetsAndCompletedGoals() {
        val progress = AnalyticsEngine.goalProgress(100.0, 130.0)
        assertEquals(0.0, progress.remaining, 0.0)
        assertEquals(1.0, progress.progress, 0.0)
        assertTrue(progress.isComplete)
        val invalid = AnalyticsEngine.goalProgress(Double.NaN, 20.0)
        assertEquals(0.0, invalid.target, 0.0)
        assertEquals(0.0, invalid.progress, 0.0)
    }

    @Test fun customRangeRejectsEndBeforeStart() {
        val result = runCatching {
            AnalyticsEngine.DateRange(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-19"))
        }
        assertTrue(result.isFailure)
    }
}
