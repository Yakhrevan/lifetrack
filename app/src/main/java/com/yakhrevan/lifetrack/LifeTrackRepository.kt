package com.yakhrevan.lifetrack

import android.content.Context

/**
 * Single local persistence boundary for LifeTrack tracker entries.
 *
 * The existing SharedPreferences JSON format is intentionally retained so this
 * refactor does not discard or require migration of existing user data.
 */
internal class LifeTrackRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    fun loadEntries(): List<LogEntry> {
        val raw = preferences.getString(ENTRIES_KEY, null)
        return try {
            val result = EntryCodec.decode(raw)
            if (result.skippedRaw.isNotEmpty()) {
                // Keep unreadable items instead of dropping them silently.
                preferences.edit().putString(SKIPPED_KEY, result.skippedRaw.toString()).apply()
            }
            val entries = EntryCodec.newestFirst(result.entries)
            if (result.needsRewrite) {
                backupRaw(raw)
                saveEntries(entries)
            }
            entries
        } catch (_: Exception) {
            if (!raw.isNullOrBlank()) {
                preferences.edit()
                    .putString(CORRUPT_BACKUP_KEY, raw)
                    .putLong(CORRUPT_BACKUP_TIME_KEY, System.currentTimeMillis())
                    .apply()
            }
            emptyList()
        }
    }

    fun saveEntries(entries: List<LogEntry>) {
        val safeEntries = EntryCodec.uniqueById(entries)
        preferences.edit()
            .putString(ENTRIES_KEY, EntryCodec.encode(safeEntries))
            .putInt(SCHEMA_KEY, SCHEMA_VERSION)
            .apply()
    }

    /** Clearing keeps one recoverable copy so the action is never fully silent. */
    fun clearEntries() {
        backupRaw(preferences.getString(ENTRIES_KEY, null))
        preferences.edit().remove(ENTRIES_KEY).apply()
    }

    private fun backupRaw(raw: String?) {
        if (!raw.isNullOrBlank()) preferences.edit().putString(LAST_BACKUP_KEY, raw).apply()
    }

    private companion object {
        const val STORE_NAME = "lifetrack"
        const val ENTRIES_KEY = "entries"
        const val CORRUPT_BACKUP_KEY = "entries_corrupt_backup"
        const val CORRUPT_BACKUP_TIME_KEY = "entries_corrupt_backup_time"
        const val SKIPPED_KEY = "entries_skipped_items"
        const val LAST_BACKUP_KEY = "entries_last_backup"
        const val SCHEMA_KEY = "entries_schema_version"
        const val SCHEMA_VERSION = 1
    }
}