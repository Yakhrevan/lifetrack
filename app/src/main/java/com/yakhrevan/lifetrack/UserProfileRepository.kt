package com.yakhrevan.lifetrack

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

// ==========================================
// 1. DATA MODELS
// ==========================================

data class UserProfile(
    val displayName: String = "",
    val avatarId: String = "avatar_1",
    val avatarUri: String? = null,
    val gender: String = "", // "Female", "Male", "Prefer not to say", ""
    val dateOfBirth: String = "", // e.g. "14 Jan 2000"
    val bio: String = "", // e.g. "Stay consistent, grow everyday"
    val location: String = "",
    val email: String = ""
)

data class AppSettings(
    val themeMode: String = "Dark", // "System", "Light", "Dark"
    val accentColorHex: String = "#A855F7",
    val textSizeMultiplier: Float = 1.0f,
    val dashboardStyle: String = "Comfortable", // "Compact", "Comfortable"
    val enableAnimations: Boolean = true,
    val reduceMotion: Boolean = false,
    
    // Notifications
    val notificationsEnabled: Boolean = true,
    val defaultReminderTime: String = "20:00",
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "06:00",
    val snoozeDurationMinutes: Int = 10,
    val notifyHabits: Boolean = true,
    val notifyRoutines: Boolean = true,
    val notifyTasks: Boolean = true,
    val notifyNotes: Boolean = true,
    val notifyWater: Boolean = true,
    val notifyStudy: Boolean = true,
    val notifyDigitalWellbeing: Boolean = true,

    // Personalisation & Units
    val appLanguage: String = "English",
    val dateFormat: String = "14 Jan 2025",
    val timeFormat: String = "12-hour", // "12-hour", "24-hour"
    val weekStartsOn: String = "Monday", // "Monday", "Sunday"
    val calendarFirstDay: String = "Monday",
    val weightUnit: String = "kg", // "kg", "lbs"
    val heightUnit: String = "cm", // "cm", "ft"
    val waterUnit: String = "ml", // "ml", "oz"
    val tempUnit: String = "°C", // "°C", "°F"

    // App Preferences
    val startPage: String = "Home",
    val hapticFeedback: Boolean = true,
    val soundEffects: Boolean = false,
    val autoSave: Boolean = true,
    val confirmBeforeDelete: Boolean = true,
    val showTips: Boolean = true
)

// ==========================================
// 2. USER PROFILE REPOSITORY
// ==========================================

object UserProfileRepository {
    private const val PREFS_PROFILE = "lifetrack_profile"
    private const val PREFS_SETTINGS = "lifetrack_settings_v2"

    private fun getProfilePrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_PROFILE, Context.MODE_PRIVATE)

    private fun getSettingsPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

    // Profile CRUD
    fun getProfile(context: Context): UserProfile {
        val prefs = getProfilePrefs(context)
        val name = prefs.getString("profile_name", null)
            ?: prefs.getString("name", "") ?: ""
        return UserProfile(
            displayName = name,
            avatarId = prefs.getString("avatar_id", "avatar_1") ?: "avatar_1",
            avatarUri = prefs.getString("avatar_uri", null),
            gender = prefs.getString("profile_gender", "") ?: "",
            dateOfBirth = prefs.getString("profile_dob", "") ?: "",
            bio = prefs.getString("profile_bio", "") ?: "",
            location = prefs.getString("profile_location", "") ?: "",
            email = prefs.getString("profile_email", "") ?: ""
        )
    }

    fun saveProfile(context: Context, profile: UserProfile) {
        getProfilePrefs(context).edit()
            .putString("profile_name", profile.displayName)
            .putString("name", profile.displayName)
            .putString("avatar_id", profile.avatarId)
            .putString("avatar_uri", profile.avatarUri)
            .putString("profile_gender", profile.gender)
            .putString("profile_dob", profile.dateOfBirth)
            .putString("profile_bio", profile.bio)
            .putString("profile_location", profile.location)
            .putString("profile_email", profile.email)
            .putBoolean("profile_saved", true)
            .apply()
    }

    fun updateAvatar(context: Context, avatarId: String, avatarUri: String? = null) {
        getProfilePrefs(context).edit()
            .putString("avatar_id", avatarId)
            .putString("avatar_uri", avatarUri)
            .apply()
    }

    /**
     * Safely copies an external image to the app's internal storage,
     * ensuring that the profile avatar persists across app restarts, reboots,
     * and external URI revocation without requiring storage permissions.
     */
    fun saveCustomAvatar(context: Context, sourceUri: Uri): String? {
        return runCatching {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            } ?: return null

            var sampleSize = 1
            val maxDimension = 1024
            while (boundsOptions.outWidth / sampleSize > maxDimension || boundsOptions.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            val avatarFile = File(context.filesDir, "custom_profile_avatar.jpg")
            FileOutputStream(avatarFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            val localUri = Uri.fromFile(avatarFile).toString()
            updateAvatar(context, "custom_gallery", localUri)
            localUri
        }.getOrNull()
    }

    /**
     * Removes any custom profile photo, deleting the local file and resetting to a built-in avatar.
     */
    fun removeCustomAvatar(context: Context, defaultAvatarId: String = "avatar_1") {
        runCatching {
            val file = File(context.filesDir, "custom_profile_avatar.jpg")
            if (file.exists()) file.delete()
        }
        updateAvatar(context, defaultAvatarId, null)
    }

    fun updateGender(context: Context, gender: String) {
        getProfilePrefs(context).edit()
            .putString("profile_gender", gender)
            .apply()
    }

    // Settings CRUD
    fun getSettings(context: Context): AppSettings {
        val prefs = getSettingsPrefs(context)
        val legacyProfile = getProfilePrefs(context)
        val defaultDark = legacyProfile.getBoolean("dark_theme", true)

        return AppSettings(
            themeMode = prefs.getString("theme_mode", if (defaultDark) "Dark" else "Light") ?: "Dark",
            accentColorHex = prefs.getString("accent_color_hex", "#A855F7") ?: "#A855F7",
            textSizeMultiplier = prefs.getFloat("text_size_multiplier", 1.0f),
            dashboardStyle = prefs.getString("dashboard_style", "Comfortable") ?: "Comfortable",
            enableAnimations = prefs.getBoolean("enable_animations", true),
            reduceMotion = prefs.getBoolean("reduce_motion", false),

            notificationsEnabled = prefs.getBoolean("notifications_enabled", true),
            defaultReminderTime = prefs.getString("default_reminder_time", "20:00") ?: "20:00",
            quietHoursStart = prefs.getString("quiet_hours_start", "22:00") ?: "22:00",
            quietHoursEnd = prefs.getString("quiet_hours_end", "06:00") ?: "06:00",
            snoozeDurationMinutes = prefs.getInt("snooze_duration_min", 10),
            notifyHabits = prefs.getBoolean("notify_habits", true),
            notifyRoutines = prefs.getBoolean("notify_routines", true),
            notifyTasks = prefs.getBoolean("notify_tasks", true),
            notifyNotes = prefs.getBoolean("notify_notes", true),
            notifyWater = prefs.getBoolean("notify_water", true),
            notifyStudy = prefs.getBoolean("notify_study", true),
            notifyDigitalWellbeing = prefs.getBoolean("notify_digital_wellbeing", true),

            appLanguage = prefs.getString("app_language", "English") ?: "English",
            dateFormat = prefs.getString("date_format", "14 Jan 2025") ?: "14 Jan 2025",
            timeFormat = prefs.getString("time_format", "12-hour") ?: "12-hour",
            weekStartsOn = prefs.getString("week_starts_on", "Monday") ?: "Monday",
            calendarFirstDay = prefs.getString("calendar_first_day", "Monday") ?: "Monday",
            weightUnit = prefs.getString("weight_unit", "kg") ?: "kg",
            heightUnit = prefs.getString("height_unit", "cm") ?: "cm",
            waterUnit = prefs.getString("water_unit", "ml") ?: "ml",
            tempUnit = prefs.getString("temp_unit", "°C") ?: "°C",

            startPage = prefs.getString("start_page", "Home") ?: "Home",
            hapticFeedback = prefs.getBoolean("haptic_feedback", true),
            soundEffects = prefs.getBoolean("sound_effects", false),
            autoSave = prefs.getBoolean("auto_save", true),
            confirmBeforeDelete = prefs.getBoolean("confirm_before_delete", true),
            showTips = prefs.getBoolean("show_tips", true)
        )
    }

    fun saveSettings(context: Context, settings: AppSettings) {
        getSettingsPrefs(context).edit()
            .putString("theme_mode", settings.themeMode)
            .putString("accent_color_hex", settings.accentColorHex)
            .putFloat("text_size_multiplier", settings.textSizeMultiplier)
            .putString("dashboard_style", settings.dashboardStyle)
            .putBoolean("enable_animations", settings.enableAnimations)
            .putBoolean("reduce_motion", settings.reduceMotion)

            .putBoolean("notifications_enabled", settings.notificationsEnabled)
            .putString("default_reminder_time", settings.defaultReminderTime)
            .putString("quiet_hours_start", settings.quietHoursStart)
            .putString("quiet_hours_end", settings.quietHoursEnd)
            .putInt("snooze_duration_min", settings.snoozeDurationMinutes)
            .putBoolean("notify_habits", settings.notifyHabits)
            .putBoolean("notify_routines", settings.notifyRoutines)
            .putBoolean("notify_tasks", settings.notifyTasks)
            .putBoolean("notify_notes", settings.notifyNotes)
            .putBoolean("notify_water", settings.notifyWater)
            .putBoolean("notify_study", settings.notifyStudy)
            .putBoolean("notify_digital_wellbeing", settings.notifyDigitalWellbeing)

            .putString("app_language", settings.appLanguage)
            .putString("date_format", settings.dateFormat)
            .putString("time_format", settings.timeFormat)
            .putString("week_starts_on", settings.weekStartsOn)
            .putString("calendar_first_day", settings.calendarFirstDay)
            .putString("weight_unit", settings.weightUnit)
            .putString("height_unit", settings.heightUnit)
            .putString("water_unit", settings.waterUnit)
            .putString("temp_unit", settings.tempUnit)

            .putString("start_page", settings.startPage)
            .putBoolean("haptic_feedback", settings.hapticFeedback)
            .putBoolean("sound_effects", settings.soundEffects)
            .putBoolean("auto_save", settings.autoSave)
            .putBoolean("confirmBeforeDelete", settings.confirmBeforeDelete)
            .putBoolean("show_tips", settings.showTips)
            .apply()

        // Sync dark_theme to legacy prefs for backward compatibility with theme engine
        val isDark = when (settings.themeMode) {
            "Light" -> false
            else -> true
        }
        getProfilePrefs(context).edit().putBoolean("dark_theme", isDark).apply()
    }
}
