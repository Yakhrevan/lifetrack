package com.yakhrevan.lifetrack

import java.util.UUID

/**
 * Canonical record shared by all LifeTrack tracker modules.
 *
 * Amount remains a string for backward compatibility with the current
 * SharedPreferences JSON format and CSV export.
 */
data class LogEntry(
    val category: String,
    val title: String,
    val amount: String,
    val date: String,
    val note: String,
    val id: String = UUID.randomUUID().toString(),
    val moneyCategory: String = "Other"
)
