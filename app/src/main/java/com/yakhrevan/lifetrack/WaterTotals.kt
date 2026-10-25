package com.yakhrevan.lifetrack

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Pure calculations for water totals derived from saved LifeTrack log entries.
 * Water amounts are stored as millilitres in LogEntry.amount.
 */
internal object WaterTotals {
    fun dayTotal(logs: List<LogEntry>, date: String): Int {
        if (parseDate(date) == null) return 0
        return logs.asSequence()
            .filter { it.category.equals("Water", ignoreCase = true) && it.date == date }
            .sumOf { entry ->
                entry.amount.trim().toIntOrNull()?.takeIf { it > 0 } ?: 0
            }
    }

    /**
     * Returns (ISO date, millilitres) pairs oldest-first, including zero-total days.
     * Invalid dates or a non-positive day count produce an empty list.
     */
    fun history(logs: List<LogEntry>, today: String, days: Int = 7): List<Pair<String, Int>> {
        val end = parseDate(today) ?: return emptyList()
        if (days <= 0) return emptyList()
        val start = end.minusDays((days - 1).toLong())
        return (0 until days).map { offset ->
            val date = start.plusDays(offset.toLong()).toString()
            date to dayTotal(logs, date)
        }
    }

    fun streak(logs: List<LogEntry>, today: String, targetMl: Int = 0): Int {
        val todayDate = parseDate(today) ?: LocalDate.now()
        val allLoggedDates = logs.asSequence()
            .filter { it.category.equals("Water", ignoreCase = true) }
            .mapNotNull { parseDate(it.date) }
            .toSet()

        if (allLoggedDates.isEmpty()) return 0

        var currentCheck = todayDate
        // If not logged today yet, check if yesterday was logged to keep the streak unbroken
        if (!allLoggedDates.contains(currentCheck)) {
            val yesterday = currentCheck.minusDays(1)
            if (allLoggedDates.contains(yesterday)) {
                currentCheck = yesterday
            } else {
                return 0
            }
        }

        var streakCount = 0
        while (allLoggedDates.contains(currentCheck)) {
            val dayAmt = dayTotal(logs, currentCheck.toString())
            if (targetMl > 0) {
                // If target provided, streak counts days reaching at least 80% of target
                if (dayAmt >= (targetMl * 0.8f)) {
                    streakCount++
                    currentCheck = currentCheck.minusDays(1)
                } else break
            } else {
                if (dayAmt > 0) {
                    streakCount++
                    currentCheck = currentCheck.minusDays(1)
                } else break
            }
        }
        return streakCount
    }

    fun dailyAverage(logs: List<LogEntry>, today: String, days: Int = 7): Int {
        if (days <= 0) return 0
        val hist = history(logs, today, days)
        val sum = hist.sumOf { it.second }
        return sum / days
    }

    fun goalDaysPercent(logs: List<LogEntry>, today: String, targetMl: Int, days: Int = 30): Int {
        if (days <= 0 || targetMl <= 0) return 0
        val hist = history(logs, today, days)
        val metDays = hist.count { it.second >= (targetMl * 0.9f) }
        return ((metDays.toFloat() / days.toFloat()) * 100).toInt()
    }

    fun weekTotal(logs: List<LogEntry>, today: String): Int {
        val end = parseDate(today) ?: LocalDate.now()
        val dayOfWeek = end.dayOfWeek.value // 1 = Mon, 7 = Sun
        val monday = end.minusDays((dayOfWeek - 1).toLong())
        return (0..6).sumOf { dayTotal(logs, monday.plusDays(it.toLong()).toString()) }
    }

    fun previousWeekTotal(logs: List<LogEntry>, today: String): Int {
        val end = parseDate(today) ?: LocalDate.now()
        val dayOfWeek = end.dayOfWeek.value
        val prevMonday = end.minusDays((dayOfWeek - 1 + 7).toLong())
        return (0..6).sumOf { dayTotal(logs, prevMonday.plusDays(it.toLong()).toString()) }
    }

    fun monthTotal(logs: List<LogEntry>, monthPrefix: String): Int {
        return logs.asSequence()
            .filter { it.category.equals("Water", ignoreCase = true) && it.date.startsWith(monthPrefix) }
            .sumOf { entry -> entry.amount.trim().toIntOrNull()?.takeIf { it > 0 } ?: 0 }
    }

    fun percentageChange(current: Int, previous: Int): Int {
        if (previous <= 0) return if (current > 0) 100 else 0
        return (((current - previous).toFloat() / previous.toFloat()) * 100).toInt()
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    } catch (_: NullPointerException) {
        null
    }
}
