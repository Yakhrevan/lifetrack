package com.yakhrevan.lifetrack

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private data class WeatherDisplay(
    val location: String,
    val temp: String,
    val condition: String,
    val icon: ImageVector,
    val color: Color
)

private data class GreetingInfo(
    val title: String,
    val line1: String,
    val line2: String,
    val gradient: List<Color>
)

// Design System Colors matching reference screenshot
private val DarkBg         = Color(0xFF070B14)
private val CardBg         = Color(0xFF0E1626)
private val CardInner      = Color(0xFF141F33)
private val CardBorder     = Color(0x1AFFFFFF)
private val TextWhite      = Color(0xFFF1F5F9)
private val TextMuted      = Color(0xFF8896AB)
private val CyanBrand      = Color(0xFF38BDF8)
private val EmeraldAccent  = Color(0xFF00E5A3)
private val BlueAccent     = Color(0xFF00B4D8)
private val OrangeAccent   = Color(0xFFFF9F1C)
private val PurpleAccent   = Color(0xFFA855F7)
private val PinkAccent     = Color(0xFFF43F5E)
private val YellowAccent   = Color(0xFFFFD166)
private val RedExpense     = Color(0xFFEF4444)
private val GreenIncome    = Color(0xFF10B981)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    logs: List<LogEntry>,
    studyGoal: Int,
    waterGoal: Int,
    profileName: String,
    profilePrefs: android.content.SharedPreferences,
    onNavigate: (String) -> Unit,
    onOpenAdd: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Refresh date-dependent Home values periodically while the screen remains visible.
    var clockRefresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            clockRefresh += 1
        }
    }
    val today = remember(clockRefresh) { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val dailyLogs = remember(logs, today) { logs.filter { it.date == today } }

    val studyMins = remember(dailyLogs) { dailyLogs.filter { it.category == "Study" }.sumOf { it.amount.toIntOrNull() ?: 0 } }
    val waterMl   = remember(dailyLogs) { dailyLogs.filter { it.category == "Water" }.sumOf { it.amount.toIntOrNull() ?: 0 } }

    // Use configured goals only; never invent a target when the user has not configured one.
    val effectiveStudyGoal = studyGoal.coerceAtLeast(0)
    val effectiveWaterGoal = waterGoal.coerceAtLeast(0)
    val studyPct = if (effectiveStudyGoal > 0) (studyMins.toFloat() / effectiveStudyGoal).coerceIn(0f, 1f) else 0f
    val waterPct = if (effectiveWaterGoal > 0) (waterMl.toFloat() / effectiveWaterGoal).coerceIn(0f, 1f) else 0f

    val currentMonth = remember(clockRefresh) { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }
    val monthLogs = remember(logs, currentMonth) { logs.filter { it.date.startsWith(currentMonth) } }
    val totalIncome  = remember(monthLogs) { monthLogs.filter { it.category == "Income" }.sumOf { MoneyAmounts.value(it.amount) } }
    val totalExpense = remember(monthLogs) { monthLogs.filter { it.category == "Expense" }.sumOf { MoneyAmounts.value(it.amount) } }
    val balance = totalIncome - totalExpense
    val yesterdayDate = remember(today) { java.time.LocalDate.parse(today).minusDays(1).toString() }
    val todayExpenses = remember(logs, today) { logs.filter { it.category.equals("Expense", true) && it.date == today }.sumOf { MoneyAmounts.value(it.amount) } }
    val yesterdayExpenses = remember(logs, yesterdayDate) { logs.filter { it.category.equals("Expense", true) && it.date == yesterdayDate }.sumOf { MoneyAmounts.value(it.amount) } }
    val todayExpenseCount = remember(logs, today) { logs.count { it.category.equals("Expense", true) && it.date == today } }
    val yesterdayExpenseCount = remember(logs, yesterdayDate) { logs.count { it.category.equals("Expense", true) && it.date == yesterdayDate } }
    val savedPct = if (totalIncome > 0) ((totalIncome - totalExpense) / totalIncome * 100).toFloat().coerceIn(0f, 100f) else 0f
    val recentMoneyTransactions = remember(monthLogs) {
        monthLogs.filter { it.category == "Income" || it.category == "Expense" }
            .reversed()
            .take(3)
    }

    val yesterday = remember(today) {
        val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -1)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    val localToday = remember(clockRefresh) { java.time.LocalDate.now() }
    val scheduledHabitsToday = remember { HabitRoutineRepository.getScheduledHabitsForDate(context, localToday) }
    val totalHabits = scheduledHabitsToday.size
    val completedHabits = remember(scheduledHabitsToday, localToday, clockRefresh) {
        scheduledHabitsToday.count { habit ->
            HabitRoutineRepository.getCompletion(context, habit.id, localToday.toString())?.status == "completed"
        }
    }

    val currentHour = remember(clockRefresh) { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    // Streak & Goal Achieved calculation
    val activeDates = remember(logs) { logs.map { it.date }.toSet() }
    val currentStreak = remember(activeDates, today) {
        val cal = Calendar.getInstance()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        if (todayStr !in activeDates) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        var count = 0
        while (count < 366) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            if (date !in activeDates) break
            count++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        count
    }

    // Only include categories that have a real target today. Never treat missing habits
    // as completed: doing so created a misleading default 33% on a fresh day.
    val dailyProgressPct = remember(studyPct, waterPct, totalHabits, completedHabits) {
        val trackedProgress = mutableListOf<Float>()
        if (effectiveStudyGoal > 0) trackedProgress += studyPct
        if (effectiveWaterGoal > 0) trackedProgress += waterPct
        if (totalHabits > 0) {
            trackedProgress += (completedHabits.toFloat() / totalHabits).coerceIn(0f, 1f)
        }
        val hasRecordedActivity = studyMins > 0 || waterMl > 0 || completedHabits > 0
        if (!hasRecordedActivity || trackedProgress.isEmpty()) 0
        else (trackedProgress.average() * 100).toInt().coerceIn(0, 100)
    }

    // Dashboard Customization State
    var showCustomizeDialog by remember { mutableStateOf(false) }

    remember {
        if (!profilePrefs.getBoolean("home_v2_cleanup", false)) {
            profilePrefs.edit()
                .putString("home_layout_mode", "full_width")
                .putBoolean("home_show_screentime", true)
                .putBoolean("home_show_routines", true)
                .putBoolean("home_show_comparison", false)
                .putBoolean("home_v2_cleanup", true)
                .apply()
        }
        true
    }

    // Enable Screen Time for existing installs once, without overriding future user customization.
    remember {
        if (!profilePrefs.getBoolean("home_v3_screen_time", false)) {
            profilePrefs.edit()
                .putBoolean("home_show_screentime", true)
                .putBoolean("home_v3_screen_time", true)
                .apply()
        }
        true
    }

    var showSmartCard by remember {
        mutableStateOf(profilePrefs.getBoolean("home_show_smart_card", true))
    }
    var showProgressRings by remember {
        mutableStateOf(profilePrefs.getBoolean("home_show_progress", true))
    }
    var showScreenTime by remember {
        mutableStateOf(profilePrefs.getBoolean("home_show_screentime", true))
    }
    var showTasks by remember {
        mutableStateOf(profilePrefs.getBoolean("home_show_tasks", true))
    }
    var showMoney by remember {
        mutableStateOf(profilePrefs.getBoolean("home_show_money", true))
    }
    var showHabits by remember {
        mutableStateOf(profilePrefs.getBoolean("home_show_habits", true))
    }
    var showRoutines by remember {
        mutableStateOf(profilePrefs.getBoolean("home_show_routines", false))
    }
    var showNotes by remember {
        mutableStateOf(profilePrefs.getBoolean("home_show_notes", true))
    }
    var showComparison by remember {
        mutableStateOf(profilePrefs.getBoolean("home_show_comparison", false))
    }
    var showUpcoming by remember {
        mutableStateOf(profilePrefs.getBoolean("home_show_upcoming", true))
    }
    var layoutMode by remember {
        mutableStateOf(profilePrefs.getString("home_layout_mode", "full_width") ?: "full_width")
    }

    // Pull to refresh
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val onRefresh: () -> Unit = {
        if (!isRefreshing) {
            scope.launch {
                isRefreshing = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val city = profilePrefs.getString("weather_city", "Coimbatore") ?: "Coimbatore"
                val savedLat = profilePrefs.getString("weather_lat", null)?.toDoubleOrNull()
                val savedLon = profilePrefs.getString("weather_lon", null)?.toDoubleOrNull()
                withContext(Dispatchers.IO) {
                    try {
                        val w = if (savedLat != null && savedLon != null) {
                            fetchLifeTrackWeatherByCoords(savedLat, savedLon, city)
                        } else {
                            fetchLifeTrackWeather(city)
                        }
                        profilePrefs.edit()
                            .putString("weather_city_label", w)
                            .putLong("weather_city_checked_at", System.currentTimeMillis())
                            .apply()
                    } catch (_: Exception) {}
                }
                kotlinx.coroutines.delay(650)
                refreshKey++
                isRefreshing = false
            }
        }
    }

    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = CardBg,
                color = CyanBrand
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Home-only redesign: keep the existing app navigation/footer outside this screen untouched.
            item {
                HomeHeader(
                    profileName = profileName,
                    profilePrefs = profilePrefs,
                    currentStreak = currentStreak,
                    onOpenCustomize = { showCustomizeDialog = true },
                    onNavigate = onNavigate
                )
            }

            // Single responsive progress section; no duplicate overall-progress hero or Quick Access row.
            if (showProgressRings) {
                item {
                    TodaysProgressCard(
                        studyMins = studyMins, studyGoal = effectiveStudyGoal, studyPct = studyPct,
                        waterMl = waterMl, waterGoal = effectiveWaterGoal, waterPct = waterPct,
                        habitsDone = completedHabits, habitsTotal = totalHabits,
                        onSeeAll = { onNavigate("Insights") },
                        onStudyClick = { onNavigate("Study") },
                        onWaterClick = { onNavigate("Water") },
                        onHabitsClick = { onNavigate("Habits & Routines") },
                        onRoutinesClick = { onNavigate("Habits & Routines") },
                        onScreenTimeClick = { onNavigate("Digital Wellbeing") }
                    )
                }
            }
            if (showScreenTime) {
                item { ScreenTimeCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onSeeAll = { onNavigate("Digital Wellbeing") }) }
            }
            if (showMoney) {
                item {
                    ExpandedMoneyOverviewCard(
                        balance = balance, income = totalIncome, expenses = totalExpense, savedPct = savedPct,
                        recentTransactions = recentMoneyTransactions, todayExpenses = todayExpenses,
                        yesterdayExpenses = yesterdayExpenses, todayExpenseCount = todayExpenseCount,
                        yesterdayExpenseCount = yesterdayExpenseCount, onSeeAll = { onNavigate("Money") }
                    )
                }
            }
            if (showTasks) {
                item {
                    TodaysTasksCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        onAdd = { onOpenAdd("Task") }, onSeeAll = { onNavigate("Tasks") }
                    )
                }
            }
            if (showComparison) {
                item { YesterdayVsTodayCard(logs = logs, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), onSeeAll = { onNavigate("Insights") }) }
            }
            if (showHabits) {
                item { DailyHabitsCard(completed = completedHabits, total = totalHabits, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), onSeeAll = { onNavigate("Habits & Routines") }) }
            }
            if (showRoutines) {
                item { RoutinesCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onSeeAll = { onNavigate("Habits & Routines") }) }
            }
            if (showUpcoming) {
                item { UpcomingCard(onSeeAll = { onNavigate("Tasks") }) }
            }
            if (showNotes) {
                item { NotesCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onQuickNote = { onOpenAdd("Note") }, onSeeAll = { onNavigate("Notes") }) }
            }
            item { MotivationFooter() }

            // Existing LifeTrack brand footer preserved verbatim.
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.brand_lyratech_transparent),
                        contentDescription = "LYRATECH",
                        modifier = Modifier.height(24.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "LYRATECH PRODUCTIVITY SYSTEM",
                        color = TextMuted.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showCustomizeDialog) {
        CustomizeDashboardDialog(
            showSmartCard = showSmartCard,
            onToggleSmartCard = {
                showSmartCard = it
                profilePrefs.edit().putBoolean("home_show_smart_card", it).apply()
            },
            showProgressRings = showProgressRings,
            onToggleProgressRings = {
                showProgressRings = it
                profilePrefs.edit().putBoolean("home_show_progress", it).apply()
            },
            showScreenTime = showScreenTime,
            onToggleScreenTime = {
                showScreenTime = it
                profilePrefs.edit().putBoolean("home_show_screentime", it).apply()
            },
            showTasks = showTasks,
            onToggleTasks = {
                showTasks = it
                profilePrefs.edit().putBoolean("home_show_tasks", it).apply()
            },
            showMoney = showMoney,
            onToggleMoney = {
                showMoney = it
                profilePrefs.edit().putBoolean("home_show_money", it).apply()
            },
            showHabits = showHabits,
            onToggleHabits = {
                showHabits = it
                profilePrefs.edit().putBoolean("home_show_habits", it).apply()
            },
            showRoutines = showRoutines,
            onToggleRoutines = {
                showRoutines = it
                profilePrefs.edit().putBoolean("home_show_routines", it).apply()
            },
            showNotes = showNotes,
            onToggleNotes = {
                showNotes = it
                profilePrefs.edit().putBoolean("home_show_notes", it).apply()
            },
            showComparison = showComparison,
            onToggleComparison = {
                showComparison = it
                profilePrefs.edit().putBoolean("home_show_comparison", it).apply()
            },
            showUpcoming = showUpcoming,
            onToggleUpcoming = {
                showUpcoming = it
                profilePrefs.edit().putBoolean("home_show_upcoming", it).apply()
            },
            layoutMode = layoutMode,
            onSelectLayoutMode = {
                layoutMode = it
                profilePrefs.edit().putString("home_layout_mode", it).apply()
            },
            onResetDefaults = {
                showSmartCard = true
                showProgressRings = true
                showScreenTime = true
                showTasks = true
                showMoney = true
                showHabits = true
                showRoutines = true
                showNotes = true
                showComparison = true
                showUpcoming = true
                layoutMode = "full_width"
                profilePrefs.edit()
                    .putBoolean("home_show_smart_card", true)
                    .putBoolean("home_show_progress", true)
                    .putBoolean("home_show_screentime", true)
                    .putBoolean("home_show_tasks", true)
                    .putBoolean("home_show_money", true)
                    .putBoolean("home_show_habits", true)
                    .putBoolean("home_show_routines", true)
                    .putBoolean("home_show_notes", true)
                    .putBoolean("home_show_comparison", true)
                    .putBoolean("home_show_upcoming", true)
                    .putString("home_layout_mode", "compact")
                    .apply()
            },
            onDismiss = { showCustomizeDialog = false }
        )
    }
}

@Composable
private fun MotivationFooter() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardInner),
        border = BorderStroke(1.dp, Color(0xFF2DD4BF).copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF103A35), Color(0xFF10243A), CardInner)
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2DD4BF).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Spa, contentDescription = null, tint = Color(0xFF5EEAD4))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Consistency over perfection", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Small steps today build better habits tomorrow.", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

// ==========================================
// HOME REDESIGN: PROGRESS HERO + QUICK ACCESS
// ==========================================
@Composable
private fun DailyProgressHero(
    progressPct: Int,
    studyMins: Int,
    studyGoal: Int,
    waterMl: Int,
    waterGoal: Int,
    onSeeAll: () -> Unit
) {
    val progress = (progressPct / 100f).coerceIn(0f, 1f)
    Card(
        onClick = onSeeAll,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CyanBrand.copy(alpha = 0.32f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF142B4B), Color(0xFF101B30), Color(0xFF071321))
                    )
                )
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Today's Progress",
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Small steps, steady progress",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CyanBrand.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "$progressPct%",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = CyanBrand,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(CyanBrand, Color(0xFF24D6A5))
                                )
                            )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProgressMiniStat(
                        label = "Study",
                        value = "${studyMins / 60}h ${studyMins % 60}m",
                        detail = "Goal ${studyGoal / 60}h",
                        tint = CyanBrand,
                        modifier = Modifier.weight(1f)
                    )
                    ProgressMiniStat(
                        label = "Water",
                        value = if (waterMl >= 1000) String.format(Locale.US, "%.1f L", waterMl / 1000f) else "${waterMl} ml",
                        detail = "Goal ${waterGoal} ml",
                        tint = Color(0xFF48B9FF),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressMiniStat(
    label: String,
    value: String,
    detail: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(detail, color = TextMuted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun HomeQuickAccessRow(
    onStudy: () -> Unit,
    onMoney: () -> Unit,
    onHabits: () -> Unit,
    onMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick Access",
                color = TextWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Your spaces",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeAccessTile(
                title = "Study",
                icon = Icons.Default.MenuBook,
                tint = CyanBrand,
                modifier = Modifier.weight(1f),
                onClick = onStudy
            )
            HomeAccessTile(
                title = "Money",
                icon = Icons.Default.AccountBalanceWallet,
                tint = OrangeAccent,
                modifier = Modifier.weight(1f),
                onClick = onMoney
            )
            HomeAccessTile(
                title = "Habits",
                icon = Icons.Default.FavoriteBorder,
                tint = Color(0xFFFB7185),
                modifier = Modifier.weight(1f),
                onClick = onHabits
            )
            HomeAccessTile(
                title = "More",
                icon = Icons.Default.GridView,
                tint = PurpleAccent,
                modifier = Modifier.weight(1f),
                onClick = onMore
            )
        }
    }
}

@Composable
private fun HomeAccessTile(
    title: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardInner),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
            }
            Text(
                text = title,
                color = TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==========================================
// 1. HOME HEADER (DYNAMIC LOCATION, LIVE WEATHER, BRAND, DYNAMIC GREETING)
// ==========================================
@Composable
private fun HomeHeader(
    profileName: String,
    profilePrefs: android.content.SharedPreferences,
    currentStreak: Int,
    onOpenCustomize: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var city by remember {
        mutableStateOf(
            profilePrefs.getString("weather_city", "Coimbatore")?.ifBlank { "Coimbatore" } ?: "Coimbatore"
        )
    }
    var weatherLabel by remember {
        mutableStateOf(profilePrefs.getString("weather_city_label", null))
    }
    var checkedAt by remember {
        mutableLongStateOf(profilePrefs.getLong("weather_city_checked_at", 0L))
    }
    var isLoading by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }
    var cityInput by remember { mutableStateOf(city) }

    fun refreshWeather(cityName: String, lat: Double? = null, lon: Double? = null) {
        if (isLoading) return
        isLoading = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (lat != null && lon != null) {
                        fetchLifeTrackWeatherByCoords(lat, lon, cityName)
                    } else {
                        fetchLifeTrackWeather(cityName)
                    }
                }
            }
            result.onSuccess { raw ->
                weatherLabel = raw
                checkedAt = System.currentTimeMillis()
                city = cityName
                val editor = profilePrefs.edit()
                    .putString("weather_city", cityName)
                    .putString("weather_city_label", raw)
                    .putLong("weather_city_checked_at", checkedAt)
                if (lat != null && lon != null) {
                    editor.putString("weather_lat", lat.toString())
                    editor.putString("weather_lon", lon.toString())
                }
                editor.apply()
            }.onFailure {
                city = cityName
                profilePrefs.edit().putString("weather_city", cityName).apply()
            }
            isLoading = false
        }
    }

    LaunchedEffect(city) {
        if (checkedAt == 0L || weatherLabel == null) {
            val savedLat = profilePrefs.getString("weather_lat", null)?.toDoubleOrNull()
            val savedLon = profilePrefs.getString("weather_lon", null)?.toDoubleOrNull()
            refreshWeather(city, savedLat, savedLon)
        }
    }

    val weatherDisplay = remember(city, weatherLabel) {
        if (weatherLabel != null && weatherLabel!!.contains(" · ")) {
            val parts = weatherLabel!!.split(" · ")
            val loc = parts.getOrNull(0) ?: city
            val t = parts.getOrNull(1) ?: "—"
            val c = parts.getOrNull(2) ?: "Weather unavailable"
            val (icon, color) = when {
                c.contains("Rain", true) || c.contains("Drizzle", true) || c.contains("Shower", true) ->
                    Icons.Default.WaterDrop to Color(0xFF38BDF8)
                c.contains("Cloud", true) || c.contains("Overcast", true) || c.contains("Fog", true) ->
                    Icons.Default.Cloud to Color(0xFF94A3B8)
                c.contains("Thunder", true) ->
                    Icons.Default.FlashOn to Color(0xFFF59E0B)
                c.contains("Snow", true) ->
                    Icons.Default.AcUnit to Color(0xFF93C5FD)
                else ->
                    Icons.Default.WbSunny to Color(0xFFFFA726)
            }
            WeatherDisplay(loc, t, c, icon, color)
        } else {
            val defaultLoc = if (city.contains(",")) city else "$city, Tamil Nadu"
            WeatherDisplay(defaultLoc, "—", "Weather unavailable", Icons.Default.CloudOff, TextMuted)
        }
    }

    val dayFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.US).format(Date())

    // Dynamic greeting based on time of day
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = remember(currentHour, profileName) {
        val nameSuffix = if (profileName.isNotBlank()) ", $profileName" else ""
        when {
            currentHour < 12 -> GreetingInfo(
                "Good Morning$nameSuffix!",
                "Small steps today,",
                "big results tomorrow.",
                listOf(Color(0xFF0F2B48), Color(0xFF1A1A38), Color(0xFF4A2526))
            )
            currentHour < 17 -> GreetingInfo(
                "Good Afternoon$nameSuffix!",
                "Keep up the momentum,",
                "stay active & hydrated.",
                listOf(Color(0xFF142850), Color(0xFF1B3B6F), Color(0xFF385A7C))
            )
            else -> GreetingInfo(
                "Good Evening$nameSuffix!",
                "Great work today,",
                "rest well for tomorrow.",
                listOf(Color(0xFF0B132B), Color(0xFF1C2541), Color(0xFF3A506B))
            )
        }
    }

    val context = LocalContext.current
    var autoLocationPermissionGranted by remember {
        mutableStateOf(LocationHelper.hasLocationPermission(context))
    }

    val locPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        autoLocationPermissionGranted = granted
        if (granted) {
            scope.launch {
                when (val res = LocationHelper.detectLocation(context)) {
                    is LocationResult.Success -> {
                        if (res.cityName.isNotBlank()) {
                            cityInput = res.cityName
                            refreshWeather(res.cityName, res.latitude, res.longitude)
                            showCityDialog = false
                        }
                    }
                    is LocationResult.Error -> {
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    }
                    is LocationResult.ServicesDisabled -> {
                        Toast.makeText(context, "Location services disabled", Toast.LENGTH_SHORT).show()
                    }
                    is LocationResult.PermissionRequired -> {
                        Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(context, "Location permission denied. Weather will use the saved city.", Toast.LENGTH_SHORT).show()
        }
    }

    // Ask once when Home first appears, then refresh location and weather automatically
    // while Home remains visible. Manual GPS detection is no longer required each time.
    LaunchedEffect(autoLocationPermissionGranted) {
        if (!autoLocationPermissionGranted) {
            if (!profilePrefs.getBoolean("location_auto_permission_prompted", false)) {
                profilePrefs.edit().putBoolean("location_auto_permission_prompted", true).apply()
                locPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        } else {
            while (true) {
                when (val result = LocationHelper.detectLocation(context)) {
                    is LocationResult.Success -> {
                        if (result.cityName.isNotBlank()) {
                            cityInput = result.cityName
                            refreshWeather(result.cityName, result.latitude, result.longitude)
                        }
                    }
                    is LocationResult.Error -> Unit // Keep the last successful weather/location on transient failures.
                    is LocationResult.ServicesDisabled -> Unit
                    is LocationResult.PermissionRequired -> {
                        autoLocationPermissionGranted = false
                    }
                }
                kotlinx.coroutines.delay(30 * 60 * 1000L)
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        // Status Row: Location | Weather | Notifications | Profile Avatar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location Pin + City + Date (Clickable to change location)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        cityInput = city
                        showCityDialog = true
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Change Location",
                    tint = CyanBrand,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            weatherDisplay.location,
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Edit Location",
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(dayFmt, color = TextMuted, fontSize = 11.sp)
                }
            }

            // Live Weather Widget (Clickable to refresh / change city)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        cityInput = city
                        showCityDialog = true
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(weatherDisplay.color.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        weatherDisplay.icon,
                        contentDescription = "Weather",
                        tint = weatherDisplay.color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (isLoading) "..." else weatherDisplay.temp,
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isLoading) "Updating" else weatherDisplay.condition,
                        color = TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Notification Bell with Badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardInner)
                    .clickable { onNavigate("History") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // User Profile Avatar
            val userProfile = remember { UserProfileRepository.getProfile(context) }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, CyanBrand, CircleShape)
                    .background(Color(0xFF1E293B))
                    .clickable { onNavigate("Profile") },
                contentAlignment = Alignment.Center
            ) {
                AvatarVisual(profile = userProfile, sizeDp = 38)
            }
        }

        // Redesigned Home hero: compact brand header, clear greeting and daily progress.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            color = CardBg,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(CyanBrand.copy(alpha = 0.5f), PurpleAccent.copy(alpha = 0.35f), CardBorder)
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                greeting.gradient.first().copy(alpha = 0.98f),
                                Color(0xFF10182B),
                                CardBg
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.app_logo),
                            contentDescription = "LifeTrack",
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("LIFETRACK", color = CyanBrand, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("Your daily overview", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.clickable(onClick = onOpenCustomize)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = "Customize Home", tint = CyanBrand, modifier = Modifier.size(16.dp))
                                Text("Edit", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            greeting.title,
                            color = TextWhite,
                            fontSize = 20.sp,
                            lineHeight = 25.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${greeting.line1} ${greeting.line2}",
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = OrangeAccent.copy(alpha = 0.13f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.30f)),
                            modifier = Modifier.weight(1f).clickable { onNavigate("Habits & Routines") }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(18.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(if (currentStreak > 0) "$currentStreak days" else "Start today", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Current streak", color = TextMuted, fontSize = 10.sp)
                                }
                            }
                        }
                        Surface(
                            color = CyanBrand.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CyanBrand.copy(alpha = 0.24f)),
                            modifier = Modifier.weight(1f).clickable { onNavigate("Goals") }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(Icons.Default.Insights, contentDescription = null, tint = CyanBrand, modifier = Modifier.size(18.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("View goals", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Track your targets", color = TextMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Location & Weather Dialog
    if (showCityDialog) {
        AlertDialog(
            onDismissRequest = { showCityDialog = false },
            containerColor = Color(0xFF0F172A),
            titleContentColor = Color.White,
            textContentColor = Color(0xFF94A3B8),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(32.dp).background(CyanBrand.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = CyanBrand, modifier = Modifier.size(18.dp))
                    }
                    Text("Change Location & Weather", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (fineGranted || coarseGranted) {
                                scope.launch {
                                    when (val res = LocationHelper.detectLocation(context)) {
                                        is LocationResult.Success -> {
                                            if (res.cityName.isNotBlank()) {
                                                cityInput = res.cityName
                                                refreshWeather(res.cityName, res.latitude, res.longitude)
                                                showCityDialog = false
                                            }
                                        }
                                        is LocationResult.Error -> {
                                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                        is LocationResult.ServicesDisabled -> {
                                            Toast.makeText(context, "Location services disabled", Toast.LENGTH_SHORT).show()
                                        }
                                        is LocationResult.PermissionRequired -> {
                                            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                locPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CyanBrand.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = CyanBrand, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Detect Current Location (GPS)", color = CyanBrand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Text(
                        "Or search/choose your city manually:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = { cityInput = it },
                        label = { Text("City or Town Name") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = CyanBrand) },
                        trailingIcon = {
                            if (cityInput.isNotEmpty()) {
                                IconButton(onClick = { cityInput = "" }) {
                                    Icon(Icons.Default.Close, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanBrand,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = CyanBrand
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Quick Suggestions:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5E1))

                    val quickCities = listOf("Coimbatore", "Chennai", "Bengaluru", "Mumbai", "Madurai", "Delhi", "Hyderabad", "Salem")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickCities.forEach { suggestion ->
                            val isSelected = cityInput.trim().equals(suggestion, ignoreCase = true)
                            Surface(
                                color = if (isSelected) CyanBrand.copy(alpha = 0.25f) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) CyanBrand else Color(0xFF334155)),
                                modifier = Modifier.clickable {
                                    cityInput = suggestion
                                }
                            ) {
                                Text(
                                    suggestion,
                                    color = if (isSelected) CyanBrand else Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = cityInput.trim()
                        if (target.isNotBlank()) {
                            refreshWeather(target)
                            showCityDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanBrand),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Update Location", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCityDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

// ==========================================
// 2. TODAY'S PROGRESS CARD (4 RINGS)
// ==========================================
@Composable
private fun TodaysProgressCard(
    studyMins: Int, studyGoal: Int, studyPct: Float, waterMl: Int, waterGoal: Int, waterPct: Float,
    habitsDone: Int, habitsTotal: Int, onSeeAll: () -> Unit,
    onStudyClick: () -> Unit = {}, onWaterClick: () -> Unit = {},
    onHabitsClick: () -> Unit = {}, onRoutinesClick: () -> Unit = {}, onScreenTimeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val today = java.time.LocalDate.now().toString()
    val routines = remember(today) { HabitRoutineRepository.getRoutines(context) }
    val sessions = remember(today) { HabitRoutineRepository.getSessionsForDate(context, today) }
    val routinesCompleted = routines.count { r -> sessions.any { it.routineId == r.id && it.isCompleted } }
    GlassSurface(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Daily Progress", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.clickable(onClick = onSeeAll), verticalAlignment = Alignment.CenterVertically) {
                    Text("See all", color = CyanBrand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ChevronRight, null, tint = CyanBrand, modifier = Modifier.size(16.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeProgressTile("Study", if (studyGoal > 0) "${studyMins / 60}h ${studyMins % 60}m" else if (studyMins > 0) "${studyMins}m" else "—", if (studyGoal > 0) "Goal ${studyGoal / 60}h · ${(studyPct * 100).toInt()}%" else "Set a study goal", if (studyGoal > 0) studyPct else 0f, EmeraldAccent, Icons.Default.MenuBook, onStudyClick, Modifier.weight(1f))
                HomeProgressTile("Water", if (waterMl >= 1000) String.format(Locale.US, "%.1f L", waterMl / 1000f) else if (waterMl > 0) "${waterMl} ml" else "—", if (waterGoal > 0) "Goal ${String.format(Locale.US, "%.1f", waterGoal / 1000f)} L · ${(waterPct * 100).toInt()}%" else "Set a water goal", if (waterGoal > 0) waterPct else 0f, BlueAccent, Icons.Default.WaterDrop, onWaterClick, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeProgressTile("Habits", if (habitsTotal > 0) "$habitsDone/$habitsTotal" else "—", if (habitsTotal > 0) "${(habitsDone.toFloat() / habitsTotal * 100).toInt()}% completed" else "No habits scheduled", if (habitsTotal > 0) habitsDone.toFloat() / habitsTotal else 0f, OrangeAccent, Icons.Default.CheckCircle, onHabitsClick, Modifier.weight(1f))
                HomeProgressTile("Routines", if (routines.isNotEmpty()) "$routinesCompleted/${routines.size}" else "—", if (routines.isNotEmpty()) "${(routinesCompleted.toFloat() / routines.size * 100).toInt()}% completed" else "No routines configured", if (routines.isNotEmpty()) routinesCompleted.toFloat() / routines.size else 0f, PurpleAccent, Icons.Default.Replay, onRoutinesClick, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeProgressTile(label: String, value: String, detail: String, progress: Float, color: Color, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.12f), CardInner)))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
            Text(label, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(color.copy(alpha = 0.16f), -90f, 360f, false, style = Stroke(5.dp.toPx()))
                    drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                }
                Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(value, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Text(detail, color = TextMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RingGauge(
    label: String,
    value: String,
    goal: String,
    percentage: String,
    progress: Float,
    color: Color,
    icon: ImageVector,
    ringSize: androidx.compose.ui.unit.Dp = 96.dp,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(if (LifeTrackColors.isMotionReduced) 0 else 1000, easing = EaseOutCubic),
        label = "ring_anim"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
        modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .width(ringSize + if (compact) 2.dp else 8.dp)
    ) {
        Box(
            modifier = Modifier.size(ringSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 7.5.dp.toPx()
                // Track
                drawArc(
                    color = color.copy(alpha = 0.16f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
                // Progress Arc
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(color.copy(alpha = 0.6f), color)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animProgress,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = color, modifier = Modifier.size(if (compact) 12.dp else 19.dp))
                Spacer(Modifier.height(2.dp))
                Text(value, color = TextWhite, fontSize = if (compact) 8.sp else 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Text(percentage, color = color, fontSize = if (compact) 9.sp else 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
        Text(label, color = TextWhite, fontSize = if (compact) 8.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

// ==========================================
// 3. SCREEN TIME & TODAY'S TASKS ROW
// ==========================================
@Composable
private fun ScreenTimeCard(
    modifier: Modifier = Modifier,
    onSeeAll: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(ScreenTimeRepository.hasUsageAccessPermission(context)) }

    DisposableEffect(Unit) {
        hasPermission = ScreenTimeRepository.hasUsageAccessPermission(context)
        onDispose {}
    }

    GlassSurface(modifier) {
        Column(modifier = Modifier.padding(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Screen Time", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.clickable(onClick = onSeeAll),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("See all", color = CyanBrand, fontSize = 12.sp)
                    Icon(Icons.Default.ChevronRight, null, tint = CyanBrand, modifier = Modifier.size(15.dp))
                }
            }

            if (!hasPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = CyanBrand, modifier = Modifier.size(26.dp))
                    Text(
                        "Usage Access Required",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Enable permission to view live app usage",
                        color = TextMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { ScreenTimeRepository.openUsageAccessSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanBrand),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Enable", color = Color(0xFF0F172A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val bounds = remember { ScreenTimeRepository.getTimeBounds(ScreenTimePeriod.TODAY) }
                val appUsageList = remember(hasPermission) {
                    ScreenTimeRepository.queryAppUsage(context, bounds.first, bounds.second)
                }
                val totalMinutes = remember(appUsageList) { appUsageList.sumOf { it.totalTimeMs } / 60000 }
                val totalHours = totalMinutes / 60
                val remMins = totalMinutes % 60
                val formattedTotal = if (totalHours > 0) "${totalHours}h ${remMins}m" else "${remMins}m"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total time circular badge
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(CardInner, CircleShape)
                            .border(2.dp, CyanBrand.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhoneAndroid, null, tint = CyanBrand, modifier = Modifier.size(15.dp))
                            Text(formattedTotal, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Today", color = TextMuted, fontSize = 8.sp)
                        }
                    }

                    // Top apps (up to 3)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (appUsageList.isEmpty()) {
                            Text("No app usage recorded yet today", color = TextMuted, fontSize = 10.sp)
                        } else {
                            appUsageList.take(3).forEach { app ->
                                RealAppUsageRow(app)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealAppUsageRow(app: AppUsageInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (app.iconBitmap != null) {
            Image(
                bitmap = app.iconBitmap,
                contentDescription = app.appName,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(app.category.color, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(app.appName.take(1).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            app.appName,
            color = TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(app.durationFormatted, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun TodaysTasksCard(
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onSeeAll: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val todayStr = remember { java.time.LocalDate.now().toString() }
    val tasks = remember(refreshKey) {
        TaskRepository.getTasks(context).filter { it.dueDate == todayStr }
    }
    val completedCount = remember(tasks) { tasks.count { it.isCompleted } }

    GlassSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Today's Tasks", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (tasks.isNotEmpty()) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "$completedCount / ${tasks.size} done",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(PurpleAccent, CircleShape)
                            .clickable(onClick = onAdd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Task", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (tasks.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { completedCount.toFloat() / tasks.size },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = GreenIncome,
                    trackColor = Color(0xFF26354B)
                )
            }

            if (tasks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "No tasks scheduled for today",
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onAdd,
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                        modifier = Modifier.height(30.dp),
                        border = BorderStroke(1.dp, PurpleAccent.copy(alpha = 0.5f))
                    ) {
                        Text("+ Add Task", color = PurpleAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tasks.take(4).forEach { task ->
                        RealTaskItem(
                            title = task.title,
                            time = task.dueTime ?: task.priority,
                            completed = task.isCompleted,
                            onToggle = {
                                TaskRepository.toggleTaskCompletion(context, task.id)
                                refreshKey++
                            }
                        )
                    }
                    if (tasks.size > 4) {
                        Text(
                            "+ ${tasks.size - 4} more tasks",
                            color = CyanBrand,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable(onClick = onSeeAll)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealTaskItem(title: String, time: String, completed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (completed) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(GreenIncome, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(1.5.dp, TextMuted.copy(alpha = 0.5f), CircleShape)
            )
        }
        Text(
            title,
            color = if (completed) TextMuted else TextWhite,
            fontSize = 11.sp,
            fontWeight = if (completed) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(time, color = TextMuted, fontSize = 9.sp)
    }
}

// ==========================================
// 4. EXPANDED MONEY OVERVIEW (FULL WIDTH)
// ==========================================
@Composable
private fun ExpandedMoneyOverviewCard(
    modifier: Modifier = Modifier,
    balance: Double,
    income: Double,
    expenses: Double,
    savedPct: Float,
    recentTransactions: List<LogEntry>,
    todayExpenses: Double, yesterdayExpenses: Double,
    todayExpenseCount: Int, yesterdayExpenseCount: Int,
    onSeeAll: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(PinkAccent.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = PinkAccent, modifier = Modifier.size(18.dp))
                        }
                        Text("Money Overview", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.clickable(onClick = onSeeAll),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("See all", color = CyanBrand, fontSize = 13.sp)
                        Icon(Icons.Default.ChevronRight, null, tint = CyanBrand, modifier = Modifier.size(15.dp))
                    }
                }

                // Balance & Income/Expense Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Net Balance", color = TextMuted, fontSize = 12.sp)
                        Text(
                            "₹ ${String.format(Locale.US, "%,.2f", balance)}",
                            color = if (balance >= 0) TextWhite else RedExpense,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Income Chip
                        Surface(
                            color = GreenIncome.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, GreenIncome.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ArrowDownward, null, tint = GreenIncome, modifier = Modifier.size(15.dp))
                                Column {
                                    Text("Income", color = TextMuted, fontSize = 9.sp)
                                    Text("₹ ${String.format(Locale.US, "%,.0f", income)}", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Expense Chip
                        Surface(
                            color = RedExpense.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, RedExpense.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ArrowUpward, null, tint = RedExpense, modifier = Modifier.size(15.dp))
                                Column {
                                    Text("Expense", color = TextMuted, fontSize = 9.sp)
                                    Text("₹ ${String.format(Locale.US, "%,.0f", expenses)}", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Saved Progress Bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Monthly Savings Rate", color = TextMuted, fontSize = 11.sp)
                        Text(
                            "${savedPct.toInt()}% saved",
                            color = if (savedPct > 0) GreenIncome else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { savedPct / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = GreenIncome,
                        trackColor = Color(0xFF1E293B)
                    )
                }

                Divider(color = CardBorder)
                Text("Spending · Today vs Yesterday", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoneyDayTile("Today", todayExpenses, todayExpenseCount, maxOf(todayExpenses, yesterdayExpenses), PinkAccent, Modifier.weight(1f))
                    MoneyDayTile("Yesterday", yesterdayExpenses, yesterdayExpenseCount, maxOf(todayExpenses, yesterdayExpenses), CyanBrand, Modifier.weight(1f))
                }
                val difference = todayExpenses - yesterdayExpenses
                val comparison = when {
                    todayExpenses == 0.0 && yesterdayExpenses == 0.0 -> "No spending recorded on either day"
                    yesterdayExpenses == 0.0 -> "Increased by ₹ ${String.format(Locale.US, "%,.0f", difference)} · percentage unavailable"
                    difference > 0.0 -> "₹ ${String.format(Locale.US, "%,.0f", difference)} more than yesterday (${String.format(Locale.US, "%.0f", difference / yesterdayExpenses * 100)}%)"
                    difference < 0.0 -> "₹ ${String.format(Locale.US, "%,.0f", -difference)} less than yesterday (${String.format(Locale.US, "%.0f", -difference / yesterdayExpenses * 100)}%)"
                    else -> "No change from yesterday"
                }
                Text(comparison, color = when { todayExpenses < yesterdayExpenses -> GreenIncome; todayExpenses > yesterdayExpenses -> RedExpense; else -> TextMuted }, fontSize = 11.sp)

                // Keep the Home money card glanceable; transaction history lives in the full Money screen.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardInner.copy(alpha = 0.7f))
                        .clickable(onClick = onSeeAll)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Recent transactions", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (recentTransactions.isEmpty()) "No transactions yet this month" else "${recentTransactions.size} recent entries · View history",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = CyanBrand, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ==========================================
// 5. DAILY HABITS & ROUTINES ROW
// ==========================================
@Composable
private fun MoneyDayTile(title: String, amount: Double, count: Int, maxAmount: Double, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.20f), RoundedCornerShape(14.dp)).padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = TextMuted, fontSize = 11.sp)
        Text("₹ ${String.format(Locale.US, "%,.0f", amount)}", color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text("$count transaction${if (count == 1) "" else "s"}", color = TextMuted, fontSize = 10.sp)
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(if (maxAmount > 0.0) (amount / maxAmount).toFloat().coerceIn(0f, 1f) else 0f).fillMaxHeight().clip(RoundedCornerShape(50)).background(tint))
        }
    }
}

@Composable
private fun DailyHabitsCard(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier,
    onSeeAll: () -> Unit
) {
    GlassSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daily Habits", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.clickable(onClick = onSeeAll),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("See all", color = CyanBrand, fontSize = 12.sp)
                    Icon(Icons.Default.ChevronRight, null, tint = CyanBrand, modifier = Modifier.size(15.dp))
                }
            }

            // Habits Item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardInner)
                    .clickable(onClick = onSeeAll)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(GreenIncome.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DateRange, null, tint = GreenIncome, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Daily Habits", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (total == 0) "No habits scheduled today" else "$completed / $total completed",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }

            // Dots Row (Only shown if total > 0)
            if (total > 0) {
                val displayCount = minOf(total, 10)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(displayCount) { index ->
                        if (index < completed) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(GreenIncome, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .border(1.dp, TextMuted.copy(alpha = 0.5f), CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutinesCard(
    modifier: Modifier = Modifier,
    onSeeAll: () -> Unit
) {
    val context = LocalContext.current
    val today = java.time.LocalDate.now().toString()
    val routines = HabitRoutineRepository.getRoutines(context).take(2)
    val todaySessions = HabitRoutineRepository.getSessionsForDate(context, today)

    GlassSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Routines", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.clickable(onClick = onSeeAll),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("See all", color = CyanBrand, fontSize = 12.sp)
                    Icon(Icons.Default.ChevronRight, null, tint = CyanBrand, modifier = Modifier.size(15.dp))
                }
            }

            if (routines.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardInner)
                        .clickable(onClick = onSeeAll)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Schedule, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    Text(
                        "No routines created yet · Tap to add",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            } else {
                routines.forEach { routine ->
                    val color = HabitRoutineIconHelper.parseColor(routine.colorHex)
                    val icon = HabitRoutineIconHelper.getIcon(routine.iconName)
                    val completedToday = todaySessions.any { it.routineId == routine.id && it.isCompleted }
                    val stepCount = routine.steps.size
                    RoutineRow(
                        icon = icon,
                        tint = color,
                        name = routine.name,
                        progressText = if (completedToday) "Completed today · ${routine.startTime}" else "Not completed today · ${routine.startTime}",
                        completed = if (completedToday) stepCount else 0,
                        total = stepCount.coerceAtLeast(1),
                        onClick = onSeeAll
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineRow(
    icon: ImageVector,
    tint: Color,
    name: String,
    progressText: String,
    completed: Int,
    total: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardInner)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(progressText, color = TextMuted, fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val displayTotal = minOf(total, 5)
            repeat(displayTotal) { i ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (i < completed) tint else TextMuted.copy(alpha = 0.3f), CircleShape)
                )
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(15.dp))
    }
}

// ==========================================
// 6. NOTES & YESTERDAY VS TODAY ROW
// ==========================================
@Composable
private fun NotesCard(
    modifier: Modifier = Modifier,
    onQuickNote: () -> Unit,
    onSeeAll: () -> Unit
) {
    val context = LocalContext.current
    val notes = remember { NoteRepository.getNotes(context) }
    val recentNote = notes.firstOrNull()

    GlassSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notes", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.clickable(onClick = onSeeAll),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("See all", color = CyanBrand, fontSize = 12.sp)
                    Icon(Icons.Default.ChevronRight, null, tint = CyanBrand, modifier = Modifier.size(15.dp))
                }
            }

            // Quick Note Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardInner)
                    .clickable(onClick = onQuickNote)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(PurpleAccent.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.EditNote, null, tint = PurpleAccent, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Quick Note", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Capture ideas instantly", color = TextMuted, fontSize = 9.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(15.dp))
            }

            // Recent Notes Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardInner)
                    .clickable(onClick = onSeeAll)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(YellowAccent.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.WbIncandescent, null, tint = YellowAccent, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (recentNote != null) recentNote.title.ifEmpty { "Recent Note" } else "Recent Notes",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (notes.isEmpty()) "No notes yet · Tap to write" else "${notes.size} ${if (notes.size == 1) "note" else "notes"} saved",
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun YesterdayVsTodayCard(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
    onSeeAll: () -> Unit
) {
    val todayFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.US).format(Date()) }
    val ydayFmt = remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        SimpleDateFormat("d MMM yyyy", Locale.US).format(cal.time)
    }

    val todayDateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val ydayDateStr = remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    val todayStudyMin = logs.filter { it.category.equals("Study", ignoreCase = true) && it.date == todayDateStr }.sumOf { it.amount.toDoubleOrNull()?.toInt() ?: 0 }
    val ydayStudyMin = logs.filter { it.category.equals("Study", ignoreCase = true) && it.date == ydayDateStr }.sumOf { it.amount.toDoubleOrNull()?.toInt() ?: 0 }

    val todayWaterMl = logs.filter { it.category.equals("Water", ignoreCase = true) && it.date == todayDateStr }.sumOf { it.amount.toDoubleOrNull()?.toInt() ?: 0 }
    val ydayWaterMl = logs.filter { it.category.equals("Water", ignoreCase = true) && it.date == ydayDateStr }.sumOf { it.amount.toDoubleOrNull()?.toInt() ?: 0 }

    val todayCare = logs.count { it.category.equals("Care", ignoreCase = true) && it.date == todayDateStr }
    val ydayCare = logs.count { it.category.equals("Care", ignoreCase = true) && it.date == ydayDateStr }

    fun fmtMin(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    fun fmtWater(ml: Int) = if (ml >= 1000) String.format(Locale.US, "%.1fL", ml / 1000f) else "${ml}ml"
    fun diffStr(cur: Int, prev: Int): String? {
        if (prev <= 0 || cur <= prev) return null
        val pct = ((cur - prev).toFloat() / prev * 100).toInt()
        return "+$pct%"
    }

    GlassSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Yesterday vs Today", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.clickable(onClick = onSeeAll),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("See all", color = CyanBrand, fontSize = 12.sp)
                    Icon(Icons.Default.ChevronRight, null, tint = CyanBrand, modifier = Modifier.size(15.dp))
                }
            }

            // Two Columns: Yesterday vs Today
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Yesterday Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Yesterday", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(ydayFmt, color = TextMuted, fontSize = 9.sp)
                    Spacer(Modifier.height(2.dp))
                    CmpItem(Icons.Default.MenuBook, EmeraldAccent, fmtMin(ydayStudyMin), null)
                    CmpItem(Icons.Default.WaterDrop, BlueAccent, fmtWater(ydayWaterMl), null)
                    CmpItem(Icons.Default.Favorite, PurpleAccent, "$ydayCare done", null)
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(80.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                        .align(Alignment.CenterVertically)
                )

                // Today Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Today", color = CyanBrand, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(todayFmt, color = TextMuted, fontSize = 9.sp)
                    Spacer(Modifier.height(2.dp))
                    CmpItem(Icons.Default.MenuBook, EmeraldAccent, fmtMin(todayStudyMin), diffStr(todayStudyMin, ydayStudyMin))
                    CmpItem(Icons.Default.WaterDrop, BlueAccent, fmtWater(todayWaterMl), diffStr(todayWaterMl, ydayWaterMl))
                    CmpItem(Icons.Default.Favorite, PurpleAccent, "$todayCare done", diffStr(todayCare, ydayCare))
                }
            }
        }
    }
}

@Composable
private fun CmpItem(icon: ImageVector, tint: Color, value: String, diff: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(value, color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        if (diff != null) {
            Text("↑ $diff", color = GreenIncome, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 7. UPCOMING CARD (FULL WIDTH)
// ==========================================
@Composable
private fun UpcomingCard(
    onSeeAll: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val tasks = remember(refreshKey) {
        val todayTasks = TaskRepository.getTodayTasks(context).filter { !it.isCompleted }
        val upcomingTasks = TaskRepository.getUpcomingTasks(context).filter { !it.isCompleted }
        (todayTasks + upcomingTasks).distinctBy { it.id }.take(4)
    }

    GlassSurface(Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Upcoming", color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.clickable(onClick = onSeeAll),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("See all", color = CyanBrand, fontSize = 13.sp)
                    Icon(Icons.Default.ChevronRight, null, tint = CyanBrand, modifier = Modifier.size(16.dp))
                }
            }

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardInner)
                        .clickable(onClick = onSeeAll)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = CyanBrand.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                        Text("No upcoming tasks", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Tap to plan your tasks and stay productive", color = TextMuted, fontSize = 10.sp)
                    }
                }
            } else {
                tasks.forEach { task ->
                    val priorityColor = when (task.priority.lowercase(Locale.ROOT)) {
                        "high" -> RedExpense
                        "medium" -> YellowAccent
                        else -> CyanBrand
                    }
                    val subtitle = buildString {
                        if (task.dueDate.isNotEmpty()) append(task.dueDate)
                        if (!task.dueTime.isNullOrEmpty()) {
                            if (isNotEmpty()) append(", ")
                            append(task.dueTime)
                        }
                        if (isEmpty()) append(task.priority.replaceFirstChar { it.uppercase() } + " Priority")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(priorityColor.copy(alpha = 0.08f))
                            .padding(10.dp)
                            .clickable {
                                TaskRepository.toggleTaskCompletion(context, task.id)
                                refreshKey++
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(priorityColor.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (task.priority.equals("high", true)) Icons.Default.PriorityHigh else Icons.Default.CheckCircle,
                                null,
                                tint = priorityColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(Modifier.weight(1f)) {
                            Text(task.title, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subtitle, color = TextMuted, fontSize = 10.sp)
                        }

                        if (task.subtasks.isNotEmpty()) {
                            val compCount = task.subtasks.count { it.isCompleted }
                            Text(
                                "$compCount/${task.subtasks.size}",
                                color = CyanBrand,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(1.5.dp, if (task.isCompleted) GreenIncome else TextMuted.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                                .background(if (task.isCompleted) GreenIncome else Color.Transparent, RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.isCompleted) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Glassmorphic Surface Container with Subtle Purple/Cyan Neon Border Glow
@Composable
private fun GlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    CyanBrand.copy(alpha = 0.32f),
                    PurpleAccent.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.08f)
                ),
                start = Offset(0f, 0f),
                end = Offset(400f, 400f)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PurpleAccent.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        radius = 450f,
                        center = Offset(100f, 0f)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                content = content
            )
        }
    }
}

// ----------------------------------------------------
// Time-Aware Smart Card (Morning / Midday / Evening)
// ----------------------------------------------------
@Composable
private fun TimeAwareSmartCard(
    currentHour: Int,
    currentStreak: Int,
    dailyProgressPct: Int,
    studyMins: Int,
    studyGoal: Int,
    waterMl: Int,
    waterGoal: Int,
    completedHabits: Int,
    totalHabits: Int,
    onNavigate: (String) -> Unit
) {
    val isMorning = currentHour in 5..11
    val isAfternoon = currentHour in 12..16

    val (cardTitle, cardSubtitle) = when {
        isMorning -> "Morning Momentum" to if (currentStreak > 0) "🔥 $currentStreak Day Streak · Kickstart habits & focus" else "Kickstart routines, top tasks & hydration"
        isAfternoon -> "Midday Energy & Pace" to "Keep momentum high · Deep focus & stay hydrated"
        else -> "Evening Wind-Down & Recap" to "Review daily wins · $dailyProgressPct% completed today"
    }

    val accentColor = when {
        isMorning -> CyanBrand
        isAfternoon -> OrangeAccent
        else -> PurpleAccent
    }

    val bgGradient = when {
        isMorning -> listOf(Color(0xFF0B2138), Color(0xFF141738), Color(0xFF0B1424))
        isAfternoon -> listOf(Color(0xFF26190B), Color(0xFF191B33), Color(0xFF0E1626))
        else -> listOf(Color(0xFF1D0E35), Color(0xFF131A36), Color(0xFF090E1A))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.2.dp,
            Brush.linearGradient(
                listOf(accentColor.copy(alpha = 0.45f), PurpleAccent.copy(alpha = 0.3f), Color.White.copy(alpha = 0.12f))
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(bgGradient))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(accentColor.copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isMorning -> Icons.Default.WbSunny
                                    isAfternoon -> Icons.Default.Bolt
                                    else -> Icons.Default.Bedtime
                                },
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                cardTitle,
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                cardSubtitle,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = if (isMorning) "Morning Focus" else if (isAfternoon) "Midday" else "Evening Recap",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Dynamic Contextual Content
                when {
                    isMorning -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SmartMiniPill(
                                title = "Habits & Routines",
                                subtitle = if (totalHabits > 0) "$completedHabits of $totalHabits Done" else "Ready to start",
                                icon = Icons.Default.Checklist,
                                tint = CyanBrand,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate("Habits & Routines") }
                            )
                            SmartMiniPill(
                                title = "Morning Water",
                                subtitle = if (waterMl > 0) "${waterMl}ml logged" else "Hydrate early",
                                icon = Icons.Default.WaterDrop,
                                tint = BlueAccent,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate("Water") }
                            )
                        }
                    }
                    isAfternoon -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SmartMiniPill(
                                title = "Study & Focus",
                                subtitle = "${studyMins / 60}h ${studyMins % 60}m / ${studyGoal / 60}h goal",
                                icon = Icons.Default.Timer,
                                tint = OrangeAccent,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate("Study") }
                            )
                            SmartMiniPill(
                                title = "Hydration Pace",
                                subtitle = "${waterMl} / ${waterGoal}ml",
                                icon = Icons.Default.WaterDrop,
                                tint = CyanBrand,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate("Water") }
                            )
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SmartMiniPill(
                                title = "Day Completed",
                                subtitle = "$dailyProgressPct% Achieved",
                                icon = Icons.Default.TrackChanges,
                                tint = EmeraldAccent,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate("Insights") }
                            )
                            SmartMiniPill(
                                title = "Habits",
                                subtitle = "$completedHabits / $totalHabits done",
                                icon = Icons.Default.CheckCircle,
                                tint = PurpleAccent,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate("Habits & Routines") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartMiniPill(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.75f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(tint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(title, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

// ----------------------------------------------------
// Customizable Dashboard Dialog & Toggles
// ----------------------------------------------------
@Composable
private fun CustomizeDashboardDialog(
    showSmartCard: Boolean,
    onToggleSmartCard: (Boolean) -> Unit,
    showProgressRings: Boolean,
    onToggleProgressRings: (Boolean) -> Unit,
    showScreenTime: Boolean,
    onToggleScreenTime: (Boolean) -> Unit,
    showTasks: Boolean,
    onToggleTasks: (Boolean) -> Unit,
    showMoney: Boolean,
    onToggleMoney: (Boolean) -> Unit,
    showHabits: Boolean,
    onToggleHabits: (Boolean) -> Unit,
    showRoutines: Boolean,
    onToggleRoutines: (Boolean) -> Unit,
    showNotes: Boolean,
    onToggleNotes: (Boolean) -> Unit,
    showComparison: Boolean,
    onToggleComparison: (Boolean) -> Unit,
    showUpcoming: Boolean,
    onToggleUpcoming: (Boolean) -> Unit,
    layoutMode: String,
    onSelectLayoutMode: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyanBrand),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onResetDefaults) {
                Text("Reset Defaults", color = TextMuted, fontSize = 12.sp)
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CyanBrand.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = CyanBrand, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(
                        "Customize Home",
                        color = TextWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Show/hide cards & layout mode",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("CARD LAYOUT MODE", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isCompact = layoutMode != "full_width"
                    Surface(
                        color = if (isCompact) CyanBrand.copy(alpha = 0.2f) else CardInner,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isCompact) CyanBrand else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectLayoutMode("compact") }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = null, tint = if (isCompact) CyanBrand else TextMuted, modifier = Modifier.size(16.dp))
                            Text(
                                "Compact Grid",
                                color = if (isCompact) Color.White else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isCompact) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Surface(
                        color = if (!isCompact) PurpleAccent.copy(alpha = 0.2f) else CardInner,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (!isCompact) PurpleAccent else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectLayoutMode("full_width") }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FormatListBulleted, contentDescription = null, tint = if (!isCompact) PurpleAccent else TextMuted, modifier = Modifier.size(16.dp))
                            Text(
                                "Full Width",
                                color = if (!isCompact) Color.White else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (!isCompact) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("VISIBLE DASHBOARD CARDS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                CustomizeToggleRow("Time-Aware Smart Card", Icons.Default.AccessTime, CyanBrand, showSmartCard, onToggleSmartCard)
                CustomizeToggleRow("Today's Progress Rings", Icons.Default.DonutLarge, EmeraldAccent, showProgressRings, onToggleProgressRings)
                CustomizeToggleRow("Screen Time Tracker", Icons.Default.PhoneAndroid, PurpleAccent, showScreenTime, onToggleScreenTime)
                CustomizeToggleRow("Today's Tasks", Icons.Default.CheckCircle, GreenIncome, showTasks, onToggleTasks)
                CustomizeToggleRow("Money & Expenses", Icons.Default.AccountBalanceWallet, OrangeAccent, showMoney, onToggleMoney)
                CustomizeToggleRow("Daily Habits", Icons.Default.Checklist, CyanBrand, showHabits, onToggleHabits)
                CustomizeToggleRow("Daily Routines", Icons.Default.Schedule, PurpleAccent, showRoutines, onToggleRoutines)
                CustomizeToggleRow("Quick Notes", Icons.Default.EditNote, YellowAccent, showNotes, onToggleNotes)
                CustomizeToggleRow("Yesterday vs Today", Icons.Default.CompareArrows, EmeraldAccent, showComparison, onToggleComparison)
                CustomizeToggleRow("Upcoming Schedule", Icons.Default.DateRange, CyanBrand, showUpcoming, onToggleUpcoming)
            }
        },
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun CustomizeToggleRow(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = CardInner,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (checked) iconTint.copy(alpha = 0.3f) else CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(iconTint.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
                }
                Text(
                    title,
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = iconTint,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
    }
}

@Composable
private fun HomeSyncActionChip(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(tint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(
                title,
                color = TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

