package com.yakhrevan.lifetrack

import android.content.Context
import java.util.Locale

/** Daily goals shared by the dashboard. Stored in the existing profile prefs. */
internal object Goals {
    const val DEFAULT_WATER_ML = 2500
    const val DEFAULT_STUDY_MIN = 240
    private const val PREFS = "lifetrack_profile"

    fun waterTarget(context: Context): Int = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("water_target", DEFAULT_WATER_ML).coerceIn(500, 10000)

    fun studyTarget(context: Context): Int = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("study_goal_min", DEFAULT_STUDY_MIN).coerceIn(15, 1440)

    fun save(context: Context, waterMl: Int, studyMin: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("water_target", waterMl.coerceIn(500, 10000))
            .putInt("study_goal_min", studyMin.coerceIn(15, 1440))
            .apply()
    }

    fun parseWater(raw: String): Int? = raw.trim().toIntOrNull()?.takeIf { it in 500..10000 }
    fun parseStudy(raw: String): Int? = raw.trim().toIntOrNull()?.takeIf { it in 15..1440 }

    fun litres(ml: Int): String = String.format(Locale.US, "%.1f L", ml / 1000f)

    fun hours(min: Int): String = when {
        min % 60 == 0 -> "${min / 60}h"
        min > 60 -> "${min / 60}h ${min % 60}m"
        else -> "${min}m"
    }
}