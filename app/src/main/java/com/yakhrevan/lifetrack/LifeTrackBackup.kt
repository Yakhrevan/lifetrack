package com.yakhrevan.lifetrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

internal data class LifeTrackBackupData(
    val entries: List<LogEntry>,
    val preferences: JSONObject?
)

/**
 * Versioned backup format. Legacy exports containing only an entry array remain
 * importable; full backups also include supported local preference files.
 */
internal object LifeTrackBackup {
    private const val FORMAT = "lifetrack_backup"
    private const val VERSION = 1
    private const val MAX_BACKUP_CHARS = 10 * 1024 * 1024
    private val preferenceFiles = listOf(
        "lifetrack_profile",
        "lifetrack_routines",
        "lifetrack_habits_routines_v2",
        "lifetrack_studio",
        "lifetrack_study",
        "lifetrack_money",
        "lifetrack_webling",
        "lifetrack_tasks_v2",
        "lifetrack_notes_v2",
        "lifetrack_personal_goals_v2",
        "lifetrack_settings_v2"
    )

    fun encode(context: Context, entries: List<LogEntry>): String {
        val preferencesJson = JSONObject()
        preferenceFiles.forEach { fileName ->
            val prefs = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            val values = JSONObject()
            prefs.all.forEach { (key, value) ->
                encodePreference(value)?.let { values.put(key, it) }
            }
            preferencesJson.put(fileName, values)
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("entries", JSONArray(EntryCodec.encode(EntryCodec.uniqueById(entries))))
            .put("preferences", preferencesJson)
            .toString(2)
    }

    fun decode(raw: String): LifeTrackBackupData {
        require(raw.length <= MAX_BACKUP_CHARS) { "Backup file is too large to import safely (maximum 10 MB)." }
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Backup file is empty." }
        if (trimmed.startsWith("[")) {
            return LifeTrackBackupData(EntryCodec.decode(trimmed).entries, null)
        }
        val root = JSONObject(trimmed)
        require(root.optString("format") == FORMAT) { "This is not a LifeTrack backup." }
        require(root.optInt("version") in 1..VERSION) { "This backup version is not supported." }
        val entriesJson = root.optJSONArray("entries") ?: error("Backup is missing tracker entries or the entries field is invalid.")
        val preferences = root.optJSONObject("preferences")
            ?: error("Backup is missing settings data or the preferences field is invalid.")
        validatePreferences(preferences)
        val entries = EntryCodec.decode(entriesJson.toString()).entries
        return LifeTrackBackupData(entries, preferences)
    }

    /** Reject malformed preference payloads before the restore flow mutates local data. */
    private fun validatePreferences(preferences: JSONObject) {
        preferenceFiles.forEach { fileName ->
            if (!preferences.has(fileName)) return@forEach
            val values = preferences.optJSONObject(fileName)
                ?: throw IllegalArgumentException("Backup settings section '$fileName' must be an object.")
            values.keys().forEach { key ->
                val encoded = values.optJSONObject(key)
                    ?: throw IllegalArgumentException("Backup setting '$key' in '$fileName' is invalid.")
                val type = encoded.optString("type")
                require(type in setOf("String", "Boolean", "Int", "Long", "Float", "StringSet")) {
                    "Backup setting '$key' uses an unsupported value type."
                }
                require(encoded.has("value") && !encoded.isNull("value")) {
                    "Backup setting '$key' is missing its value."
                }
                val value = encoded.opt("value")
                val validValue = when (type) {
                    "String" -> value is String
                    "Boolean" -> value is Boolean
                    "Int" -> value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0 &&
                        value.toDouble() >= Int.MIN_VALUE && value.toDouble() <= Int.MAX_VALUE
                    "Long" -> value is Number && value.toDouble().isFinite() &&
                        value.toDouble() % 1.0 == 0.0 &&
                        runCatching {
                            val exact = BigDecimal(value.toString())
                            exact >= BigDecimal.valueOf(Long.MIN_VALUE) &&
                                exact <= BigDecimal.valueOf(Long.MAX_VALUE)
                        }.getOrDefault(false)
                    "Float" -> value is Number && value.toDouble().isFinite() &&
                        value.toDouble() >= -Float.MAX_VALUE && value.toDouble() <= Float.MAX_VALUE
                    "StringSet" -> value is JSONArray && (0 until value.length()).all { value.opt(it) is String }
                    else -> false
                }
                require(validValue) { "Backup setting '$key' has a value that does not match its declared type." }
            }
        }
    }

    fun restorePreferences(context: Context, preferencesJson: JSONObject?) {
        if (preferencesJson == null) return
        preferenceFiles.forEach { fileName ->
            // Partial/older backups may omit a section; preserve that local settings file.
            val values = preferencesJson.optJSONObject(fileName) ?: return@forEach
            val prefs = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            val editor = prefs.edit().clear()
            values.keys().forEach keyLoop@ { key ->
                val encoded = values.optJSONObject(key) ?: return@keyLoop
                when (encoded.optString("type")) {
                    "String" -> editor.putString(key, encoded.optString("value"))
                    "Boolean" -> editor.putBoolean(key, encoded.optBoolean("value"))
                    "Int" -> editor.putInt(key, encoded.optInt("value"))
                    "Long" -> editor.putLong(key, encoded.optLong("value"))
                    "Float" -> editor.putFloat(key, encoded.optDouble("value").toFloat())
                    "StringSet" -> {
                        val array = encoded.optJSONArray("value") ?: JSONArray()
                        editor.putStringSet(key, (0 until array.length()).map { array.optString(it) }.toSet())
                    }
                }
            }
            editor.apply()
        }
    }

    private fun encodePreference(value: Any?): JSONObject? = when (value) {
        is String -> JSONObject().put("type", "String").put("value", value)
        is Boolean -> JSONObject().put("type", "Boolean").put("value", value)
        is Int -> JSONObject().put("type", "Int").put("value", value)
        is Long -> JSONObject().put("type", "Long").put("value", value)
        is Float -> JSONObject().put("type", "Float").put("value", value.toDouble())
        is Set<*> -> {
            if (value.all { it is String }) {
                val array = JSONArray()
                value.forEach { array.put(it as String) }
                JSONObject().put("type", "StringSet").put("value", array)
            } else null
        }
        else -> null
    }
}
