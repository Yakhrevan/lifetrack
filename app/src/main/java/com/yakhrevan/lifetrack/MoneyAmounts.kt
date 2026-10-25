package com.yakhrevan.lifetrack

/** Safe numeric conversion for legacy money entries used by summaries and charts. */
internal object MoneyAmounts {
    const val MAX_ENTRY_AMOUNT = 1_000_000_000.0

    /** Null means the stored value is invalid/unsupported; callers must not fabricate a total from it. */
    fun parse(raw: String): Double? =
        raw.trim().toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 && it <= MAX_ENTRY_AMOUNT }

    /** Compatibility accessor for legacy non-financial surfaces. */
    fun value(raw: String): Double = parse(raw) ?: 0.0
}
