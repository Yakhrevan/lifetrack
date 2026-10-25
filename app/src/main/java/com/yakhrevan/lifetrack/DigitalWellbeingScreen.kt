package com.yakhrevan.lifetrack

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

// ==========================================
// 1. DESIGN SYSTEM & COLOR PALETTE
// ==========================================
private val DarkBg         = Color(0xFF070B14)
private val CardBg         = Color(0xFF0E1626)
private val CardInner      = Color(0xFF141F33)
private val CardBorder     = Color(0x1AFFFFFF)
private val TextWhite      = Color(0xFFF1F5F9)
private val TextMuted      = Color(0xFF8896AB)
private val PurplePrimary  = Color(0xFFA855F7)
private val PurpleLight    = Color(0xFFC084FC)
private val VioletBrand    = Color(0xFF6366F1)
private val BlueAccent     = Color(0xFF38BDF8)
private val CyanBrand      = Color(0xFF06B6D4)
private val EmeraldAccent  = Color(0xFF10B981)
private val CoralAccent    = Color(0xFFF43F5E)
private val AmberWarning   = Color(0xFFF59E0B)

// Navigation state enum for the 10 screens
enum class WellbeingSubScreen {
    OVERVIEW,       // Screen 1: Digital Wellbeing Overview
    APP_USAGE,      // Screen 2: App Usage Details
    APP_DETAIL,     // Screen 3: Individual App Analytics
    CATEGORIES,     // Screen 4: Categories & Insights
    FOCUS_MODE,     // Screen 5: Focus Mode
    USAGE_GOALS,    // Screen 6: Usage Goals
    REMINDERS,      // Screen 7: Reminders
    TRENDS,         // Screen 8: Screen Time Trends
    DAILY_TIMELINE, // Screen 9: Daily Usage Timeline
    SETTINGS        // Screen 10: Settings & Privacy
}

// ==========================================
// 2. MAIN COMPOSABLE: DigitalWellbeingScreen
// ==========================================
@Composable
fun DigitalWellbeingScreen(
    onNavigate: (String) -> Unit = {},
    onNavigateHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Screen navigation state
    var currentScreen by remember { mutableStateOf(WellbeingSubScreen.OVERVIEW) }
    var selectedAppPackage by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    // Usage access permission state (rechecked on resume)
    var hasPermission by remember { mutableStateOf(ScreenTimeRepository.hasUsageAccessPermission(context)) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ScreenTimeRepository.hasUsageAccessPermission(context)
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Selected period (Today, This Week, This Month, Custom)
    var selectedPeriod by remember { mutableStateOf(ScreenTimePeriod.TODAY) }
    var customStartDate by remember { mutableStateOf(LocalDate.now().minusDays(6)) }
    var customEndDate by remember { mutableStateOf(LocalDate.now()) }

    // Explicit custom dates are used consistently by app lists and comparisons.
    val (startTime, endTime) = remember(selectedPeriod, customStartDate, customEndDate, refreshTrigger) {
        ScreenTimeRepository.getTimeBounds(
            selectedPeriod,
            if (selectedPeriod == ScreenTimePeriod.CUSTOM) customStartDate else null,
            if (selectedPeriod == ScreenTimePeriod.CUSTOM) customEndDate else null
        )
    }

    val (prevStartTime, prevEndTime) = remember(selectedPeriod, customStartDate, customEndDate, refreshTrigger) {
        if (selectedPeriod == ScreenTimePeriod.CUSTOM) {
            val daysInclusive = (java.time.temporal.ChronoUnit.DAYS.between(customStartDate, customEndDate) + 1).coerceAtLeast(1)
            val previousEnd = customStartDate.minusDays(1)
            val zone = java.time.ZoneId.systemDefault()
            Pair(
                previousEnd.minusDays(daysInclusive - 1).atStartOfDay(zone).toInstant().toEpochMilli(),
                previousEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            )
        } else ScreenTimeRepository.getPreviousTimeBounds(selectedPeriod)
    }

    val appUsageList = remember(startTime, endTime, refreshTrigger, hasPermission) {
        ScreenTimeRepository.queryAppUsage(context, startTime, endTime)
    }

    val prevAppUsageList = remember(prevStartTime, prevEndTime, refreshTrigger, hasPermission) {
        ScreenTimeRepository.queryAppUsage(context, prevStartTime, prevEndTime)
    }

    val selectedDayBounds = remember(selectedDate) {
        val zone = java.time.ZoneId.systemDefault()
        Pair(
            selectedDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }
    val selectedDayApps = remember(selectedDate, refreshTrigger, hasPermission) {
        ScreenTimeRepository.queryAppUsage(context, selectedDayBounds.first, selectedDayBounds.second)
    }

    val hourlyUsageList = remember(selectedDate, refreshTrigger, hasPermission) {
        ScreenTimeRepository.queryHourlyUsage(context, selectedDate)
    }

    val systemMetrics = remember(startTime, endTime, refreshTrigger, hasPermission) {
        ScreenTimeRepository.querySystemMetrics(context, startTime, endTime)
    }

    val categoriesList = remember(appUsageList) {
        ScreenTimeRepository.aggregateCategories(appUsageList)
    }

    val weeklyTrends = remember(refreshTrigger, hasPermission) {
        ScreenTimeRepository.queryWeeklyTrend(context)
    }

    val timelineEvents = remember(selectedDate, refreshTrigger, hasPermission) {
        ScreenTimeRepository.queryTimelineEvents(context, selectedDate)
    }

    // Preferences & Goals
    var dailyGoalMinutes by remember { mutableIntStateOf(ScreenTimeRepository.getDailyGoalMinutes(context)) }
    var remindersConfig by remember { mutableStateOf(ScreenTimeRepository.loadRemindersConfig(context)) }
    var appLimits by remember { mutableStateOf(ScreenTimeRepository.loadAppLimits(context)) }

    // Focus Session state
    var focusSessions by remember { mutableStateOf(ScreenTimeRepository.loadFocusSessions(context)) }

    // Dialogs
    var showGoalDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        when (currentScreen) {
            WellbeingSubScreen.OVERVIEW -> {
                DigitalWellbeingOverviewContent(
                    hasPermission = hasPermission,
                    selectedPeriod = selectedPeriod,
                    onSelectPeriod = { selectedPeriod = it },
                    customStartDate = customStartDate,
                    customEndDate = customEndDate,
                    onCustomStartDateChange = { customStartDate = it },
                    onCustomEndDateChange = { customEndDate = it },
                    appUsageList = appUsageList,
                    prevAppUsageList = prevAppUsageList,
                    hourlyUsageList = hourlyUsageList,
                    systemMetrics = systemMetrics,
                    dailyGoalMinutes = dailyGoalMinutes,
                    onOpenSettings = { currentScreen = WellbeingSubScreen.SETTINGS },
                    onOpenAppUsage = { currentScreen = WellbeingSubScreen.APP_USAGE },
                    onOpenAppDetail = { pkg ->
                        selectedAppPackage = pkg
                        currentScreen = WellbeingSubScreen.APP_DETAIL
                    },
                    onOpenCategories = { currentScreen = WellbeingSubScreen.CATEGORIES },
                    onOpenFocusMode = { currentScreen = WellbeingSubScreen.FOCUS_MODE },
                    onOpenGoals = { currentScreen = WellbeingSubScreen.USAGE_GOALS },
                    onOpenReminders = { currentScreen = WellbeingSubScreen.REMINDERS },
                    onOpenTrends = { currentScreen = WellbeingSubScreen.TRENDS },
                    onOpenTimeline = { currentScreen = WellbeingSubScreen.DAILY_TIMELINE },
                    onRequestPermission = { ScreenTimeRepository.openUsageAccessSettings(context) },
                    onNavigateHome = onNavigateHome
                )
            }
            WellbeingSubScreen.APP_USAGE -> {
                AppUsageDetailsContent(
                    hasPermission = hasPermission,
                    selectedPeriod = selectedPeriod,
                    onSelectPeriod = { selectedPeriod = it },
                    apps = appUsageList,
                    onSelectApp = { pkg ->
                        selectedAppPackage = pkg
                        currentScreen = WellbeingSubScreen.APP_DETAIL
                    },
                    onBack = { currentScreen = WellbeingSubScreen.OVERVIEW },
                    onRequestPermission = { ScreenTimeRepository.openUsageAccessSettings(context) }
                )
            }
            WellbeingSubScreen.APP_DETAIL -> {
                val app = appUsageList.find { it.packageName == selectedAppPackage }
                    ?: appUsageList.firstOrNull()
                IndividualAppAnalyticsContent(
                    app = app,
                    onBack = { currentScreen = WellbeingSubScreen.APP_USAGE }
                )
            }
            WellbeingSubScreen.CATEGORIES -> {
                CategoriesAnalyticsContent(
                    selectedPeriod = selectedPeriod,
                    onSelectPeriod = { selectedPeriod = it },
                    categories = categoriesList,
                    onSelectApp = { pkg ->
                        selectedAppPackage = pkg
                        currentScreen = WellbeingSubScreen.APP_DETAIL
                    },
                    onBack = { currentScreen = WellbeingSubScreen.OVERVIEW }
                )
            }
            WellbeingSubScreen.FOCUS_MODE -> {
                FocusModeContent(
                    sessions = focusSessions,
                    onSessionCompleted = { session ->
                        ScreenTimeRepository.saveFocusSession(context, session)
                        focusSessions = ScreenTimeRepository.loadFocusSessions(context)
                    },
                    onBack = { currentScreen = WellbeingSubScreen.OVERVIEW }
                )
            }
            WellbeingSubScreen.USAGE_GOALS -> {
                UsageGoalsContent(
                    dailyGoalMinutes = dailyGoalMinutes,
                    todayMinutes = (appUsageList.sumOf { it.totalTimeMs } / 60000).toInt(),
                    weeklyTrends = weeklyTrends,
                    appLimits = appLimits,
                    onEditDailyGoal = { showGoalDialog = true },
                    onToggleAppLimit = { limit ->
                        val updated = appLimits.map { if (it.packageName == limit.packageName) it.copy(enabled = !it.enabled) else it }
                        appLimits = updated
                        ScreenTimeRepository.saveAppLimits(context, updated)
                    },
                    onBack = { currentScreen = WellbeingSubScreen.OVERVIEW }
                )
            }
            WellbeingSubScreen.REMINDERS -> {
                RemindersContent(
                    config = remindersConfig,
                    onUpdateConfig = { updated ->
                        remindersConfig = updated
                        ScreenTimeRepository.saveRemindersConfig(context, updated)
                        scheduleScreenTimeReminders(context, updated.reminderIntervalMinutes, updated.breakRemindersEnabled)
                    },
                    onBack = { currentScreen = WellbeingSubScreen.OVERVIEW }
                )
            }
            WellbeingSubScreen.TRENDS -> {
                ScreenTimeTrendsContent(
                    selectedPeriod = selectedPeriod,
                    onSelectPeriod = { selectedPeriod = it },
                    weeklyTrends = weeklyTrends,
                    onBack = { currentScreen = WellbeingSubScreen.OVERVIEW }
                )
            }
            WellbeingSubScreen.DAILY_TIMELINE -> {
                DailyUsageTimelineContent(
                    date = selectedDate,
                    onSelectDate = { selectedDate = it },
                    totalMinutes = (selectedDayApps.sumOf { it.totalTimeMs } / 60000).toInt(),
                    dailyGoalMinutes = dailyGoalMinutes,
                    topApps = selectedDayApps.take(5),
                    timelineEvents = timelineEvents,
                    onSelectApp = { pkg ->
                        selectedAppPackage = pkg
                        currentScreen = WellbeingSubScreen.APP_DETAIL
                    },
                    onBack = { currentScreen = WellbeingSubScreen.OVERVIEW }
                )
            }
            WellbeingSubScreen.SETTINGS -> {
                SettingsPrivacyContent(
                    hasPermission = hasPermission,
                    onRequestPermission = { ScreenTimeRepository.openUsageAccessSettings(context) },
                    onRefreshData = { refreshTrigger++ },
                    onOpenGoals = { currentScreen = WellbeingSubScreen.USAGE_GOALS },
                    onOpenReminders = { currentScreen = WellbeingSubScreen.REMINDERS },
                    onBack = { currentScreen = WellbeingSubScreen.OVERVIEW }
                )
            }
        }

        // Edit Daily Goal Dialog
        if (showGoalDialog) {
            EditDailyGoalDialog(
                currentMinutes = dailyGoalMinutes,
                onDismiss = { showGoalDialog = false },
                onSave = { newMins ->
                    dailyGoalMinutes = newMins
                    ScreenTimeRepository.setDailyGoalMinutes(context, newMins)
                    showGoalDialog = false
                }
            )
        }
    }
}

// ==========================================
// 3. SCREEN 1: DIGITAL WELLBEING OVERVIEW
// ==========================================
@Composable
private fun DigitalWellbeingOverviewContent(
    hasPermission: Boolean,
    selectedPeriod: ScreenTimePeriod,
    onSelectPeriod: (ScreenTimePeriod) -> Unit,
    customStartDate: LocalDate,
    customEndDate: LocalDate,
    onCustomStartDateChange: (LocalDate) -> Unit,
    onCustomEndDateChange: (LocalDate) -> Unit,
    appUsageList: List<AppUsageInfo>,
    prevAppUsageList: List<AppUsageInfo>,
    hourlyUsageList: List<HourlyUsage>,
    systemMetrics: Triple<Int, Int, Int>,
    dailyGoalMinutes: Int,
    onOpenSettings: () -> Unit,
    onOpenAppUsage: () -> Unit,
    onOpenAppDetail: (String) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenFocusMode: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenTrends: () -> Unit,
    onOpenTimeline: () -> Unit,
    onRequestPermission: () -> Unit,
    onNavigateHome: () -> Unit
 ) {
    val context = LocalContext.current
    val totalTimeMs = appUsageList.sumOf { it.totalTimeMs }
    val totalMinutes = (totalTimeMs / 60000).toInt()
    val prevTotalMs = prevAppUsageList.sumOf { it.totalTimeMs }
    val prevTotalMinutes = (prevTotalMs / 60000).toInt()

    val diffMinutes = totalMinutes - prevTotalMinutes
    val pctChange = if (prevTotalMinutes > 0) {
        ((diffMinutes.toFloat() / prevTotalMinutes) * 100).toInt()
    } else 0

    val goalPct = if (dailyGoalMinutes > 0) {
        (totalMinutes.toFloat() / dailyGoalMinutes).coerceIn(0f, 1f)
    } else 0f

    var selectedHourIndex by remember { mutableStateOf<Int?>(12) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Life",
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Track",
                            color = PurpleLight,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        "Digital Wellbeing",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Understand your usage. Build better habits.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(38.dp)
                        .background(CardInner, CircleShape)
                        .border(1.dp, CardBorder, CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextWhite, modifier = Modifier.size(18.dp))
                }
            }
        }

        // 2. Permission Banner if needed
        if (!hasPermission) {
            item {
                Surface(
                    color = AmberWarning.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(AmberWarning.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Usage Access Permission", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Grant access to display exact device screen time.", color = TextMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Enable", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Period Selector Pills
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScreenTimePeriod.values().forEach { period ->
                    val isSelected = period == selectedPeriod
                    Surface(
                        color = if (isSelected) VioletBrand else CardBg,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (isSelected) VioletBrand else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectPeriod(period) }
                    ) {
                        Text(
                            period.title,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (selectedPeriod == ScreenTimePeriod.CUSTOM) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = CardBg, shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f).clickable {
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    val chosen = LocalDate.of(year, month + 1, day)
                                    onCustomStartDateChange(chosen)
                                    if (customEndDate.isBefore(chosen)) onCustomEndDateChange(chosen)
                                },
                                customStartDate.year, customStartDate.monthValue - 1, customStartDate.dayOfMonth
                            ).show()
                        }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("START DATE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(customStartDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")), color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Surface(
                        color = CardBg, shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f).clickable {
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    val chosen = LocalDate.of(year, month + 1, day)
                                    if (!chosen.isBefore(customStartDate)) onCustomEndDateChange(chosen)
                                },
                                customEndDate.year, customEndDate.monthValue - 1, customEndDate.dayOfMonth
                            ).show()
                        }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("END DATE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(customEndDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")), color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 4. Feature Shortcut Pills (Categories, Focus Mode, Goals, Reminders, Trends, Timeline)
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { ShortcutPill("Categories", Icons.Default.Category, EmeraldAccent, onOpenCategories) }
                item { ShortcutPill("Focus Mode", Icons.Default.SelfImprovement, PurplePrimary, onOpenFocusMode) }
                item { ShortcutPill("Goals", Icons.Default.Flag, BlueAccent, onOpenGoals) }
                item { ShortcutPill("Reminders", Icons.Default.Notifications, CoralAccent, onOpenReminders) }
                item { ShortcutPill("Trends", Icons.Default.ShowChart, CyanBrand, onOpenTrends) }
                item { ShortcutPill("Timeline", Icons.Default.Schedule, AmberWarning, onOpenTimeline) }
            }
        }

        // 5. Screen Time Summary Card with Circular Goal Ring
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Screen Time (${selectedPeriod.title})", color = TextMuted, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val hours = totalMinutes / 60
                            val mins = totalMinutes % 60
                            Text(
                                "${hours}h ${mins}m",
                                color = TextWhite,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (pctChange != 0) {
                                Surface(
                                    color = (if (pctChange > 0) CoralAccent else EmeraldAccent).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "${if (pctChange > 0) "↑" else "↓"} ${kotlin.math.abs(pctChange)}%",
                                        color = if (pctChange > 0) CoralAccent else EmeraldAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val diffHours = kotlin.math.abs(diffMinutes) / 60
                        val diffMins = kotlin.math.abs(diffMinutes) % 60
                        val comparisonLabel = when (selectedPeriod) {
                            ScreenTimePeriod.TODAY -> "yesterday"
                            ScreenTimePeriod.THIS_WEEK -> "last week"
                            ScreenTimePeriod.THIS_MONTH -> "last month"
                            ScreenTimePeriod.CUSTOM -> "previous period"
                        }
                        Text(
                            "${diffHours}h ${diffMins}m ${if (diffMinutes >= 0) "more" else "less"} than $comparisonLabel",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    // Circular Progress Indicator
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 8.dp.toPx()
                            // Track
                            drawCircle(
                                color = Color(0xFF1E293B),
                                style = Stroke(strokeWidth)
                            )
                            // Progress
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(PurplePrimary, BlueAccent, CyanBrand, PurplePrimary)
                                ),
                                startAngle = -90f,
                                sweepAngle = goalPct * 360f,
                                useCenter = false,
                                style = Stroke(strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${(goalPct * 100).toInt()}%",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "of ${dailyGoalMinutes / 60}h goal",
                                color = TextMuted,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }

        // 6. 3 Metrics Badges: Unlocks, Notifications, App Launches
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val (unlocks, notifications, appLaunches) = systemMetrics
                MetricBadgeCard(
                    modifier = Modifier.weight(1f),
                    label = "Unlocks",
                    value = unlocks.toString(),
                    change = "-20%",
                    isPositive = true,
                    icon = Icons.Default.LockOpen,
                    color = BlueAccent
                )
                MetricBadgeCard(
                    modifier = Modifier.weight(1f),
                    label = "Notifications",
                    value = notifications.toString(),
                    change = "+12%",
                    isPositive = false,
                    icon = Icons.Default.Notifications,
                    color = CoralAccent
                )
                MetricBadgeCard(
                    modifier = Modifier.weight(1f),
                    label = "App Launches",
                    value = appLaunches.toString(),
                    change = "+8%",
                    isPositive = false,
                    icon = Icons.Default.RocketLaunch,
                    color = EmeraldAccent
                )
            }
        }

        // 7. Hourly Screen Time Bar Chart
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Hourly Screen Time",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedHourIndex != null) {
                            val h = hourlyUsageList.getOrNull(selectedHourIndex!!)
                            val duration = h?.timeMinutes ?: 0
                            Surface(
                                color = CardInner,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Text(
                                    "${selectedHourIndex}:00 - ${selectedHourIndex!! + 1}:00 : ${duration}m",
                                    color = PurpleLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 24 Hour Bars
                    val maxMinutes = (hourlyUsageList.maxOfOrNull { it.timeMinutes } ?: 60).coerceAtLeast(30)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        hourlyUsageList.forEachIndexed { index, hourUsage ->
                            val isSelected = selectedHourIndex == index
                            val barHeightFraction = (hourUsage.timeMinutes.toFloat() / maxMinutes).coerceIn(0.04f, 1f)

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(barHeightFraction)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(
                                            if (isSelected) PurpleLight
                                            else if (hourUsage.timeMinutes > 0) PurplePrimary.copy(alpha = 0.75f)
                                            else Color.White.copy(alpha = 0.08f)
                                        )
                                        .clickable { selectedHourIndex = index }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // X-Axis Labels (12 AM, 6 AM, 12 PM, 6 PM)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("12 AM", color = TextMuted, fontSize = 9.sp)
                        Text("6 AM", color = TextMuted, fontSize = 9.sp)
                        Text("12 PM", color = TextMuted, fontSize = 9.sp)
                        Text("6 PM", color = TextMuted, fontSize = 9.sp)
                        Text("11 PM", color = TextMuted, fontSize = 9.sp)
                    }
                }
            }
        }

        // 8. Top Apps Section
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Top Apps (${selectedPeriod.title})",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.clickable(onClick = onOpenAppUsage),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("See All", color = PurpleLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = PurpleLight, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (appUsageList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No recorded usage for this period.", color = TextMuted, fontSize = 12.sp)
                        }
                    } else {
                        appUsageList.take(6).forEach { app ->
                            AppUsageRow(
                                app = app,
                                onClick = { onOpenAppDetail(app.packageName) }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. SCREEN 2: APP USAGE DETAILS
// ==========================================
@Composable
private fun AppUsageDetailsContent(
    hasPermission: Boolean,
    selectedPeriod: ScreenTimePeriod,
    onSelectPeriod: (ScreenTimePeriod) -> Unit,
    apps: List<AppUsageInfo>,
    onSelectApp: (String) -> Unit,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortByDuration by remember { mutableStateOf(true) }

    val filteredApps = remember(apps, searchQuery, sortByDuration) {
        val query = searchQuery.trim().lowercase()
        val list = if (query.isEmpty()) apps else apps.filter { it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query) }
        if (sortByDuration) list.sortedByDescending { it.totalTimeMs } else list.sortedBy { it.appName.lowercase() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Bar
        item {
            SubScreenTopBar(title = "App Usage", onBack = onBack)
        }

        // Period Filters
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScreenTimePeriod.values().forEach { period ->
                    val isSelected = period == selectedPeriod
                    Surface(
                        color = if (isSelected) VioletBrand else CardBg,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (isSelected) VioletBrand else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectPeriod(period) }
                    ) {
                        Text(
                            period.title,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Search & Sort Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = PurplePrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { sortByDuration = !sortByDuration },
                    modifier = Modifier
                        .size(46.dp)
                        .background(CardBg, RoundedCornerShape(14.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        Icons.Default.Sort,
                        contentDescription = "Sort",
                        tint = if (sortByDuration) PurpleLight else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // App List
        if (filteredApps.isEmpty()) {
            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No applications found.", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(filteredApps) { app ->
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { onSelectApp(app.packageName) }
                ) {
                    AppUsageRow(
                        app = app,
                        onClick = { onSelectApp(app.packageName) },
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 5. SCREEN 3: INDIVIDUAL APP ANALYTICS
// ==========================================
@Composable
private fun IndividualAppAnalyticsContent(
    app: AppUsageInfo?,
    onBack: () -> Unit
) {
    if (app == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("App not found", color = TextWhite)
        }
        return
    }

    var selectedTab by remember { mutableStateOf("Month") }
    var viewMode by remember { mutableStateOf("Daily View") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubScreenTopBar(title = app.appName, onBack = onBack)
        }

        // Header: App Icon + Name + Period Tabs
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AppIconBox(app = app, size = 52)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(app.category.displayName, color = app.category.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Period Selector (Day, Week, Month, Custom)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Day", "Week", "Month", "Custom").forEach { tab ->
                            val isSelected = tab == selectedTab
                            Surface(
                                color = if (isSelected) VioletBrand else CardInner,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = tab }
                            ) {
                                Text(
                                    tab,
                                    color = if (isSelected) Color.White else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Usage Metric
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(app.durationFormatted, color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Recorded usage", color = TextMuted, fontSize = 10.sp)
                    }
                    Text("Usage recorded for the selected reporting period", color = TextMuted, fontSize = 10.sp)
                }
            }
        }

        // Historical app charts are intentionally omitted until date-specific per-app records are available.
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Historical usage", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "This screen shows the app's recorded usage for the selected overview period. Historical per-app comparisons will appear when reliable date-specific records are available.",
                        color = TextMuted, fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. SCREEN 4: CATEGORIES & INSIGHTS
// ==========================================
@Composable
private fun CategoriesAnalyticsContent(
    selectedPeriod: ScreenTimePeriod,
    onSelectPeriod: (ScreenTimePeriod) -> Unit,
    categories: List<CategoryUsage>,
    onSelectApp: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    val activeCategory = categories.getOrNull(selectedCategoryIndex) ?: categories.firstOrNull()

    val totalCategoryMinutes = categories.sumOf { it.timeMinutes }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubScreenTopBar(title = "App Categories", onBack = onBack)
        }

        // Period Filters
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(ScreenTimePeriod.TODAY, ScreenTimePeriod.THIS_WEEK, ScreenTimePeriod.THIS_MONTH).forEach { p ->
                    val isSel = p == selectedPeriod
                    Surface(
                        color = if (isSel) VioletBrand else CardBg,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (isSel) VioletBrand else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectPeriod(p) }
                    ) {
                        Text(
                            p.title,
                            color = if (isSel) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Donut Chart + Category Breakdown
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Donut Canvas
                    Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 12.dp.toPx()
                            var currentAngle = -90f
                            categories.forEach { cat ->
                                val sweep = (cat.percentage / 100f) * 360f
                                if (sweep > 0) {
                                    drawArc(
                                        color = cat.category.color,
                                        startAngle = currentAngle,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(strokeWidth)
                                    )
                                    currentAngle += sweep
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val h = totalCategoryMinutes / 60
                            val m = totalCategoryMinutes % 60
                            Text("${h}h ${m}m", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Total", color = TextMuted, fontSize = 9.sp)
                        }
                    }

                    // Legend
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEachIndexed { idx, cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCategoryIndex = idx },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.size(8.dp).background(cat.category.color, CircleShape))
                                    Text(cat.category.displayName, color = if (selectedCategoryIndex == idx) TextWhite else TextMuted, fontSize = 11.sp)
                                }
                                Text("${cat.percentage.toInt()}%", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Top Apps in Selected Category
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Top Apps in Category",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = activeCategory?.category?.color?.copy(alpha = 0.2f) ?: CardInner,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                activeCategory?.category?.displayName ?: "All",
                                color = activeCategory?.category?.color ?: TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    val apps = activeCategory?.apps ?: emptyList()
                    if (apps.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No apps in this category", color = TextMuted, fontSize = 12.sp)
                        }
                    } else {
                        apps.forEach { app ->
                            AppUsageRow(
                                app = app,
                                onClick = { onSelectApp(app.packageName) }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. SCREEN 5: FOCUS MODE
// ==========================================
@Composable
private fun FocusModeContent(
    sessions: List<FocusSession>,
    onSessionCompleted: (FocusSession) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("Timer") }

    // Presets in minutes
    var targetMinutes by remember { mutableIntStateOf(25) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }

    // Countdown loop
    LaunchedEffect(isRunning, remainingSeconds) {
        if (isRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        } else if (isRunning && remainingSeconds <= 0) {
            isRunning = false
            val newSession = FocusSession(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                durationMinutes = targetMinutes,
                targetMinutes = targetMinutes,
                completed = true
            )
            onSessionCompleted(newSession)
            scheduleFocusCompletionAlert(context, targetMinutes)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubScreenTopBar(title = "Focus Mode", onBack = onBack)
        }

        // Sub-tabs: Timer, Sessions, Settings
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Timer", "Sessions", "Settings").forEach { tab ->
                    val isSel = tab == activeTab
                    Surface(
                        color = if (isSel) VioletBrand else CardBg,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (isSel) VioletBrand else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeTab = tab }
                    ) {
                        Text(
                            tab,
                            color = if (isSel) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (activeTab == "Timer") {
            // Big Circular Focus Timer
            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Circular Ring
                        val totalSecs = (targetMinutes * 60).coerceAtLeast(1)
                        val progressFraction = 1f - (remainingSeconds.toFloat() / totalSecs)

                        Box(
                            modifier = Modifier.size(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 14.dp.toPx()
                                drawCircle(
                                    color = Color(0xFF1E293B),
                                    style = Stroke(strokeWidth)
                                )
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(PurplePrimary, VioletBrand, PurpleLight, PurplePrimary)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = progressFraction * 360f,
                                    useCenter = false,
                                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Spa, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.height(4.dp))
                                val mins = remainingSeconds / 60
                                val secs = remainingSeconds % 60
                                Text(
                                    String.format(Locale.US, "%02d:%02d", mins, secs),
                                    color = TextWhite,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text("Focus Time", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Action Button (Start / Pause / Reset)
                        Button(
                            onClick = {
                                if (isRunning) {
                                    isRunning = false
                                } else {
                                    if (remainingSeconds == 0) remainingSeconds = targetMinutes * 60
                                    isRunning = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VioletBrand),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Text(if (isRunning) "Pause" else "Start", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Preset chips (25m, 45m, 60m, Custom)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(25, 45, 60).forEach { mins ->
                                val isSelected = targetMinutes == mins
                                Surface(
                                    color = if (isSelected) PurplePrimary else CardInner,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.clickable {
                                        if (!isRunning) {
                                            targetMinutes = mins
                                            remainingSeconds = mins * 60
                                        }
                                    }
                                ) {
                                    Text(
                                        "${mins}m",
                                        color = if (isSelected) Color.White else TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Focus Session Stats
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val completedCount = sessions.count { it.completed }
                    val totalFocusMins = sessions.filter { it.completed }.sumOf { it.durationMinutes }
                    MetricBadgeCard(
                        modifier = Modifier.weight(1f),
                        label = "Sessions",
                        value = completedCount.toString(),
                        change = null,
                        isPositive = true,
                        icon = Icons.Default.CheckCircle,
                        color = EmeraldAccent
                    )
                    MetricBadgeCard(
                        modifier = Modifier.weight(1f),
                        label = "Total Focus",
                        value = "${totalFocusMins / 60}h ${totalFocusMins % 60}m",
                        change = null,
                        isPositive = true,
                        icon = Icons.Default.Timer,
                        color = PurplePrimary
                    )
                    MetricBadgeCard(
                        modifier = Modifier.weight(1f),
                        label = "Completion",
                        value = "85%",
                        change = null,
                        isPositive = true,
                        icon = Icons.Default.TrendingUp,
                        color = BlueAccent
                    )
                }
            }

            // Today's Goal Card
            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.HourglassTop, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(18.dp))
                                Text("Today's Goal", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("3 / 4 Sessions", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        LinearProgressIndicator(
                            progress = { 0.75f },
                            color = EmeraldAccent,
                            trackColor = CardInner,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                    }
                }
            }
        } else if (activeTab == "Sessions") {
            // Sessions History List
            if (sessions.isEmpty()) {
                item {
                    Surface(
                        color = CardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No focus sessions recorded yet.", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(sessions) { s ->
                    Surface(
                        color = CardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("${s.durationMinutes} min Focus Session", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    val dateStr = DateTimeFormatter.ofPattern("MMM dd, yyyy · h:mm a", Locale.US)
                                        .format(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(s.timestamp), java.time.ZoneId.systemDefault()))
                                    Text(dateStr, color = TextMuted, fontSize = 10.sp)
                                }
                            }
                            Surface(
                                color = EmeraldAccent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Completed", color = EmeraldAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // Settings tab for focus mode
            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Focus Mode Options", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Completion Notification", color = TextWhite, fontSize = 13.sp)
                                Text("Play sound/alert when session ends", color = TextMuted, fontSize = 10.sp)
                            }
                            Switch(
                                checked = true,
                                onCheckedChange = {},
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VioletBrand)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. SCREEN 6: USAGE GOALS
// ==========================================
@Composable
private fun UsageGoalsContent(
    dailyGoalMinutes: Int,
    todayMinutes: Int,
    weeklyTrends: List<DailyTrendPoint>,
    appLimits: List<AppLimit>,
    onEditDailyGoal: () -> Unit,
    onToggleAppLimit: (AppLimit) -> Unit,
    onBack: () -> Unit
) {
    var activeSubTab by remember { mutableStateOf("Daily Goal") }

    val goalFraction = if (dailyGoalMinutes > 0) (todayMinutes.toFloat() / dailyGoalMinutes).coerceIn(0f, 1f) else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubScreenTopBar(title = "Usage Goals", onBack = onBack)
        }

        // Sub-tabs: Daily Goal, App Limits, Reminders
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Daily Goal", "App Limits", "Reminders").forEach { tab ->
                    val isSel = tab == activeSubTab
                    Surface(
                        color = if (isSel) VioletBrand else CardBg,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (isSel) VioletBrand else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeSubTab = tab }
                    ) {
                        Text(
                            tab,
                            color = if (isSel) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (activeSubTab == "Daily Goal") {
            // Daily Screen Time Goal Card
            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Daily Screen Time Goal", color = TextMuted, fontSize = 11.sp)
                                val usedH = todayMinutes / 60
                                val usedM = todayMinutes % 60
                                val goalH = dailyGoalMinutes / 60
                                Text("${usedH}h ${usedM}m of ${goalH}h", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Text(
                                "${(goalFraction * 100).toInt()}%",
                                color = if (todayMinutes > dailyGoalMinutes) CoralAccent else EmeraldAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LinearProgressIndicator(
                            progress = { goalFraction },
                            color = if (todayMinutes > dailyGoalMinutes) CoralAccent else EmeraldAccent,
                            trackColor = CardInner,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )

                        Button(
                            onClick = onEditDailyGoal,
                            colors = ButtonDefaults.buttonColors(containerColor = CardInner),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Edit Goal", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Goal Progress (This Week)
            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Goal Progress (This Week)", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Goal (${dailyGoalMinutes / 60}h)", color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(18.dp))

                        // Weekly Bars with Goal Line
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weeklyTrends.forEach { day ->
                                val dayHours = day.thisPeriodMinutes / 60f
                                val goalHours = (dailyGoalMinutes / 60f).coerceAtLeast(1f)
                                val fraction = (dayHours / (goalHours * 1.3f)).coerceIn(0.04f, 1f)
                                val isOver = day.thisPeriodMinutes > dailyGoalMinutes

                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                    Box(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .fillMaxHeight(fraction)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(if (isOver) CoralAccent else VioletBrand)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(day.dayOfWeek, color = TextMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeSubTab == "App Limits") {
            // App Limits configuration
            item {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Application Daily Limits", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Set daily usage restrictions for individual apps.", color = TextMuted, fontSize = 11.sp)

                        val sampleLimits = if (appLimits.isEmpty()) {
                            listOf(
                                AppLimit("com.google.android.youtube", "YouTube", 60, true),
                                AppLimit("com.instagram.android", "Instagram", 45, true),
                                AppLimit("com.whatsapp", "WhatsApp", 90, false)
                            )
                        } else appLimits

                        sampleLimits.forEach { limit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardInner, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(limit.appName, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("Daily limit: ${limit.limitMinutes} minutes", color = TextMuted, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = limit.enabled,
                                    onCheckedChange = { onToggleAppLimit(limit) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VioletBrand)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. SCREEN 7: REMINDERS
// ==========================================
@Composable
private fun RemindersContent(
    config: RemindersConfig,
    onUpdateConfig: (RemindersConfig) -> Unit,
    onBack: () -> Unit
) {
    var activeSubTab by remember { mutableStateOf("Break Reminders") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubScreenTopBar(title = "Reminders", onBack = onBack)
        }

        // Sub-tabs: Break Reminders, Bedtime, Notifications
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Break Reminders", "Bedtime", "Notifications").forEach { tab ->
                    val isSel = tab == activeSubTab
                    Surface(
                        color = if (isSel) VioletBrand else CardBg,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (isSel) VioletBrand else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeSubTab = tab }
                    ) {
                        Text(
                            tab,
                            color = if (isSel) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Enable Break Reminders Card
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(EmeraldAccent.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text("Enable Break Reminders", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Get notified to take a break from screen time", color = TextMuted, fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = config.breakRemindersEnabled,
                            onCheckedChange = { onUpdateConfig(config.copy(breakRemindersEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmeraldAccent)
                        )
                    }

                    Divider(color = CardBorder)

                    // Reminder Interval
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reminder Interval", color = TextWhite, fontSize = 12.sp)
                        Surface(color = CardInner, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CardBorder)) {
                            Text("${config.reminderIntervalMinutes} minutes", color = PurpleLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }

                    // Break Duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Break Duration", color = TextWhite, fontSize = 12.sp)
                        Surface(color = CardInner, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CardBorder)) {
                            Text("${config.breakDurationMinutes} minutes", color = BlueAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }

                    // Notification Style
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notification Style", color = TextWhite, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Standard", "Gentle", "Silent").forEach { style ->
                                val isSelected = config.notificationStyle == style
                                Surface(
                                    color = if (isSelected) VioletBrand else CardInner,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.clickable { onUpdateConfig(config.copy(notificationStyle = style)) }
                                ) {
                                    Text(
                                        style,
                                        color = if (isSelected) Color.White else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Next Reminder Card
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(CoralAccent.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = CoralAccent, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Next Reminder", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("In 35 minutes · Take a short break", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// 10. SCREEN 8: SCREEN TIME TRENDS
// ==========================================
@Composable
private fun ScreenTimeTrendsContent(
    selectedPeriod: ScreenTimePeriod,
    onSelectPeriod: (ScreenTimePeriod) -> Unit,
    weeklyTrends: List<DailyTrendPoint>,
    onBack: () -> Unit
) {
    val totalMins = weeklyTrends.sumOf { it.thisPeriodMinutes }
    val avgMins = if (weeklyTrends.isNotEmpty()) totalMins / weeklyTrends.size else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubScreenTopBar(title = "Screen Time Trends", onBack = onBack)
        }

        // Period Filters (Day, Week, Month, Custom)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScreenTimePeriod.values().forEach { p ->
                    val isSel = p == selectedPeriod
                    Surface(
                        color = if (isSel) VioletBrand else CardBg,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (isSel) VioletBrand else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectPeriod(p) }
                    ) {
                        Text(
                            p.title,
                            color = if (isSel) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Summary Cards: Total Screen Time & Daily Average
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Screen Time", color = TextMuted, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${totalMins / 60}h ${totalMins % 60}m", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text("↑ 12% vs last week", color = EmeraldAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Daily Average", color = TextMuted, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${avgMins / 60}h ${avgMins % 60}m", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text("↓ 8% vs last week", color = BlueAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Multi-line Trend Chart (This Week vs Last Week)
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Usage Trends", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChartLegendItem("This Week", PurplePrimary)
                            ChartLegendItem("Last Week", CyanBrand)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Trend Lines Canvas
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val maxVal = (weeklyTrends.maxOfOrNull { it.thisPeriodMinutes.coerceAtLeast(it.previousPeriodMinutes) } ?: 360).toFloat().coerceAtLeast(60f)
                            val stepX = w / (weeklyTrends.size - 1).coerceAtLeast(1)

                            // Draw This Week Line
                            val thisPoints = weeklyTrends.mapIndexed { idx, pt ->
                                val x = idx * stepX
                                val y = h - (pt.thisPeriodMinutes.toFloat() / maxVal) * (h - 20) - 10
                                Offset(x, y)
                            }

                            for (i in 0 until thisPoints.size - 1) {
                                drawLine(PurplePrimary, thisPoints[i], thisPoints[i + 1], strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                            }
                            thisPoints.forEach { pt ->
                                drawCircle(Color(0xFF070B14), radius = 5.dp.toPx(), center = pt)
                                drawCircle(PurplePrimary, radius = 3.dp.toPx(), center = pt)
                            }

                            // Draw Last Week Line (Cyan)
                            val lastPoints = weeklyTrends.mapIndexed { idx, pt ->
                                val x = idx * stepX
                                val y = h - (pt.previousPeriodMinutes.toFloat() / maxVal) * (h - 20) - 10
                                Offset(x, y)
                            }

                            for (i in 0 until lastPoints.size - 1) {
                                drawLine(CyanBrand.copy(alpha = 0.6f), lastPoints[i], lastPoints[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        weeklyTrends.forEach { Text(it.dayOfWeek, color = TextMuted, fontSize = 10.sp) }
                    }
                }
            }
        }
    }
}

// ==========================================
// 11. SCREEN 9: DAILY USAGE TIMELINE
// ==========================================
@Composable
private fun DailyUsageTimelineContent(
    date: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    totalMinutes: Int,
    dailyGoalMinutes: Int,
    topApps: List<AppUsageInfo>,
    timelineEvents: List<TimelineEvent>,
    onSelectApp: (String) -> Unit,
    onBack: () -> Unit
) {
    val dateTitle = DateTimeFormatter.ofPattern("EEEE · d MMMM yyyy", Locale.US).format(date)
    val goalPct = (totalMinutes.toFloat() / dailyGoalMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubScreenTopBar(title = dateTitle, onBack = onBack)
        }

        // Summary Card
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Screen Time", color = TextMuted, fontSize = 11.sp)
                            Text("${totalMinutes / 60}h ${totalMinutes % 60}m", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Surface(
                            color = EmeraldAccent.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("${(goalPct * 100).toInt()}% of goal", color = EmeraldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    LinearProgressIndicator(
                        progress = { goalPct },
                        color = VioletBrand,
                        trackColor = CardInner,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().height(7.dp)
                    )

                    // Top Apps Row Icons
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Top Apps:", color = TextMuted, fontSize = 11.sp)
                        topApps.take(4).forEach { app ->
                            AppIconBox(app = app, size = 26)
                        }
                    }
                }
            }
        }

        // Timeline Events
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Usage Timeline", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))

                    if (timelineEvents.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No usage timeline recorded for this date.", color = TextMuted, fontSize = 12.sp)
                        }
                    } else {
                        timelineEvents.forEachIndexed { idx, ev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectApp(ev.packageName) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(ev.timeLabel, color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                                Box(modifier = Modifier.size(8.dp).background(PurplePrimary, CircleShape))
                                Text(ev.appName, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${ev.durationMinutes}m", color = PurpleLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (idx < timelineEvents.size - 1) {
                                Row(modifier = Modifier.padding(start = 76.dp)) {
                                    Box(modifier = Modifier.width(1.dp).height(18.dp).background(CardBorder))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 12. SCREEN 10: SETTINGS & PRIVACY
// ==========================================
@Composable
private fun SettingsPrivacyContent(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefreshData: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenReminders: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SubScreenTopBar(title = "Settings", onBack = onBack)
        }

        // Usage Access Permission Card
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (hasPermission) EmeraldAccent.copy(alpha = 0.2f) else AmberWarning.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasPermission) EmeraldAccent else AmberWarning,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text("Usage Access Permission", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(if (hasPermission) "Access Granted · LifeTrack can read usage" else "Permission Required", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = if (hasPermission) CardInner else AmberWarning),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (hasPermission) "Manage" else "Grant", color = if (hasPermission) TextWhite else Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Why This Permission?
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = PurpleLight, modifier = Modifier.size(18.dp))
                        Text("Why this permission?", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "We use Android's Usage Access to calculate exact foreground app durations, session counts, and hourly trends. Your usage data never leaves your device.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Data Refresh Action
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Data Refresh", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Reload UsageStats queries now", color = TextMuted, fontSize = 10.sp)
                    }
                    Button(
                        onClick = onRefreshData,
                        colors = ButtonDefaults.buttonColors(containerColor = VioletBrand),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Refresh", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Data Retention & Privacy Cards
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Data Retention", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Usage metrics are stored locally on your device.", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                    Divider(color = CardBorder)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Your data is private", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("LifeTrack does not collect or upload app statistics.", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 13. REUSABLE COMPONENT WIDGETS
// ==========================================

@Composable
private fun SubScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(38.dp)
                .background(CardInner, CircleShape)
                .border(1.dp, CardBorder, CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite, modifier = Modifier.size(18.dp))
        }
        Text(
            title,
            color = TextWhite,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ShortcutPill(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(label, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MetricBadgeCard(
    label: String,
    value: String,
    change: String?,
    isPositive: Boolean,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = TextMuted, fontSize = 10.sp)
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(value, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (change != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    change,
                    color = if (isPositive) EmeraldAccent else CoralAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AppUsageRow(
    app: AppUsageInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIconBox(app = app, size = 38)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(app.appName, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.durationFormatted, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (app.percentage / 100f).coerceIn(0.02f, 1f) },
                    color = app.category.color,
                    trackColor = CardInner,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.weight(1f).height(5.dp)
                )
                Text("${app.percentage.toInt()}%", color = TextMuted, fontSize = 10.sp)
            }
        }

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun AppIconBox(app: AppUsageInfo, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(app.category.color.copy(alpha = 0.2f), RoundedCornerShape(size.dp / 4))
            .border(1.dp, app.category.color.copy(alpha = 0.4f), RoundedCornerShape(size.dp / 4)),
        contentAlignment = Alignment.Center
    ) {
        if (app.iconBitmap != null) {
            Image(
                bitmap = app.iconBitmap,
                contentDescription = app.appName,
                modifier = Modifier
                    .size((size * 0.75).dp)
                    .clip(RoundedCornerShape(size.dp / 5))
            )
        } else {
            Text(
                app.appName.take(1).uppercase(),
                color = app.category.color,
                fontSize = (size / 2).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChartLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Text(label, color = TextMuted, fontSize = 10.sp)
    }
}

// Dialog for editing daily goal
@Composable
private fun EditDailyGoalDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var hours by remember { mutableIntStateOf(currentMinutes / 60) }
    var minutes by remember { mutableIntStateOf(currentMinutes % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text("Set Daily Goal", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Select your daily device screen-time limit:", color = TextMuted, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { if (hours < 18) hours++ }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = TextWhite)
                        }
                        Text("${hours}h", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { if (hours > 1) hours-- }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextWhite)
                        }
                    }
                    Spacer(Modifier.width(24.dp))
                    // Minutes
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { minutes = (minutes + 15) % 60 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = TextWhite)
                        }
                        Text("${minutes}m", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { minutes = if (minutes >= 15) minutes - 15 else 45 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextWhite)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(hours * 60 + minutes) },
                colors = ButtonDefaults.buttonColors(containerColor = VioletBrand)
            ) {
                Text("Save Goal", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
