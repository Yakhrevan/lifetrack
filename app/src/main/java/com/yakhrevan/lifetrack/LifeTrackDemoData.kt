package com.yakhrevan.lifetrack

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Clearly labelled synthetic data for QA. Only used for a fresh install where
 * both the tracker store and profile setup are empty.
 */
internal object LifeTrackDemoData {
    const val PROFILE_NAME = "LifeTrack Demo"
    const val WATER_GOAL_ML = 2500
    const val STUDY_GOAL_MIN = 120

    fun create(today: Date = Date()): List<LogEntry> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayCalendar = Calendar.getInstance().apply { time = today }
        val entries = mutableListOf<LogEntry>()
        val expenseOptions = listOf(
            "Food & groceries" to listOf("Groceries", "Lunch", "Coffee & snacks"),
            "Transport" to listOf("Bus / fuel", "Cab fare"),
            "Shopping" to listOf("Household items", "Clothing"),
            "Bills & utilities" to listOf("Mobile recharge", "Electricity bill"),
            "Health" to listOf("Pharmacy", "Health supplies"),
            "Entertainment" to listOf("Movie / games"),
            "Subscriptions" to listOf("Monthly subscription"),
            "Home" to listOf("Home supplies"),
            "Education" to listOf("Learning material")
        )
        repeat(70) { index ->
            val day = Calendar.getInstance().apply {
                time = todayCalendar.time
                add(Calendar.DAY_OF_YEAR, index - 69)
            }
            val date = dateFormat.format(day.time)
            fun add(
                category: String,
                title: String,
                amount: Int,
                moneyCategory: String = "Other",
                note: String = "DEMO DATA · synthetic test entry"
            ) {
                entries += LogEntry(category, title, amount.toString(), date, note, moneyCategory = moneyCategory)
            }

            add("Study", if (index % 5 == 0) "Deep work session" else "Study session", 45 + (index * 37 % 116))
            add("Water", "Morning water", 300 + (index * 17 % 251))
            add("Water", "Afternoon water", 350 + (index * 29 % 451))
            if (index % 2 == 0) add("Meal", if (index % 4 == 0) "Home-cooked meal" else "Lunch", 1, note = "DEMO DATA · sample meal log")
            if (index % 3 != 0) add("Care", if (index % 2 == 0) "Personal care" else "Routine completed", 1, note = "DEMO DATA · sample routine log")
            if (index % 4 != 1) {
                val choice = expenseOptions[index % expenseOptions.size]
                val title = choice.second[index % choice.second.size]
                val amount = when (choice.first) {
                    "Food & groceries" -> 120 + index % 8 * 35
                    "Transport" -> 45 + index % 5 * 25
                    "Shopping" -> 180 + index % 7 * 60
                    "Bills & utilities" -> 299 + index % 4 * 100
                    "Health" -> 80 + index % 5 * 45
                    "Entertainment" -> 150 + index % 4 * 75
                    "Subscriptions" -> 149
                    "Home" -> 220 + index % 4 * 80
                    else -> 99 + index % 5 * 50
                }
                add("Expense", title, amount, choice.first)
            }
            if (index % 7 == 0) add("Expense", "Snacks", 50 + index % 4 * 20, "Food & groceries")
            if (index == 5 || index == 35 || index == 65) add("Income", "Sample salary", 25000, "Salary", "DEMO DATA · synthetic income")
        }
        return entries.sortedWith(compareByDescending<LogEntry> { it.date }.thenBy { it.category })
    }
}
