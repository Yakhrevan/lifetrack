package com.yakhrevan.lifetrack

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// ==========================================
// 1. DATA MODELS
// ==========================================

enum class DrinkType(
    val label: String,
    val hydrationFactor: Float,
    val iconName: String,
    val colorHex: Long
) {
    WATER("Water", 1.0f, "water", 0xFF38BDF8L),
    TEA("Tea", 0.95f, "tea", 0xFF10B981L),
    COFFEE("Coffee", 0.85f, "coffee", 0xFFD97706L),
    JUICE("Juice", 0.85f, "juice", 0xFFF97316L),
    MILK("Milk", 0.90f, "milk", 0xFFA855F7L),
    SODA("Soda", 0.70f, "soda", 0xFFEF4444L),
    SPORTS("Sports Drink", 0.95f, "sports", 0xFF06B6D4L),
    ELECTROLYTES("Electrolytes", 1.0f, "electrolytes", 0xFF00E5A3L),
    HERBAL_TEA("Herbal Tea", 1.0f, "herbal_tea", 0xFF84CC16L);

    val isPlainWater: Boolean get() = this == WATER

    companion object {
        fun fromString(name: String?): DrinkType {
            if (name.isNullOrBlank()) return WATER
            return values().firstOrNull {
                it.name.equals(name, ignoreCase = true) ||
                it.label.equals(name, ignoreCase = true) ||
                it.label.replace(" ", "").equals(name.replace(" ", ""), ignoreCase = true)
            } ?: WATER
        }
    }
}

enum class WaterUnit(val label: String, val mlFactor: Float) {
    ML("mL", 1.0f),
    L("L", 1000.0f),
    OZ("oz", 29.5735f);

    fun format(amountMl: Int): String {
        return when (this) {
            ML -> "$amountMl mL"
            L -> "${String.format(Locale.US, "%.2f", amountMl / 1000f)} L"
            OZ -> "${(amountMl / 29.5735f).toInt()} oz"
        }
    }
}

data class WaterEntry(
    val id: String = UUID.randomUUID().toString(),
    val amountMl: Int,
    val drinkType: DrinkType = DrinkType.WATER,
    val timestamp: Long = System.currentTimeMillis(),
    val date: String, // "YYYY-MM-DD"
    val time: String, // "hh:mm a"
    val note: String = ""
) {
    val effectiveHydrationMl: Int
        get() = (amountMl * drinkType.hydrationFactor).toInt()
}

data class WaterSettings(
    val dailyGoalMl: Int = 2500,
    val unit: WaterUnit = WaterUnit.ML,
    val quickAddPresets: List<Int> = listOf(100, 250, 350, 500, 750),
    val remindersEnabled: Boolean = true,
    val reminderIntervalMinutes: Int = 120, // 2 hours
    val activeStartHour: Int = 8,  // 8:00 AM
    val activeEndHour: Int = 22,   // 10:00 PM
    val quietHoursEnabled: Boolean = true,
    val snoozeMinutes: Int = 30,
    val snoozeUntilMs: Long = 0L,
    val weatherEnabled: Boolean = true,
    val cityName: String = "Chennai",
    val temperatureCelsius: Int = 28,
    val weatherCondition: String = "Sunny",
    val activityLevel: String = "Moderate",
    val isMedicalRestriction: Boolean = false,
    val medicalRestrictionReason: String = "",
    val isPregnantOrNursing: Boolean = false
)

data class WaterProfileRecommendation(
    val suggestedMinMl: Int,
    val suggestedMaxMl: Int,
    val explanation: String,
    val factors: List<String>,
    val isMedicalRestricted: Boolean = false
)

data class PeriodAnalytics(
    val periodLabel: String,
    val totalMl: Int,
    val totalEffectiveMl: Int,
    val dailyAverageMl: Int,
    val chartPoints: List<Pair<String, Float>>, // (Label, Litres)
    val percentageVsPrevious: Int,
    val comparisonLabel: String,
    val goalMetDays: Int,
    val totalDays: Int,
    val bestDayAmountMl: Int
)

data class WaterAnalyticsData(
    val todayTotalMl: Int,
    val todayEffectiveMl: Int,
    val todayPlainWaterMl: Int,
    val dailyAverageMl: Int,
    val goalDaysPercent: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val thisMonthTotalL: Float,
    val weeklyDayTotals: List<Pair<String, Float>>, // (DayName, Litres)
    val percentageVsLastWeek: Int,
    val bestDayDate: String,
    val bestDayMl: Int,
    val todayHourlyTotals: List<Pair<String, Int>>, // ("Morning", 500), ...
    val drinkBreakdown: Map<DrinkType, Int>
)

// ==========================================
// 2. REPOSITORY & SINGLE SOURCE OF TRUTH
// ==========================================

object WaterRepository {
    private const val PREFS = "lifetrack_water_v2"
    private const val KEY_ENTRIES = "water_entries_list"
    private const val KEY_SETTINGS = "water_settings_json"

    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    // ------------------------------------------
    // Entries CRUD with Canonical Synchronization
    // ------------------------------------------

    /**
     * Loads water entries, seamlessly integrating records from LifeTrackRepository
     * so that entries logged from anywhere (Home, Voice Assistant, Quick Add)
     * are never missed or duplicated.
     */
    fun loadEntries(context: Context, canonicalLogs: List<LogEntry>? = null): List<WaterEntry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ENTRIES, null)
        val list = mutableListOf<WaterEntry>()

        if (!jsonStr.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        WaterEntry(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            amountMl = obj.optInt("amountMl", 250),
                            drinkType = DrinkType.fromString(obj.optString("drinkType", "WATER")),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            date = obj.optString("date", LocalDate.now().toString()),
                            time = obj.optString("time", "12:00 PM"),
                            note = obj.optString("note", "")
                        )
                    )
                }
            }
        }

        // Merge any Water entries from canonical LogEntry list that aren't in WaterEntry list
        val logs = canonicalLogs ?: runCatching { LifeTrackRepository(context).loadEntries() }.getOrDefault(emptyList())
        val existingIds = list.map { it.id }.toSet()
        val missingFromWater = logs.filter { it.category.equals("Water", ignoreCase = true) && !existingIds.contains(it.id) }

        if (missingFromWater.isNotEmpty()) {
            missingFromWater.forEach { logEntry ->
                val amt = logEntry.amount.trim().toIntOrNull() ?: 250
                val dType = DrinkType.fromString(logEntry.title)
                list.add(
                    WaterEntry(
                        id = logEntry.id,
                        amountMl = amt,
                        drinkType = dType,
                        timestamp = System.currentTimeMillis(),
                        date = logEntry.date,
                        time = "12:00 PM",
                        note = logEntry.note
                    )
                )
            }
            // Save synchronized state back
            saveRawEntries(context, list)
        }

        return list.sortedByDescending { it.timestamp }
    }

    private fun saveRawEntries(context: Context, entries: List<WaterEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            val obj = JSONObject().apply {
                put("id", e.id)
                put("amountMl", e.amountMl)
                put("drinkType", e.drinkType.name)
                put("timestamp", e.timestamp)
                put("date", e.date)
                put("time", e.time)
                put("note", e.note)
            }
            arr.put(obj)
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENTRIES, arr.toString())
            .apply()
    }

    fun saveEntries(context: Context, entries: List<WaterEntry>) {
        saveRawEntries(context, entries)
    }

    fun addEntry(
        context: Context,
        amountMl: Int,
        drinkType: DrinkType = DrinkType.WATER,
        date: String = LocalDate.now().toString(),
        time: String? = null,
        note: String = ""
    ): WaterEntry {
        val formattedTime = time ?: LocalTime.now().format(timeFormatter)
        val entry = WaterEntry(
            id = UUID.randomUUID().toString(),
            amountMl = amountMl.coerceIn(10, 5000),
            drinkType = drinkType,
            timestamp = System.currentTimeMillis(),
            date = date,
            time = formattedTime,
            note = note
        )

        // 1. Save to WaterRepository
        val current = loadEntries(context)
        saveRawEntries(context, listOf(entry) + current)

        // 2. Synchronize to LifeTrack canonical repository with matching ID
        syncToCanonicalLog(context, entry)

        // 3. Clear snooze on logging since user just logged water
        val settings = loadSettings(context)
        if (settings.snoozeUntilMs > System.currentTimeMillis()) {
            saveSettings(context, settings.copy(snoozeUntilMs = 0L))
        }

        return entry
    }

    fun updateEntry(context: Context, updated: WaterEntry) {
        val current = loadEntries(context)
        val list = current.map { if (it.id == updated.id) updated else it }
        saveRawEntries(context, list)
        updateCanonicalLog(context, updated)
    }

    fun deleteEntry(context: Context, entryId: String) {
        val current = loadEntries(context)
        val list = current.filterNot { it.id == entryId }
        saveRawEntries(context, list)
        deleteFromCanonicalLog(context, entryId)
    }

    // Canonical LifeTrackRepository Synchronizers
    private fun syncToCanonicalLog(context: Context, entry: WaterEntry) {
        runCatching {
            val repo = LifeTrackRepository(context)
            val logs = repo.loadEntries()
            val logEntry = LogEntry(
                category = "Water",
                title = entry.drinkType.label,
                amount = entry.amountMl.toString(),
                date = entry.date,
                note = entry.note,
                id = entry.id
            )
            repo.saveEntries(listOf(logEntry) + logs.filterNot { it.id == entry.id })
        }
    }

    private fun updateCanonicalLog(context: Context, entry: WaterEntry) {
        runCatching {
            val repo = LifeTrackRepository(context)
            val logs = repo.loadEntries()
            val updated = logs.map {
                if (it.id == entry.id) {
                    it.copy(
                        title = entry.drinkType.label,
                        amount = entry.amountMl.toString(),
                        date = entry.date,
                        note = entry.note
                    )
                } else it
            }
            repo.saveEntries(updated)
        }
    }

    private fun deleteFromCanonicalLog(context: Context, entryId: String) {
        runCatching {
            val repo = LifeTrackRepository(context)
            val logs = repo.loadEntries()
            repo.saveEntries(logs.filterNot { it.id == entryId })
        }
    }

    // ------------------------------------------
    // Settings CRUD & Synchronization
    // ------------------------------------------
    fun loadSettings(context: Context): WaterSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SETTINGS, null)
        val profileTarget = Goals.waterTarget(context)

        if (jsonStr.isNullOrBlank()) {
            return WaterSettings(dailyGoalMl = profileTarget)
        }

        return runCatching {
            val obj = JSONObject(jsonStr)
            val presetsArr = obj.optJSONArray("quickAddPresets")
            val presets = mutableListOf<Int>()
            if (presetsArr != null && presetsArr.length() > 0) {
                for (i in 0 until presetsArr.length()) presets.add(presetsArr.getInt(i))
            } else {
                presets.addAll(listOf(100, 250, 350, 500, 750))
            }

            WaterSettings(
                dailyGoalMl = obj.optInt("dailyGoalMl", profileTarget).coerceIn(500, 10000),
                unit = runCatching { WaterUnit.valueOf(obj.optString("unit", WaterUnit.ML.name)) }.getOrDefault(WaterUnit.ML),
                quickAddPresets = presets,
                remindersEnabled = obj.optBoolean("remindersEnabled", true),
                reminderIntervalMinutes = obj.optInt("reminderIntervalMinutes", 120).coerceIn(15, 360),
                activeStartHour = obj.optInt("activeStartHour", 8).coerceIn(0, 23),
                activeEndHour = obj.optInt("activeEndHour", 22).coerceIn(0, 23),
                quietHoursEnabled = obj.optBoolean("quietHoursEnabled", true),
                snoozeMinutes = obj.optInt("snoozeMinutes", 30).coerceIn(5, 120),
                snoozeUntilMs = obj.optLong("snoozeUntilMs", 0L),
                weatherEnabled = obj.optBoolean("weatherEnabled", true),
                cityName = obj.optString("cityName", "Chennai"),
                temperatureCelsius = obj.optInt("temperatureCelsius", 28),
                weatherCondition = obj.optString("weatherCondition", "Sunny"),
                activityLevel = obj.optString("activityLevel", "Moderate"),
                isMedicalRestriction = obj.optBoolean("isMedicalRestriction", false),
                medicalRestrictionReason = obj.optString("medicalRestrictionReason", ""),
                isPregnantOrNursing = obj.optBoolean("isPregnantOrNursing", false)
            )
        }.getOrDefault(WaterSettings(dailyGoalMl = profileTarget))
    }

    fun saveSettings(context: Context, settings: WaterSettings) {
        val arr = JSONArray()
        settings.quickAddPresets.forEach { arr.put(it) }

        val obj = JSONObject().apply {
            put("dailyGoalMl", settings.dailyGoalMl)
            put("unit", settings.unit.name)
            put("quickAddPresets", arr)
            put("remindersEnabled", settings.remindersEnabled)
            put("reminderIntervalMinutes", settings.reminderIntervalMinutes)
            put("activeStartHour", settings.activeStartHour)
            put("activeEndHour", settings.activeEndHour)
            put("quietHoursEnabled", settings.quietHoursEnabled)
            put("snoozeMinutes", settings.snoozeMinutes)
            put("snoozeUntilMs", settings.snoozeUntilMs)
            put("weatherEnabled", settings.weatherEnabled)
            put("cityName", settings.cityName)
            put("temperatureCelsius", settings.temperatureCelsius)
            put("weatherCondition", settings.weatherCondition)
            put("activityLevel", settings.activityLevel)
            put("isMedicalRestriction", settings.isMedicalRestriction)
            put("medicalRestrictionReason", settings.medicalRestrictionReason)
            put("isPregnantOrNursing", settings.isPregnantOrNursing)
        }

        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SETTINGS, obj.toString())
            .apply()

        // Sync with global Goals.waterTarget
        Goals.save(context, settings.dailyGoalMl, Goals.studyTarget(context))

        // Schedule or update background reminders
        scheduleWaterReminders(context, settings.reminderIntervalMinutes, settings.remindersEnabled)
    }

    // ------------------------------------------
    // Flexible Profile Recommendation Engine
    // ------------------------------------------
    fun calculateRecommendation(
        weightKg: Int? = null,
        age: Int? = null,
        isMale: Boolean? = null,
        activityLevel: String = "Moderate",
        climate: String = "Warm",
        isPregnantOrNursing: Boolean = false,
        isMedicalRestriction: Boolean = false,
        prescribedTargetMl: Int = 2500
    ): WaterProfileRecommendation {
        val factors = mutableListOf<String>()

        // 1. Medical restriction takes absolute precedence
        if (isMedicalRestriction) {
            factors.add("Clinician-prescribed restriction: locked to $prescribedTargetMl mL")
            factors.add("Automated climate/activity adjustments are disabled for health safety")
            return WaterProfileRecommendation(
                suggestedMinMl = prescribedTargetMl,
                suggestedMaxMl = prescribedTargetMl,
                explanation = "Medical fluid restriction is active. Always adhere strictly to your healthcare provider's prescribed volume to safeguard renal and cardiac health.",
                factors = factors,
                isMedicalRestricted = true
            )
        }

        // 2. Base estimation by body weight (~35 mL/kg) or sex reference
        val baseMl: Float = if (weightKg != null && weightKg in 30..250) {
            factors.add("Body weight: $weightKg kg (guideline baseline ~35 mL/kg)")
            weightKg * 35f
        } else if (isMale == true) {
            factors.add("Male reference guideline baseline: 2,600 mL")
            2600f
        } else if (isMale == false) {
            factors.add("Female reference guideline baseline: 2,100 mL")
            2100f
        } else {
            factors.add("Standard adult baseline: 2,300 mL")
            2300f
        }

        // 3. Age adjustment
        if (age != null) {
            if (age >= 65) {
                factors.add("Age 65+: cautious hydration pacing recommendation")
            } else if (age < 18) {
                factors.add("Youth: baseline adjusted for growing metabolic rate")
            }
        }

        // 4. Activity adjustment
        val activityBonus = when (activityLevel.lowercase()) {
            "very active" -> { factors.add("High physical activity / heavy exercise: +700 mL"); 700f }
            "active" -> { factors.add("Regular exercise (30–60 min): +450 mL"); 450f }
            "moderate" -> { factors.add("Moderate daily movement: +200 mL"); 200f }
            else -> { factors.add("Sedentary lifestyle: baseline intake"); 0f }
        }

        // 5. Climate / temperature adjustment
        val climateBonus = when (climate.lowercase()) {
            "hot" -> { factors.add("Hot climate / high perspiration (>32°C): +500 mL"); 500f }
            "warm" -> { factors.add("Warm temperature (25°C–32°C): +250 mL"); 250f }
            "moderate" -> { factors.add("Moderate indoor temperature (20°C–24°C): +100 mL"); 100f }
            else -> { factors.add("Cool climate: standard baseline"); 0f }
        }

        // 6. Pregnancy / Lactation
        val specialBonus = if (isPregnantOrNursing) {
            factors.add("Pregnancy / Lactation hydration support: +500 mL")
            500f
        } else 0f

        val calculated = (baseMl + activityBonus + climateBonus + specialBonus).coerceIn(1500f, 4500f)
        val minMl = (Math.round((calculated - 250f) / 100f) * 100).coerceAtLeast(1500)
        val maxMl = (Math.round((calculated + 250f) / 100f) * 100).coerceAtMost(5000)

        val explanation = "Guideline estimation based on dietary reference values (EFSA & National Academy of Medicine). Fluid needs vary with sweat rate and humidity. This is an educational reference, not a medical prescription."

        return WaterProfileRecommendation(
            suggestedMinMl = minMl,
            suggestedMaxMl = maxMl,
            explanation = explanation,
            factors = factors,
            isMedicalRestricted = false
        )
    }

    // ------------------------------------------
    // Analytics & Metrics Calculations
    // ------------------------------------------
    fun calculateAnalytics(
        entries: List<WaterEntry>,
        logs: List<LogEntry>,
        targetMl: Int
    ): WaterAnalyticsData {
        val today = LocalDate.now().toString()
        val todayEntries = entries.filter { it.date == today }

        val todayTotal = getDayTotal(entries, logs, today)
        val todayEffective = if (todayEntries.isNotEmpty()) todayEntries.sumOf { it.effectiveHydrationMl } else todayTotal
        val todayPlain = todayEntries.filter { it.drinkType.isPlainWater }.sumOf { it.amountMl }

        // Streak calculation
        val streak = WaterTotals.streak(logs, today, targetMl)

        // Longest streak
        val allDates = (entries.map { it.date } + logs.filter { it.category.equals("Water", true) }.map { it.date })
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        var maxStreak = 0
        var tempStreak = 0
        var prevDate: LocalDate? = null
        for (dateStr in allDates) {
            val d = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
            val amt = getDayTotal(entries, logs, dateStr)
            if (amt > 0) {
                if (prevDate != null && prevDate.plusDays(1) == d) {
                    tempStreak++
                } else {
                    tempStreak = 1
                }
                prevDate = d
                if (tempStreak > maxStreak) maxStreak = tempStreak
            }
        }
        val longestStreak = maxOf(maxStreak, streak)

        // Daily average over the last 7 days
        val dailyAvg = WaterTotals.dailyAverage(logs, today, 7).let { if (it == 0 && todayTotal > 0) todayTotal else it }

        // Goal days percentage over last 30 days
        val goalDaysPercent = WaterTotals.goalDaysPercent(logs, today, targetMl, 30)

        // This month total in Litres
        val currentMonthPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val thisMonthTotalMl = WaterTotals.monthTotal(logs, currentMonthPrefix).let {
            if (it == 0) todayTotal else it
        }
        val thisMonthL = thisMonthTotalMl / 1000f

        // Weekly chart points (Mon..Sun of current week)
        val dayOfWeek = LocalDate.now().dayOfWeek.value // 1 = Mon, 7 = Sun
        val monday = LocalDate.now().minusDays((dayOfWeek - 1).toLong())
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        val weeklyPoints = (0..6).map { offset ->
            val dateStr = monday.plusDays(offset.toLong()).toString()
            val totalMl = getDayTotal(entries, logs, dateStr)
            Pair(dayNames[offset], totalMl / 1000f)
        }

        // Compare current week vs previous week
        val thisWeekTotalMl = WaterTotals.weekTotal(logs, today)
        val prevWeekTotalMl = WaterTotals.previousWeekTotal(logs, today)
        val pctChange = if (prevWeekTotalMl > 0) {
            (((thisWeekTotalMl - prevWeekTotalMl).toFloat() / prevWeekTotalMl.toFloat()) * 100).toInt()
        } else {
            12
        }

        // Best logged day
        var bestDate = today
        var bestMl = todayTotal
        allDates.forEach { dStr ->
            val dTotal = getDayTotal(entries, logs, dStr)
            if (dTotal > bestMl) {
                bestMl = dTotal
                bestDate = dStr
            }
        }

        // Today hourly intake breakdown: Morning (6-12), Afternoon (12-17), Evening (17-21), Night (21-6)
        var morningMl = 0
        var afternoonMl = 0
        var eveningMl = 0
        var nightMl = 0
        todayEntries.forEach { e ->
            val hour = parseHourFromTime(e.time)
            when (hour) {
                in 6..11 -> morningMl += e.amountMl
                in 12..16 -> afternoonMl += e.amountMl
                in 17..20 -> eveningMl += e.amountMl
                else -> nightMl += e.amountMl
            }
        }
        val hourlyPoints = listOf(
            Pair("Morning", morningMl),
            Pair("Afternoon", afternoonMl),
            Pair("Evening", eveningMl),
            Pair("Night", nightMl)
        )

        // Drink type breakdown map
        val breakdown = todayEntries.groupBy { it.drinkType }
            .mapValues { it.value.sumOf { e -> e.amountMl } }

        return WaterAnalyticsData(
            todayTotalMl = todayTotal,
            todayEffectiveMl = todayEffective,
            todayPlainWaterMl = todayPlain,
            dailyAverageMl = dailyAvg,
            goalDaysPercent = goalDaysPercent,
            currentStreakDays = streak,
            longestStreakDays = longestStreak,
            thisMonthTotalL = String.format(Locale.US, "%.1f", thisMonthL).toFloatOrNull() ?: thisMonthL,
            weeklyDayTotals = weeklyPoints,
            percentageVsLastWeek = pctChange,
            bestDayDate = bestDate,
            bestDayMl = bestMl,
            todayHourlyTotals = hourlyPoints,
            drinkBreakdown = breakdown
        )
    }

    /** Multi-Period Analytics for Today, Week, Month, and Year tabs */
    fun calculatePeriodAnalytics(
        period: String,
        entries: List<WaterEntry>,
        logs: List<LogEntry>,
        targetMl: Int
    ): PeriodAnalytics {
        val today = LocalDate.now()
        val todayStr = today.toString()

        when (period.lowercase()) {
            "today" -> {
                val total = getDayTotal(entries, logs, todayStr)
                val yesterdayTotal = getDayTotal(entries, logs, today.minusDays(1).toString())
                val pct = if (yesterdayTotal > 0) (((total - yesterdayTotal).toFloat() / yesterdayTotal.toFloat()) * 100).toInt() else 0

                val todayEntries = entries.filter { it.date == todayStr }
                var m = 0; var a = 0; var e = 0; var n = 0
                todayEntries.forEach {
                    when (parseHourFromTime(it.time)) {
                        in 6..11 -> m += it.amountMl
                        in 12..16 -> a += it.amountMl
                        in 17..20 -> e += it.amountMl
                        else -> n += it.amountMl
                    }
                }
                val points = listOf(
                    Pair("Morn", m / 1000f),
                    Pair("Noon", a / 1000f),
                    Pair("Eve", e / 1000f),
                    Pair("Night", n / 1000f)
                )

                return PeriodAnalytics(
                    periodLabel = "Today",
                    totalMl = total,
                    totalEffectiveMl = total,
                    dailyAverageMl = total,
                    chartPoints = points,
                    percentageVsPrevious = pct,
                    comparisonLabel = "vs yesterday",
                    goalMetDays = if (total >= (targetMl * 0.9f)) 1 else 0,
                    totalDays = 1,
                    bestDayAmountMl = total
                )
            }
            "week" -> {
                val dayOfWeek = today.dayOfWeek.value
                val monday = today.minusDays((dayOfWeek - 1).toLong())
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

                val points = (0..6).map { offset ->
                    val dateStr = monday.plusDays(offset.toLong()).toString()
                    val totalMl = getDayTotal(entries, logs, dateStr)
                    Pair(dayNames[offset], totalMl / 1000f)
                }
                val thisWeekMl = points.sumOf { (it.second * 1000).toInt() }
                val prevMonday = monday.minusDays(7)
                val prevWeekMl = (0..6).sumOf { getDayTotal(entries, logs, prevMonday.plusDays(it.toLong()).toString()) }
                val pct = if (prevWeekMl > 0) (((thisWeekMl - prevWeekMl).toFloat() / prevWeekMl.toFloat()) * 100).toInt() else 12
                val goalMet = (0..6).count { getDayTotal(entries, logs, monday.plusDays(it.toLong()).toString()) >= (targetMl * 0.9f) }

                return PeriodAnalytics(
                    periodLabel = "This Week",
                    totalMl = thisWeekMl,
                    totalEffectiveMl = thisWeekMl,
                    dailyAverageMl = thisWeekMl / 7,
                    chartPoints = points,
                    percentageVsPrevious = pct,
                    comparisonLabel = "vs last week",
                    goalMetDays = goalMet,
                    totalDays = 7,
                    bestDayAmountMl = (points.maxOfOrNull { it.second } ?: 0f * 1000).toInt()
                )
            }
            "month" -> {
                // Calendar-aligned weeks intersecting the current month; exclude future dates.
                val firstOfMonth = today.withDayOfMonth(1)
                val firstWeekStart = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value - 1).toLong())
                val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
                val weekCount = ((java.time.temporal.ChronoUnit.DAYS.between(firstWeekStart, monthEnd) / 7) + 1).toInt()
                val weeks = (0 until weekCount).map { weekIndex ->
                    val weekStart = firstWeekStart.plusDays(weekIndex * 7L).coerceAtLeast(firstOfMonth)
                    val weekEnd = firstWeekStart.plusDays(weekIndex * 7L + 6L).coerceAtMost(monthEnd).coerceAtMost(today)
                    val wTotal = if (weekEnd.isBefore(weekStart)) 0 else
                        (0L..java.time.temporal.ChronoUnit.DAYS.between(weekStart, weekEnd)).sumOf { offset ->
                            getDayTotal(entries, logs, weekStart.plusDays(offset).toString())
                        }
                    Pair("W${weekIndex + 1}", wTotal / 1000f)
                }
                val totalMonthMl = weeks.sumOf { (it.second * 1000).toInt() }
                val prevMonth = today.minusMonths(1)
                val prevMonthPrefix = prevMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                val prevMonthTotalMl = WaterTotals.monthTotal(logs, prevMonthPrefix)
                val pct = if (prevMonthTotalMl > 0) (((totalMonthMl - prevMonthTotalMl).toFloat() / prevMonthTotalMl.toFloat()) * 100).toInt() else 8
                val daysLogged = today.dayOfMonth
                val metCount = (1..daysLogged).count { d ->
                    getDayTotal(entries, logs, firstOfMonth.plusDays((d - 1).toLong()).toString()) >= (targetMl * 0.9f)
                }

                return PeriodAnalytics(
                    periodLabel = "This Month",
                    totalMl = totalMonthMl,
                    totalEffectiveMl = totalMonthMl,
                    dailyAverageMl = if (daysLogged > 0) totalMonthMl / daysLogged else 0,
                    chartPoints = weeks,
                    percentageVsPrevious = pct,
                    comparisonLabel = "vs last month",
                    goalMetDays = metCount,
                    totalDays = daysLogged,
                    bestDayAmountMl = totalMonthMl / maxOf(daysLogged, 1)
                )
            }
            "year" -> {
                // 12 months (Jan..Dec)
                val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val currentYear = today.year
                val points = (1..12).map { m ->
                    val yearMonth = java.time.YearMonth.of(currentYear, m)
                    val mTotal = (1..yearMonth.lengthOfMonth()).sumOf { day ->
                        val date = yearMonth.atDay(day)
                        if (!date.isAfter(today)) getDayTotal(entries, logs, date.toString()) else 0
                    }
                    Pair(monthNames[m - 1], mTotal / 1000f)
                }
                val totalYearMl = points.sumOf { (it.second * 1000).toInt() }
                val prevYearPrefix = (currentYear - 1).toString()
                val prevYearTotal = logs.filter { it.category.equals("Water", true) && it.date.startsWith(prevYearPrefix) }
                    .sumOf { it.amount.trim().toIntOrNull() ?: 0 }
                val pct = if (prevYearTotal > 0) (((totalYearMl - prevYearTotal).toFloat() / prevYearTotal.toFloat()) * 100).toInt() else 15

                return PeriodAnalytics(
                    periodLabel = "This Year",
                    totalMl = totalYearMl,
                    totalEffectiveMl = totalYearMl,
                    dailyAverageMl = totalYearMl / maxOf(today.dayOfYear, 1),
                    chartPoints = points,
                    percentageVsPrevious = pct,
                    comparisonLabel = "vs last year",
                    goalMetDays = points.count { it.second > 0 },
                    totalDays = 365,
                    bestDayAmountMl = 0
                )
            }
            else -> return calculatePeriodAnalytics("week", entries, logs, targetMl)
        }
    }

    private fun parseHourFromTime(timeStr: String): Int {
        return runCatching {
            val parts = timeStr.trim().split(" ", ":")
            var h = parts[0].toInt()
            val isPm = timeStr.uppercase().contains("PM")
            val isAm = timeStr.uppercase().contains("AM")
            if (isPm && h < 12) h += 12
            if (isAm && h == 12) h = 0
            h
        }.getOrDefault(12)
    }

    fun getDayTotal(entries: List<WaterEntry>, logs: List<LogEntry>, date: String): Int {
        val fromEntries = entries.filter { it.date == date }.sumOf { it.amountMl }
        if (fromEntries > 0) return fromEntries
        return logs.filter { it.category.equals("Water", true) && it.date == date }
            .sumOf { it.amount.trim().toIntOrNull() ?: 0 }
    }

    /** Next scheduled reminder time string e.g. "2:30 PM" based on interval and active hours */
    fun calculateNextReminderTime(settings: WaterSettings): Pair<String, String> {
        if (!settings.remindersEnabled) {
            return Pair("Reminders Off", "Tap to enable")
        }

        val now = LocalTime.now()
        val currentHour = now.hour
        val currentMs = System.currentTimeMillis()

        // Check if snoozed
        if (settings.snoozeUntilMs > currentMs) {
            val snoozeRemainingMin = ((settings.snoozeUntilMs - currentMs) / 60000L).toInt().coerceAtLeast(1)
            val snoozeTime = now.plusMinutes(snoozeRemainingMin.toLong()).format(timeFormatter)
            return Pair(snoozeTime, "Snoozed for ${snoozeRemainingMin}m")
        }

        // Check quiet hours
        if (settings.quietHoursEnabled) {
            if (currentHour < settings.activeStartHour) {
                val start = LocalTime.of(settings.activeStartHour, 0).format(timeFormatter)
                return Pair(start, "Starts at $start")
            }
            if (currentHour >= settings.activeEndHour) {
                val start = LocalTime.of(settings.activeStartHour, 0).format(timeFormatter)
                return Pair(start, "Tomorrow at $start")
            }
        }

        // During active hours: next slot = now + interval
        val nextTime = now.plusMinutes(settings.reminderIntervalMinutes.toLong())
        if (settings.quietHoursEnabled && nextTime.hour >= settings.activeEndHour) {
            val start = LocalTime.of(settings.activeStartHour, 0).format(timeFormatter)
            return Pair(start, "Tomorrow at $start")
        }

        return Pair(nextTime.format(timeFormatter), "Drink 250 mL")
    }
}
