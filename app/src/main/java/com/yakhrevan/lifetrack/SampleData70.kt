package com.yakhrevan.lifetrack

import java.time.LocalDate

/** Explicitly opt-in, removable sample history for exercising dashboard, Insights and Money. */
internal object SampleData70 {
    private const val ID_PREFIX = "sample70-"
    private const val NOTE_PREFIX = "LifeTrack 70-day sample"

    fun contains(entries: List<LogEntry>): Boolean = entries.any { it.id.startsWith(ID_PREFIX) }
    fun remove(entries: List<LogEntry>): List<LogEntry> = entries.filterNot { it.id.startsWith(ID_PREFIX) }

    fun create(today: LocalDate = LocalDate.now()): List<LogEntry> {
        val result = mutableListOf<LogEntry>()
        val expenseCategories = listOf("Food", "Transport", "Bills", "Health", "Shopping", "Learning")
        val expenseTitles = listOf("Lunch", "Bus / fuel", "Monthly service", "Pharmacy", "Household item", "Course material")

        for (offset in 69 downTo 0) {
            val date = today.minusDays(offset.toLong())
            val key = date.toString()
            val seed = date.toEpochDay().toInt()
            val dayIndex = 69 - offset
            result += entry(key, "Study", "Focused study session", 25 + Math.floorMod(seed * 17, 96), "Study block ${1 + dayIndex % 3}")
            listOf(350, 450, 300, 500).forEachIndexed { index, base ->
                result += entry(key, "Water", "Water intake", base + Math.floorMod(seed + index * 73, 151), "Sample hydration log ${index + 1}")
            }
            if (date.dayOfWeek.value in setOf(1, 3, 5, 6)) {
                result += entry(key, "Exercise", listOf("Walk", "Strength training", "Mobility session")[dayIndex % 3],
                    15 + Math.floorMod(seed * 3, 46), "Sample activity duration in minutes")
            }
            if (date.dayOfWeek.value in setOf(2, 4, 6, 7)) {
                result += entry(key, "Care", listOf("Reading", "Stretching", "Planning")[dayIndex % 3], 1, "Sample routine completion")
            }
            val expenseIndex = Math.floorMod(seed, expenseCategories.size)
            result += LogEntry(
                category = "Expense", title = expenseTitles[expenseIndex],
                amount = (80 + Math.floorMod(seed * 29, 1420)).toString(), date = key,
                note = "$NOTE_PREFIX · expense", id = "$ID_PREFIX$key-expense",
                moneyCategory = expenseCategories[expenseIndex]
            )
            if (date.dayOfMonth == 1 || date.dayOfMonth == 15) {
                val salary = date.dayOfMonth == 1
                val incomeAmount = if (salary) 28000 + Math.floorMod(seed * 11, 5000) else 1200 + Math.floorMod(seed * 7, 6500)
                result += LogEntry(
                    category = "Income", title = if (salary) "Salary" else "Freelance payment",
                    amount = incomeAmount.toString(), date = key,
                    note = "$NOTE_PREFIX · income", id = "$ID_PREFIX$key-income",
                    moneyCategory = if (salary) "Salary" else "Freelance"
                )
            }
        }
        return result.sortedByDescending { it.date }
    }

    private fun entry(date: String, category: String, title: String, amount: Int, detail: String) =
        LogEntry(
            category = category, title = title, amount = amount.toString(), date = date,
            note = "$NOTE_PREFIX · $detail",
            id = "$ID_PREFIX$date-$category-${title.lowercase().replace(Regex("[^a-z0-9]+"), "-")}-${detail.hashCode().toUInt()}",
            moneyCategory = "Other"
        )
}
