package com.yakhrevan.lifetrack

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Shared, deterministic aggregation helpers for all LifeTrack analytics surfaces.
 * Invalid dates and non-finite numeric amounts are excluded rather than silently
 * being assigned to an arbitrary period. Empty periods remain zero-valued but
 * comparisons distinguish "no baseline" from a genuine 0% change.
 */
internal object AnalyticsEngine {
    enum class Period { DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

    data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
        init { require(!endInclusive.isBefore(start)) { "End date must not precede start date" } }
        fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)
        val days: Long get() = java.time.temporal.ChronoUnit.DAYS.between(start, endInclusive) + 1
        fun previous(): DateRange {
            val previousEnd = start.minusDays(1)
            return DateRange(previousEnd.minusDays(days - 1), previousEnd)
        }
    }

    data class Comparison(val current: Double, val previous: Double) {
        val absoluteChange: Double get() = current - previous
        /** null means a percentage is mathematically undefined (no baseline). */
        val percentageChange: Double? get() = if (previous == 0.0) null else ((current - previous) / kotlin.math.abs(previous)) * 100.0
    }

    data class Aggregate(
        val total: Double,
        val count: Int,
        val byDate: Map<LocalDate, Double>,
        val byCategory: Map<String, Double>
    )

    data class GoalProgress(val target: Double, val current: Double) {
        val remaining: Double get() = (target - current).coerceAtLeast(0.0)
        val progress: Double get() = if (target <= 0.0) 0.0 else (current / target).coerceIn(0.0, 1.0)
        val isComplete: Boolean get() = target > 0.0 && current >= target
    }

    fun goalProgress(target: Double, current: Double): GoalProgress = GoalProgress(
        target = target.takeIf { it.isFinite() && it > 0.0 } ?: 0.0,
        current = current.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    )

    fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value.trim())
    } catch (_: DateTimeParseException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    fun amount(entry: LogEntry): Double? = entry.amount.trim().toDoubleOrNull()
        ?.takeIf { it.isFinite() }

    fun aggregate(
        entries: List<LogEntry>,
        range: DateRange,
        category: String? = null,
        moneyCategory: String? = null
    ): Aggregate {
        val valid = entries.mapNotNull { entry ->
            val date = parseDate(entry.date) ?: return@mapNotNull null
            val value = amount(entry) ?: return@mapNotNull null
            if (!range.contains(date)) return@mapNotNull null
            if (category != null && !entry.category.equals(category, ignoreCase = true)) return@mapNotNull null
            if (moneyCategory != null && !entry.moneyCategory.equals(moneyCategory, ignoreCase = true)) return@mapNotNull null
            Triple(entry, date, value)
        }
        return Aggregate(
            total = valid.sumOf { it.third },
            count = valid.size,
            byDate = valid.groupBy { it.second }.mapValues { (_, rows) -> rows.sumOf { it.third } }.toSortedMap(),
            byCategory = valid.groupBy { it.first.category.trim().ifEmpty { "Uncategorized" } }
                .mapValues { (_, rows) -> rows.sumOf { it.third } }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        )
    }

    fun compare(current: Double, previous: Double): Comparison =
        Comparison(current = current.takeIf { it.isFinite() } ?: 0.0,
            previous = previous.takeIf { it.isFinite() } ?: 0.0)

    fun dateRange(period: Period, today: LocalDate, customStart: LocalDate? = null, customEnd: LocalDate? = null): DateRange =
        when (period) {
            Period.DAILY -> DateRange(today, today)
            Period.WEEKLY -> {
                val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
                DateRange(start, start.plusDays(6))
            }
            Period.MONTHLY -> DateRange(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1).minusDays(1))
            Period.YEARLY -> DateRange(today.withDayOfYear(1), today.withDayOfYear(1).plusYears(1).minusDays(1))
            Period.CUSTOM -> {
                val start = requireNotNull(customStart) { "Custom start date is required" }
                val end = requireNotNull(customEnd) { "Custom end date is required" }
                DateRange(start, end)
            }
        }

    fun formatPercent(value: Double?): String = when {
        value == null -> "No previous data"
        !value.isFinite() -> "—"
        else -> String.format(Locale.US, "%+.1f%%", value)
    }
}
