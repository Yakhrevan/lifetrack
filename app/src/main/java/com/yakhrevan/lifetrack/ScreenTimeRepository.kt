package com.yakhrevan.lifetrack

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// ==========================================
// DATA MODELS & ENUMS
// ==========================================

enum class ScreenTimePeriod(val title: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    CUSTOM("Custom")
}

enum class AppCategory(val displayName: String, val color: Color) {
    SOCIAL("Social", Color(0xFFA855F7)),             // Purple
    ENTERTAINMENT("Entertainment", Color(0xFFF43F5E)), // Coral/Pink
    PRODUCTIVITY("Productivity", Color(0xFF38BDF8)),   // Cyan/Blue
    COMMUNICATION("Communication", Color(0xFF10B981)), // Emerald Green
    TOOLS("Tools", Color(0xFFF59E0B)),                 // Amber
    OTHERS("Others", Color(0xFF64748B))                // Slate
}

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeMs: Long,
    val percentage: Float,
    val category: AppCategory,
    val launchCount: Int = 0,
    val lastTimeUsed: Long = 0L,
    val iconBitmap: ImageBitmap? = null
) {
    val durationFormatted: String
        get() {
            val totalMinutes = totalTimeMs / 60000
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            return when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                hours > 0 -> "${hours}h"
                mins > 0 -> "${mins}m"
                totalTimeMs > 0 -> "<1m"
                else -> "0m"
            }
        }
}

data class HourlyUsage(
    val hour: Int, // 0..23
    val timeMinutes: Int,
    val label: String
)

data class CategoryUsage(
    val category: AppCategory,
    val timeMinutes: Int,
    val percentage: Float,
    val apps: List<AppUsageInfo>
) {
    val durationFormatted: String
        get() {
            val hours = timeMinutes / 60
            val mins = timeMinutes % 60
            return when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                hours > 0 -> "${hours}h"
                else -> "${mins}m"
            }
        }
}

data class DailyTrendPoint(
    val dayOfWeek: String, // Mon, Tue...
    val date: LocalDate,
    val thisPeriodMinutes: Int,
    val previousPeriodMinutes: Int,
    val categoryBreakdown: Map<AppCategory, Int> = emptyMap()
)

data class TimelineEvent(
    val timeLabel: String,
    val appName: String,
    val packageName: String,
    val durationMinutes: Int,
    val iconBitmap: ImageBitmap? = null
)

data class FocusSession(
    val id: String,
    val timestamp: Long,
    val durationMinutes: Int,
    val targetMinutes: Int,
    val completed: Boolean
)

data class RemindersConfig(
    val breakRemindersEnabled: Boolean = true,
    val reminderIntervalMinutes: Int = 50,
    val breakDurationMinutes: Int = 10,
    val notificationStyle: String = "Standard", // Standard, Gentle, Silent
    val bedtimeEnabled: Boolean = false,
    val bedtimeHour: Int = 23,
    val bedtimeMinute: Int = 0
)

data class AppLimit(
    val packageName: String,
    val appName: String,
    val limitMinutes: Int,
    val enabled: Boolean
)

// ==========================================
// REPOSITORY & USAGE STATS HELPER
// ==========================================

object ScreenTimeRepository {

    private const val PREFS_NAME = "lifetrack_screen_time_prefs"
    private const val FOCUS_PREFS_NAME = "lifetrack_focus_prefs"
    private val iconCache = mutableMapOf<String, ImageBitmap?>()

    /**
     * Check if Android Usage Access permission is granted.
     */
    fun hasUsageAccessPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Open Android system Usage Access settings page.
     */
    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {}
        }
    }

    /**
     * Calculate time bounds for a given period.
     */
    fun getTimeBounds(period: ScreenTimePeriod, customStart: LocalDate? = null, customEnd: LocalDate? = null): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()

        return when (period) {
            ScreenTimePeriod.TODAY -> {
                val start = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                val end = now.atZone(zone).toInstant().toEpochMilli()
                Pair(start, end)
            }
            ScreenTimePeriod.THIS_WEEK -> {
                val startOfWeek = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val start = startOfWeek.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = now.atZone(zone).toInstant().toEpochMilli()
                Pair(start, end)
            }
            ScreenTimePeriod.THIS_MONTH -> {
                val startOfMonth = now.toLocalDate().with(TemporalAdjusters.firstDayOfMonth())
                val start = startOfMonth.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = now.atZone(zone).toInstant().toEpochMilli()
                Pair(start, end)
            }
            ScreenTimePeriod.CUSTOM -> {
                val s = customStart ?: now.toLocalDate().minusDays(7)
                val e = customEnd ?: now.toLocalDate()
                val start = s.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = e.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                Pair(start, end)
            }
        }
    }

    /**
     * Calculate previous comparable period bounds.
     */
    fun getPreviousTimeBounds(period: ScreenTimePeriod): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()

        return when (period) {
            ScreenTimePeriod.TODAY -> {
                val yesterday = now.toLocalDate().minusDays(1)
                val start = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = yesterday.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                Pair(start, end)
            }
            ScreenTimePeriod.THIS_WEEK -> {
                val startOfLastWeek = now.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .minusWeeks(1)
                val endOfLastWeek = startOfLastWeek.plusDays(6)
                val start = startOfLastWeek.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = endOfLastWeek.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                Pair(start, end)
            }
            ScreenTimePeriod.THIS_MONTH -> {
                val startOfLastMonth = now.toLocalDate().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth())
                val endOfLastMonth = startOfLastMonth.with(TemporalAdjusters.lastDayOfMonth())
                val start = startOfLastMonth.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = endOfLastMonth.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                Pair(start, end)
            }
            ScreenTimePeriod.CUSTOM -> {
                val diffDays = 7L
                val start = now.minusDays(diffDays * 2).atZone(zone).toInstant().toEpochMilli()
                val end = now.minusDays(diffDays).atZone(zone).toInstant().toEpochMilli()
                Pair(start, end)
            }
        }
    }

    /**
     * Query and aggregate app usage for a given time window using UsageStatsManager.
     */
    fun queryAppUsage(context: Context, startTime: Long, endTime: Long): List<AppUsageInfo> {
        if (!hasUsageAccessPermission(context)) return emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val pm = context.packageManager
        val statsMap = try {
            usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        } catch (_: Exception) {
            emptyMap<String, UsageStats>()
        }

        if (statsMap.isEmpty()) return emptyList()

        val rawList = mutableListOf<AppUsageInfo>()
        var totalTimeOverall = 0L

        // System packages to ignore/filter out for cleaner app overview
        val ignoredPrefixes = listOf(
            "com.android.systemui",
            "com.google.android.inputmethod",
            "com.android.keyguard"
        )

        for ((packageName, stats) in statsMap) {
            val totalTime = stats.totalTimeInForeground
            if (totalTime < 1000L) continue // ignore under 1 second

            if (ignoredPrefixes.any { packageName.startsWith(it) }) continue

            var appName = packageName
            var category = classifyApp(packageName, null)
            var icon: ImageBitmap? = iconCache[packageName]

            try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }

                appName = pm.getApplicationLabel(appInfo).toString()
                category = classifyApp(packageName, appInfo)

                if (icon == null) {
                    val drawable = pm.getApplicationIcon(appInfo)
                    icon = drawableToBitmap(drawable)
                    iconCache[packageName] = icon
                }
            } catch (_: Exception) {
                val parts = packageName.split(".")
                appName = parts.lastOrNull()?.replaceFirstChar { it.uppercase() } ?: packageName
            }

            totalTimeOverall += totalTime
            rawList.add(
                AppUsageInfo(
                    packageName = packageName,
                    appName = appName,
                    totalTimeMs = totalTime,
                    percentage = 0f,
                    category = category,
                    lastTimeUsed = stats.lastTimeUsed,
                    iconBitmap = icon
                )
            )
        }

        val sorted = rawList.sortedByDescending { it.totalTimeMs }
        return sorted.map { app ->
            val pct = if (totalTimeOverall > 0) {
                (app.totalTimeMs.toFloat() / totalTimeOverall) * 100f
            } else 0f
            app.copy(percentage = pct)
        }
    }

    /**
     * Query hourly breakdown for a given date (0h to 23h).
     */
    fun queryHourlyUsage(context: Context, date: LocalDate): List<HourlyUsage> {
        val hourlyMinutes = IntArray(24) { 0 }
        if (!hasUsageAccessPermission(context)) {
            return (0..23).map { HourlyUsage(it, 0, formatHourLabel(it)) }
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return (0..23).map { HourlyUsage(it, 0, formatHourLabel(it)) }

        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = date.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

        try {
            val events = usageStatsManager.queryEvents(startOfDay, endOfDay)
            val event = UsageEvents.Event()

            var lastResumedTime = 0L
            var currentPackage: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val eventTime = event.timeStamp
                val hour = LocalDateTime.ofInstant(Instant.ofEpochMilli(eventTime), zone).hour.coerceIn(0, 23)

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        lastResumedTime = eventTime
                        currentPackage = event.packageName
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (lastResumedTime > 0 && currentPackage == event.packageName) {
                            val durationMinutes = ((eventTime - lastResumedTime) / 60000).toInt().coerceIn(0, 60)
                            hourlyMinutes[hour] = (hourlyMinutes[hour] + durationMinutes).coerceAtMost(60)
                            lastResumedTime = 0L
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return (0..23).map { hour ->
            HourlyUsage(
                hour = hour,
                timeMinutes = hourlyMinutes[hour],
                label = formatHourLabel(hour)
            )
        }
    }

    /**
     * Query supporting metrics (Unlocks, Notifications, App Launches).
     */
    fun querySystemMetrics(context: Context, startTime: Long, endTime: Long): Triple<Int, Int, Int> {
        if (!hasUsageAccessPermission(context)) return Triple(0, 0, 0)

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return Triple(0, 0, 0)

        var screenUnlocks = 0
        var appLaunches = 0

        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> appLaunches++
                    UsageEvents.Event.SCREEN_INTERACTIVE, UsageEvents.Event.KEYGUARD_HIDDEN -> screenUnlocks++
                }
            }
        } catch (_: Exception) {}

        // Android UsageStats does not expose notification totals; never estimate them.
        return Triple(screenUnlocks.coerceAtLeast(0), 0, appLaunches.coerceAtLeast(0))
    }

    /**
     * Extract timeline events for a given date.
     */
    fun queryTimelineEvents(context: Context, date: LocalDate): List<TimelineEvent> {
        if (!hasUsageAccessPermission(context)) return emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val pm = context.packageManager
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = date.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

        val result = mutableListOf<TimelineEvent>()

        try {
            val events = usageStatsManager.queryEvents(startOfDay, endOfDay)
            val event = UsageEvents.Event()

            var lastResumedTime = 0L
            var currentPackage: String? = null

            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val eventTime = event.timeStamp

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        lastResumedTime = eventTime
                        currentPackage = event.packageName
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (lastResumedTime > 0 && currentPackage == event.packageName) {
                            val durationMins = ((eventTime - lastResumedTime) / 60000).toInt()
                            if (durationMins >= 1) {
                                val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastResumedTime), zone)
                                val label = dateTime.format(timeFormatter)

                                var appName = currentPackage ?: ""
                                try {
                                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        pm.getApplicationInfo(currentPackage ?: "", PackageManager.ApplicationInfoFlags.of(0))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        pm.getApplicationInfo(currentPackage ?: "", 0)
                                    }
                                    appName = pm.getApplicationLabel(appInfo).toString()
                                } catch (_: Exception) {}

                                result.add(
                                    TimelineEvent(
                                        timeLabel = label,
                                        appName = appName,
                                        packageName = currentPackage ?: "",
                                        durationMinutes = durationMins,
                                        iconBitmap = iconCache[currentPackage ?: ""]
                                    )
                                )
                            }
                            lastResumedTime = 0L
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return result.takeLast(30).reversed()
    }

    /**
     * Group apps into categories and compute aggregate durations.
     */
    fun aggregateCategories(apps: List<AppUsageInfo>): List<CategoryUsage> {
        val totalMs = apps.sumOf { it.totalTimeMs }
        val map = apps.groupBy { it.category }

        return AppCategory.values().map { cat ->
            val catApps = map[cat] ?: emptyList()
            val catTimeMs = catApps.sumOf { it.totalTimeMs }
            val catMinutes = (catTimeMs / 60000).toInt()
            val pct = if (totalMs > 0) (catTimeMs.toFloat() / totalMs) * 100f else 0f
            CategoryUsage(
                category = cat,
                timeMinutes = catMinutes,
                percentage = pct,
                apps = catApps
            )
        }.sortedByDescending { it.timeMinutes }
    }

    /**
     * Query weekly trend data points comparing current week vs previous week.
     */
    fun queryWeeklyTrend(context: Context): List<DailyTrendPoint> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val startOfThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        return days.mapIndexed { index, dayName ->
            val dayDate = startOfThisWeek.plusDays(index.toLong())
            val prevDayDate = dayDate.minusWeeks(1)

            val dayStart = dayDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = dayDate.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
            val prevStart = prevDayDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val prevEnd = prevDayDate.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

            val thisApps = if (dayDate.isAfter(today)) emptyList() else queryAppUsage(context, dayStart, dayEnd)
            val prevApps = queryAppUsage(context, prevStart, prevEnd)

            val thisMins = (thisApps.sumOf { it.totalTimeMs } / 60000).toInt()
            val prevMins = (prevApps.sumOf { it.totalTimeMs } / 60000).toInt()

            val catBreakdown = thisApps.groupBy { it.category }
                .mapValues { (_, list) -> (list.sumOf { it.totalTimeMs } / 60000).toInt() }

            DailyTrendPoint(
                dayOfWeek = dayName,
                date = dayDate,
                thisPeriodMinutes = thisMins,
                previousPeriodMinutes = prevMins,
                categoryBreakdown = catBreakdown
            )
        }
    }

    private fun classifyApp(packageName: String, appInfo: ApplicationInfo?): AppCategory {
        val lower = packageName.lowercase()
        when {
            lower.contains("instagram") || lower.contains("twitter") || lower.contains("reddit") ||
            lower.contains("facebook") || lower.contains("tiktok") || lower.contains("snapchat") ||
            lower.contains("linkedin") || lower.contains("pinterest") -> return AppCategory.SOCIAL

            lower.contains("youtube") || lower.contains("netflix") || lower.contains("spotify") ||
            lower.contains("twitch") || lower.contains("hulu") || lower.contains("primevideo") ||
            lower.contains("disney") || lower.contains("music") || lower.contains("game") -> return AppCategory.ENTERTAINMENT

            lower.contains("whatsapp") || lower.contains("telegram") || lower.contains("messenger") ||
            lower.contains("discord") || lower.contains("signal") || lower.contains("wechat") ||
            lower.contains("slack") || lower.contains("teams") || lower.contains("skype") -> return AppCategory.COMMUNICATION

            lower.contains("docs") || lower.contains("sheets") || lower.contains("notion") ||
            lower.contains("keep") || lower.contains("evernote") || lower.contains("trello") ||
            lower.contains("tasks") || lower.contains("calendar") || lower.contains("todoist") ||
            lower.contains("notes") || lower.contains("lifetrack") -> return AppCategory.PRODUCTIVITY

            lower.contains("chrome") || lower.contains("browser") || lower.contains("calculator") ||
            lower.contains("camera") || lower.contains("gallery") || lower.contains("files") ||
            lower.contains("settings") || lower.contains("clock") || lower.contains("maps") -> return AppCategory.TOOLS
        }

        if (appInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
                ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.ENTERTAINMENT
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
                ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.TOOLS
            }
        }

        return AppCategory.OTHERS
    }

    private fun drawableToBitmap(drawable: Drawable): ImageBitmap? {
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap.asImageBitmap()
            }
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun formatHourLabel(hour: Int): String {
        return when (hour) {
            0 -> "12 AM"
            6 -> "6 AM"
            12 -> "12 PM"
            18 -> "6 PM"
            23 -> "11 PM"
            else -> ""
        }
    }

    // ==========================================
    // PREFERENCES: GOALS, LIMITS, REMINDERS, FOCUS
    // ==========================================

    fun getDailyGoalMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("daily_goal_minutes", 360)
    }

    fun setDailyGoalMinutes(context: Context, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("daily_goal_minutes", minutes.coerceIn(30, 1440)).apply()
    }

    fun loadRemindersConfig(context: Context): RemindersConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return RemindersConfig(
            breakRemindersEnabled = prefs.getBoolean("break_reminders_enabled", true),
            reminderIntervalMinutes = prefs.getInt("reminder_interval", 50),
            breakDurationMinutes = prefs.getInt("break_duration", 10),
            notificationStyle = prefs.getString("notification_style", "Standard") ?: "Standard",
            bedtimeEnabled = prefs.getBoolean("bedtime_enabled", false),
            bedtimeHour = prefs.getInt("bedtime_hour", 23),
            bedtimeMinute = prefs.getInt("bedtime_minute", 0)
        )
    }

    fun saveRemindersConfig(context: Context, config: RemindersConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("break_reminders_enabled", config.breakRemindersEnabled)
            .putInt("reminder_interval", config.reminderIntervalMinutes)
            .putInt("break_duration", config.breakDurationMinutes)
            .putString("notification_style", config.notificationStyle)
            .putBoolean("bedtime_enabled", config.bedtimeEnabled)
            .putInt("bedtime_hour", config.bedtimeHour)
            .putInt("bedtime_minute", config.bedtimeMinute)
            .apply()
    }

    fun loadAppLimits(context: Context): List<AppLimit> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("app_limits_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<AppLimit>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AppLimit(
                        packageName = obj.getString("package"),
                        appName = obj.getString("name"),
                        limitMinutes = obj.getInt("limit"),
                        enabled = obj.optBoolean("enabled", true)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAppLimits(context: Context, limits: List<AppLimit>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        limits.forEach {
            arr.put(
                JSONObject().apply {
                    put("package", it.packageName)
                    put("name", it.appName)
                    put("limit", it.limitMinutes)
                    put("enabled", it.enabled)
                }
            )
        }
        prefs.edit().putString("app_limits_json", arr.toString()).apply()
    }

    fun loadFocusSessions(context: Context): List<FocusSession> {
        val prefs = context.getSharedPreferences(FOCUS_PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("focus_sessions", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<FocusSession>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    FocusSession(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        durationMinutes = obj.getInt("duration"),
                        targetMinutes = obj.getInt("target"),
                        completed = obj.getBoolean("completed")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFocusSession(context: Context, session: FocusSession) {
        val current = loadFocusSessions(context).toMutableList()
        current.add(0, session)
        val prefs = context.getSharedPreferences(FOCUS_PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        current.take(50).forEach {
            arr.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("timestamp", it.timestamp)
                    put("duration", it.durationMinutes)
                    put("target", it.targetMinutes)
                    put("completed", it.completed)
                }
            )
        }
        prefs.edit().putString("focus_sessions", arr.toString()).apply()
    }


}
