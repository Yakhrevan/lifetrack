package com.yakhrevan.lifetrack

/**
 * Shared validation rules for entries created from the add/edit forms and other
 * tracker entry points. Keep these rules centralized so every screen behaves
 * consistently as more modules are added.
 */
internal object EntryValidator {
    private val wholeNumberCategories = setOf(
        "Water", "Study", "Art", "Phone usage", "Care", "Face routine", "Meal"
    )
    private val durationCategories = setOf("Study", "Art", "Phone usage")

    fun amountError(category: String, raw: String): String? {
        val parsed = raw.trim().toDoubleOrNull()
        if (parsed == null || !parsed.isFinite() || parsed <= 0.0) {
            return "Enter a valid amount greater than zero."
        }
        if (category in wholeNumberCategories &&
            (parsed % 1.0 != 0.0 || parsed > 100000.0)
        ) {
            return "Enter a whole number within a reasonable range."
        }
        if (category in setOf("Income", "Expense") && parsed > 1_000_000_000.0) {
            return "Amount is too large. Check the value and try again."
        }
        if (category in durationCategories && parsed > 1440.0) {
            return "Duration must be 1–1440 minutes."
        }
        if (category == "Water" && parsed > 10000.0) {
            return "Water entry must be 10,000 ml or less."
        }
        return null
    }

    fun normalizeAmount(raw: String): String = raw.trim()
}
