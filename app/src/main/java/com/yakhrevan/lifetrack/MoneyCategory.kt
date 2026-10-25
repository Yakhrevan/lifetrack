package com.yakhrevan.lifetrack

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import java.util.Locale

/**
 * Built-in and custom transaction categories, icons, colors, and budgets.
 */
object MoneyCategory {
    val expenses = listOf(
        "Food",
        "Travel",
        "Shopping",
        "Home",
        "Bills",
        "Health",
        "Study",
        "Entertainment",
        "Transport",
        "Fuel",
        "Groceries",
        "Personal",
        "Pets",
        "Clothing",
        "Electronics",
        "Dining Out",
        "Subscription",
        "Gift",
        "Tax",
        "Loan",
        "Other"
    )

    val incomes = listOf(
        "Salary",
        "Freelance",
        "Business",
        "Investment",
        "Gift",
        "Refund",
        "Pocket Money",
        "Other"
    )

    /** User-created categories (observable so pickers update immediately). */
    val customExpenses = mutableStateListOf<String>()
    val customIncomes = mutableStateListOf<String>()
    val customIconKeys = mutableStateMapOf<String, String>()
    val customColors = mutableStateMapOf<String, Long>()
    /** Legacy/current-month budget values retained for existing installs. */
    val categoryBudgets = mutableStateMapOf<String, Double>()
    /** Month-specific snapshots keyed by "yyyy-MM::Category". */
    val monthlyCategoryBudgets = mutableStateMapOf<String, Double>()

    private fun monthlyBudgetKey(month: String, category: String) = "$month::$category"

    fun categoryBudgetForMonth(category: String, month: String): Double? {
        monthlyCategoryBudgets[monthlyBudgetKey(month, category)]?.let { return it }
        return if (month == java.time.YearMonth.now().toString()) categoryBudgets[category] else null
    }

    fun categoryBudgetsForMonth(month: String): Map<String, Double> {
        val result = linkedMapOf<String, Double>()
        val prefix = "$month::"
        monthlyCategoryBudgets.forEach { (key, value) ->
            if (key.startsWith(prefix)) result[key.removePrefix(prefix)] = value
        }
        if (month == java.time.YearMonth.now().toString()) {
            categoryBudgets.forEach { (category, value) -> result.putIfAbsent(category, value) }
        }
        return result
    }

    fun categoryBudgetHistory(category: String): Map<String, Double> =
        monthlyCategoryBudgets.entries.mapNotNull { (key, value) ->
            val separator = key.indexOf("::")
            if (separator > 0 && key.substring(separator + 2).equals(category, ignoreCase = true)) {
                key.substring(0, separator) to value
            } else null
        }.toMap()

    val iconChoices = listOf(
        "restaurant", "fastfood", "coffee",
        "transport", "directions_car", "flight",
        "shopping", "clothing", "electronics",
        "home", "build", "pets",
        "bills", "bolt", "phone",
        "health", "fitness", "medical",
        "study", "education", "laptop",
        "entertainment", "movie", "sports",
        "salary", "work", "savings",
        "business", "investment", "gift",
        "refund", "tax", "loan", "other"
    )

    val colorPalette = listOf(
        Color(0xFFEF4444), // Coral Red
        Color(0xFFF97316), // Orange
        Color(0xFFF59E0B), // Amber / Gold
        Color(0xFF10B981), // Emerald Green
        Color(0xFF06B6D4), // Cyan
        Color(0xFF3B82F6), // Blue
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Magenta / Pink
        Color(0xFF6366F1), // Indigo
        Color(0xFF14B8A6)  // Teal
    )

    fun iconKeyFor(category: String): String = customIconKeys[category] ?: when (category.lowercase(Locale.ROOT).trim()) {
        "food", "food & groceries", "dining out" -> "restaurant"
        "groceries", "fastfood" -> "fastfood"
        "coffee" -> "coffee"
        "travel", "car" -> "directions_car"
        "transport", "fuel", "petrol" -> "transport"
        "shopping" -> "shopping"
        "clothing" -> "clothing"
        "electronics" -> "electronics"
        "home", "rent" -> "home"
        "bills", "bills & utilities", "electricity", "water" -> "bolt"
        "phone", "subscription", "subscriptions", "wifi" -> "phone"
        "health", "medical" -> "health"
        "fitness" -> "fitness"
        "study", "education" -> "study"
        "entertainment", "movie" -> "movie"
        "sports", "gaming" -> "sports"
        "laptop", "freelance" -> "laptop"
        "salary" -> "salary"
        "business" -> "business"
        "investment", "savings" -> "savings"
        "gift" -> "gift"
        "refund" -> "refund"
        "pets" -> "pets"
        "tax" -> "tax"
        "loan" -> "loan"
        "pocket money" -> "savings"
        else -> "other"
    }

    fun iconFor(category: String): ImageVector = iconByKey(iconKeyFor(category))

    fun iconByKey(key: String): ImageVector = when (key.lowercase(Locale.ROOT).trim()) {
        "restaurant" -> Icons.Default.Restaurant
        "fastfood" -> Icons.Default.Fastfood
        "coffee" -> Icons.Default.LocalCafe
        "transport" -> Icons.Default.DirectionsBus
        "directions_car" -> Icons.Default.DirectionsCar
        "flight" -> Icons.Default.Flight
        "shopping" -> Icons.Default.ShoppingBag
        "clothing" -> Icons.Default.Checkroom
        "electronics" -> Icons.Default.Devices
        "home" -> Icons.Default.Home
        "build" -> Icons.Default.Build
        "pets" -> Icons.Default.Pets
        "bills", "bolt" -> Icons.Default.Bolt
        "phone" -> Icons.Default.PhoneAndroid
        "health" -> Icons.Default.Favorite
        "fitness" -> Icons.Default.FitnessCenter
        "medical" -> Icons.Default.LocalHospital
        "study", "education" -> Icons.Default.MenuBook
        "laptop" -> Icons.Default.Laptop
        "entertainment", "movie" -> Icons.Default.Movie
        "sports" -> Icons.Default.SportsEsports
        "salary", "work" -> Icons.Default.Work
        "savings" -> Icons.Default.Savings
        "business" -> Icons.Default.BusinessCenter
        "investment" -> Icons.Default.TrendingUp
        "gift" -> Icons.Default.CardGiftcard
        "refund" -> Icons.Default.Replay
        "tax" -> Icons.Default.ReceiptLong
        "loan" -> Icons.Default.AccountBalance
        else -> Icons.Default.Category
    }

    fun colorFor(category: String): Color {
        val customVal = customColors[category]
        if (customVal != null) return Color(customVal.toULong())

        return when (category.lowercase(Locale.ROOT).trim()) {
            "food", "food & groceries", "dining out" -> Color(0xFFEF4444) // Red
            "travel", "directions_car", "flight" -> Color(0xFF0EA5E9)    // Sky Blue
            "shopping", "clothing", "electronics" -> Color(0xFFF59E0B)  // Gold
            "home", "rent" -> Color(0xFF6366F1)                         // Indigo
            "bills", "bills & utilities", "electricity" -> Color(0xFF3B82F6) // Blue
            "health", "fitness", "medical" -> Color(0xFFEC4899)          // Pink
            "study", "education" -> Color(0xFF10B981)                   // Teal
            "entertainment", "sports", "gaming" -> Color(0xFF8B5CF6)    // Purple
            "transport", "fuel", "petrol", "diesel", "groceries" -> Color(0xFFF97316)               // Orange
            "salary" -> Color(0xFF10B981)                               // Green
            "freelance", "business" -> Color(0xFF06B6D4)                // Cyan
            "investment", "savings" -> Color(0xFFA855F7)                // Violet
            "gift" -> Color(0xFFF43F5E)                                 // Rose
            "refund", "pocket money" -> Color(0xFF14B8A6)               // Teal
            else -> Color(0xFF94A3B8)                                   // Slate
        }
    }

    fun optionsFor(transactionType: String): List<String> =
        if (transactionType == "Income") incomes + customIncomes else expenses + customExpenses

    fun normalize(value: String): String {
        val input = value.trim().lowercase(Locale.ROOT)
        if (input.isBlank()) return "Other"
        val categories = expenses + incomes + customExpenses + customIncomes
        categories.firstOrNull { input == it.lowercase(Locale.ROOT) }?.let { return it }
        return when {
            listOf("petrol", "fuel", "gas station", "bike fuel", "bus", "taxi", "cab", "transport", "travel").any(input::contains) -> "Transport"
            listOf("grocery", "groceries", "supermarket").any(input::contains) -> "Groceries"
            listOf("restaurant", "food", "lunch", "dinner", "snack", "coffee").any(input::contains) -> "Food"
            listOf("electricity", "water bill", "internet bill", "utility", "bill").any(input::contains) -> "Bills"
            listOf("salary", "paycheck").any(input::contains) -> "Salary"
            listOf("freelance", "contract work").any(input::contains) -> "Freelance"
            else -> "Other"
        }
    }


    fun nameError(name: String, existing: List<String>): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Enter a category name."
            n.length > 30 -> "Keep the name to 30 characters."
            n.any { it == ',' || it == '"' || it == '\n' } -> "Avoid commas, quotes and new lines."
            existing.any { it.equals(n, ignoreCase = true) } -> "That category already exists."
            else -> null
        }
    }

    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("lifetrack_money", Context.MODE_PRIVATE)
        fun read(key: String) = try {
            val a = JSONArray(prefs.getString(key, "[]"))
            (0 until a.length()).map { a.getString(it) }
        } catch (_: Exception) { emptyList() }

        customExpenses.clear()
        customExpenses.addAll(read("custom_expense_categories"))
        customIncomes.clear()
        customIncomes.addAll(read("custom_income_categories"))
        customIconKeys.clear()
        customColors.clear()
        categoryBudgets.clear()
        monthlyCategoryBudgets.clear()

        prefs.all.forEach { (key, value) ->
            when {
                key.startsWith("category_icon_") -> {
                    val cat = key.removePrefix("category_icon_")
                    val v = value as? String
                    if (!v.isNullOrBlank()) customIconKeys[cat] = v
                }
                key.startsWith("category_color_") -> {
                    val cat = key.removePrefix("category_color_")
                    val v = (value as? Number)?.toLong() ?: (value as? String)?.toLongOrNull()
                    if (v != null) customColors[cat] = v
                }
                key.startsWith("category_budget_month_") -> {
                    val remainder = key.removePrefix("category_budget_month_")
                    val month = remainder.take(7)
                    val category = remainder.drop(8)
                    val v = (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull()
                    if (month.matches(Regex("\\d{4}-\\d{2}")) && category.isNotBlank() && v != null && v > 0.0) {
                        monthlyCategoryBudgets[monthlyBudgetKey(month, category)] = v
                    }
                }
                key.startsWith("category_budget_") -> {
                    val cat = key.removePrefix("category_budget_")
                    val v = (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull()
                    if (v != null && v > 0.0) categoryBudgets[cat] = v
                }
            }
        }
    }

    fun addCustom(
        context: Context,
        transactionType: String,
        name: String,
        iconKey: String = "other",
        colorValue: Long? = null,
        budgetLimit: Double? = null
    ): String? {
        val error = nameError(name, optionsFor(transactionType))
        if (error != null) return error
        val cleanName = name.trim()
        val list = if (transactionType == "Income") customIncomes else customExpenses
        list.add(cleanName)

        val key = if (transactionType == "Income") "custom_income_categories" else "custom_expense_categories"
        val prefs = context.applicationContext.getSharedPreferences("lifetrack_money", Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(key, JSONArray(list.toList()).toString())
            .putString("category_icon_$cleanName", iconKey)

        customIconKeys[cleanName] = iconKey

        if (colorValue != null) {
            editor.putLong("category_color_$cleanName", colorValue)
            customColors[cleanName] = colorValue
        }
        if (budgetLimit != null && budgetLimit > 0.0) {
            editor.putString("category_budget_$cleanName", budgetLimit.toString())
            categoryBudgets[cleanName] = budgetLimit
        }
        editor.apply()
        return null
    }

    fun setCategoryBudget(
        context: Context,
        category: String,
        limit: Double?,
        month: String = java.time.YearMonth.now().toString()
    ) {
        require(month.matches(Regex("\\d{4}-\\d{2}"))) { "Month must use yyyy-MM format" }
        val prefs = context.applicationContext.getSharedPreferences("lifetrack_money", Context.MODE_PRIVATE)
        val historyKey = "category_budget_month_${month}_${category}"
        val editor = prefs.edit()
        if (limit != null && limit.isFinite() && limit > 0.0) {
            editor.putString(historyKey, limit.toString())
            monthlyCategoryBudgets[monthlyBudgetKey(month, category)] = limit
            if (month == java.time.YearMonth.now().toString()) {
                editor.putString("category_budget_$category", limit.toString())
                categoryBudgets[category] = limit
            }
        } else {
            editor.remove(historyKey)
            monthlyCategoryBudgets.remove(monthlyBudgetKey(month, category))
            if (month == java.time.YearMonth.now().toString()) {
                editor.remove("category_budget_$category")
                categoryBudgets.remove(category)
            }
        }
        editor.apply()
    }

    fun deleteCustom(context: Context, transactionType: String, categoryName: String) {
        val list = if (transactionType == "Income") customIncomes else customExpenses
        list.remove(categoryName)
        customIconKeys.remove(categoryName)
        customColors.remove(categoryName)
        categoryBudgets.remove(categoryName)

        val key = if (transactionType == "Income") "custom_income_categories" else "custom_expense_categories"
        val prefs = context.applicationContext.getSharedPreferences("lifetrack_money", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(key, JSONArray(list.toList()).toString())
            .remove("category_icon_$categoryName")
            .remove("category_color_$categoryName")
            .remove("category_budget_$categoryName")
            .apply()
    }
}