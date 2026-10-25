package com.yakhrevan.lifetrack

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

// ==========================================
// DESIGN TOKENS (Matching Insights Reference)
// ==========================================
private val DarkBg          = Color(0xFF070B14)
private val CardBg          = Color(0xFF0E1626)
private val CardInner       = Color(0xFF141F33)
private val CardBorder      = Color(0x1AFFFFFF)
private val TextWhite       = Color(0xFFF1F5F9)
private val TextMuted       = Color(0xFF8896AB)
private val PurpleNeon      = Color(0xFFA855F7)
private val VioletTab       = Color(0xFF6366F1)
private val CyanBrand       = Color(0xFF38BDF8)
private val TealChart       = Color(0xFF14B8A6)
private val EmeraldIncome   = Color(0xFF10B981)
private val CoralExpense    = Color(0xFFEF4444)
private val AmberWarning    = Color(0xFFF59E0B)
private val PinkAccent      = Color(0xFFEC4899)
private val BlueAccent      = Color(0xFF3B82F6)

// ==========================================
// PERIOD & CATEGORY ENUMS
// ==========================================
enum class InsightPeriod(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    LAST_WEEK("Last Week"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom")
}

enum class InsightCategory(val label: String, val icon: ImageVector, val color: Color) {
    ALL("All", Icons.Default.GridView, PurpleNeon),
    PRODUCTIVITY("Productivity", Icons.Default.CheckCircle, PurpleNeon),
    WATER("Water", Icons.Default.WaterDrop, CyanBrand),
    HABITS("Habits", Icons.Default.Spa, EmeraldIncome),
    SCREEN("Screen", Icons.Default.PhoneAndroid, CyanBrand),
    MONEY("Money", Icons.Default.AccountBalanceWallet, AmberWarning),
    LEARNING("Learning", Icons.Default.MenuBook, PinkAccent)
}

data class PeriodRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val prevStartDate: LocalDate,
    val prevEndDate: LocalDate,
    val displayLabel: String,
    val comparisonLabel: String
)

// ==========================================
// MAIN COMPOSABLE: InsightsScreen
// ==========================================
@Composable
fun InsightsScreen(
    logs: List<LogEntry>,
    onNavigate: (String) -> Unit = {},
    onNavigateHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var analyticsRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) analyticsRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Reload persisted tracker entries whenever this screen resumes, not only when the parent recomposes.
    val sourceLogs = remember(context, logs, analyticsRefresh) { LifeTrackRepository(context).loadEntries() }
    var selectedCategory by remember { mutableStateOf(InsightCategory.ALL) }
    var selectedPeriod by remember { mutableStateOf(InsightPeriod.THIS_WEEK) }
    var showPeriodSelector by remember { mutableStateOf(false) }
    var showCompareDialog by remember { mutableStateOf(false) }

    // Custom date range state
    var customStartDate by remember { mutableStateOf(LocalDate.now().minusDays(6)) }
    var customEndDate by remember { mutableStateOf(LocalDate.now()) }

    // Calculate current and previous date ranges
    val range = remember(selectedPeriod, customStartDate, customEndDate, analyticsRefresh) {
        // Re-evaluate relative ranges after resume so Today/Yesterday/week/month/year don't stay stale across midnight.
        computePeriodRange(selectedPeriod, customStartDate, customEndDate)
    }

    // Filter logs for current and previous period
    val currentLogs = remember(sourceLogs, range) {
        sourceLogs.filter { entry ->
            try {
                val d = LocalDate.parse(entry.date)
                !d.isBefore(range.startDate) && !d.isAfter(range.endDate)
            } catch (_: Exception) { false }
        }
    }

    val previousLogs = remember(sourceLogs, range) {
        sourceLogs.filter { entry ->
            try {
                val d = LocalDate.parse(entry.date)
                !d.isBefore(range.prevStartDate) && !d.isAfter(range.prevEndDate)
            } catch (_: Exception) { false }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 10.dp, bottom = 110.dp)
        ) {
            // 1. Top Bar Header
            item {
                InsightsTopBar(
                    onSettingsClick = { onNavigate("Settings") }
                )
            }

            // 2. Universal Date Selector Card
            item {
                UniversalDateSelectorCard(
                    range = range,
                    onOpenSelector = { showPeriodSelector = true }
                )
            }

            // 3. Quick Period Chips: Today, Yesterday, This Week, Last Week, etc.
            item {
                QuickPeriodChipsRow(
                    selectedPeriod = selectedPeriod,
                    onSelectPeriod = { period ->
                        if (period == InsightPeriod.CUSTOM) {
                            showPeriodSelector = true
                        } else {
                            selectedPeriod = period
                        }
                    }
                )
            }

            // 4. Category Selector Icons: All, Productivity, Habits, Screen, Money, Learning
            item {
                CategoryPillsRow(
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
            }

            // 5. Category-Specific Body Content
            when (selectedCategory) {
                InsightCategory.ALL -> {
                    item {
                        FullLifeAnalyticsDashboard(
                            context = context,
                            logs = sourceLogs,
                            range = range,
                            refreshKey = analyticsRefresh
                        )
                    }
                    item {
                        InsightsOverviewSection(
                            currentLogs = currentLogs,
                            previousLogs = previousLogs,
                            range = range,
                            onCompareClick = { showCompareDialog = true },
                            onOpenCategory = { selectedCategory = it },
                            refreshKey = analyticsRefresh
                        )
                    }
                }
                InsightCategory.PRODUCTIVITY -> {
                    item {
                        ProductivityInsightsSection(
                            currentLogs = currentLogs,
                            previousLogs = previousLogs,
                            range = range,
                            onNavigate = onNavigate,
                            refreshKey = analyticsRefresh
                        )
                    }
                }
                InsightCategory.HABITS -> {
                    item {
                        HabitsRoutinesInsightsSection(
                            currentLogs = currentLogs,
                            previousLogs = previousLogs,
                            range = range,
                            onNavigate = onNavigate,
                            refreshKey = analyticsRefresh
                        )
                    }
                }
                InsightCategory.SCREEN -> {
                    item {
                        ScreenTimeInsightsSection(
                            currentLogs = currentLogs,
                            previousLogs = previousLogs,
                            range = range,
                            context = context,
                            refreshKey = analyticsRefresh
                        )
                    }
                }
                InsightCategory.MONEY -> {
                    item {
                        MoneyInsightsSummarySection(
                            currentLogs = currentLogs,
                            previousLogs = previousLogs,
                            range = range,
                            onNavigateToMoney = { onNavigate("Money") },
                            refreshKey = analyticsRefresh
                        )
                    }
                }
                InsightCategory.WATER -> {
                    item {
                        WaterInsightsSection(
                            currentLogs = currentLogs,
                            previousLogs = previousLogs,
                            range = range,
                            onNavigateToWater = { onNavigate("Water") }
                        )
                    }
                }
                InsightCategory.LEARNING -> {
                    item {
                        LearningInsightsSection(
                            currentLogs = currentLogs,
                            previousLogs = previousLogs,
                            range = range,
                            onNavigate = onNavigate
                        )
                    }
                }
            }
        }

        // ==========================================
        // PERIOD SELECTION DIALOG (Screen 2)
        // ==========================================
        if (showPeriodSelector) {
            PeriodSelectionDialog(
                selectedPeriod = selectedPeriod,
                customStart = customStartDate,
                customEnd = customEndDate,
                onDismiss = { showPeriodSelector = false },
                onApply = { period, start, end ->
                    selectedPeriod = period
                    customStartDate = start
                    customEndDate = end
                    showPeriodSelector = false
                }
            )
        }

        // ==========================================
        // COMPARE PERIODS DIALOG (Screen 8)
        // ==========================================
        if (showCompareDialog) {
            ComparePeriodsDialog(
                range = range,
                currentLogs = currentLogs,
                previousLogs = previousLogs,
                onDismiss = { showCompareDialog = false }
            )
        }
    }
}

// ==========================================
// 1. TOP BAR HEADER
// ==========================================
@Composable
private fun InsightsTopBar(
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "LifeTrack",
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Insights",
                    color = PurpleNeon,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    " · Your life, visualized",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(36.dp)
                .background(CardInner, CircleShape)
        ) {
            Icon(Icons.Default.Settings, "Settings", tint = TextWhite, modifier = Modifier.size(18.dp))
        }
    }
}

// ==========================================
// 2. UNIVERSAL DATE SELECTOR CARD
// ==========================================
@Composable
private fun UniversalDateSelectorCard(
    range: PeriodRange,
    onOpenSelector: () -> Unit
) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onOpenSelector)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(PurpleNeon.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CalendarToday, null, tint = PurpleNeon, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text(
                        range.displayLabel,
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
                    val yearFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
                    val datesStr = if (range.startDate == range.endDate) {
                        range.startDate.format(yearFormatter)
                    } else {
                        "${range.startDate.format(formatter)} - ${range.endDate.format(yearFormatter)}"
                    }
                    Text(
                        datesStr,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Icon(Icons.Default.KeyboardArrowDown, "Change Period", tint = TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

// ==========================================
// 3. QUICK PERIOD CHIPS ROW
// ==========================================
@Composable
private fun QuickPeriodChipsRow(
    selectedPeriod: InsightPeriod,
    onSelectPeriod: (InsightPeriod) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(InsightPeriod.values()) { p ->
            val isSelected = selectedPeriod == p
            Surface(
                color = if (isSelected) PurpleNeon else CardInner,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isSelected) PurpleNeon else CardBorder),
                modifier = Modifier.clickable { onSelectPeriod(p) }
            ) {
                Text(
                    p.label,
                    color = if (isSelected) Color.White else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ==========================================
// 4. CATEGORY PILLS ROW
// ==========================================
@Composable
private fun CategoryPillsRow(
    selectedCategory: InsightCategory,
    onSelectCategory: (InsightCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        InsightCategory.values().forEach { cat ->
            val isSelected = selectedCategory == cat
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectCategory(cat) }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isSelected) cat.color else CardInner,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        cat.icon,
                        cat.label,
                        tint = if (isSelected) Color.White else cat.color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    cat.label,
                    color = if (isSelected) Color.White else TextMuted,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

// ==========================================
// SECTION 1: INSIGHTS OVERVIEW (SCREEN 1)
// ==========================================
@Composable
private fun InsightsOverviewSection(
    currentLogs: List<LogEntry>,
    previousLogs: List<LogEntry>,
    range: PeriodRange,
    onCompareClick: () -> Unit,
    onOpenCategory: (InsightCategory) -> Unit,
    refreshKey: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Life Balance Score Card
        LifeBalanceScoreCard(
            currentLogs = currentLogs,
            previousLogs = previousLogs,
            range = range,
            refreshKey = refreshKey
        )

        // Water / Hydration Overview Card
        WaterOverviewCard(
            currentLogs = currentLogs,
            range = range,
            refreshKey = refreshKey,
            onOpenWater = { onOpenCategory(InsightCategory.WATER) }
        )

        // Weekly Activity Heatmap
        WeeklyActivityHeatmapCard(
            logs = currentLogs,
            range = range,
            onOpenCategory = onOpenCategory
        )

        // Key Insights Card
        KeyInsightsCard(
            currentLogs = currentLogs,
            previousLogs = previousLogs,
            range = range,
            onSeeAllClick = onCompareClick
        )
    }
}

// ------------------------------------------
// Life Balance Score Card (calculated from saved activity and configured goals)
// ------------------------------------------
@Composable
private fun LifeBalanceScoreCard(
    currentLogs: List<LogEntry>,
    previousLogs: List<LogEntry>,
    range: PeriodRange,
    refreshKey: Int
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val days = (java.time.temporal.ChronoUnit.DAYS.between(range.startDate, range.endDate).toInt() + 1).coerceAtLeast(1)
    val periodTasks = remember(range, refreshKey) {
        TaskRepository.getTasks(ctx).filter {
            val d = runCatching { LocalDate.parse(it.dueDate) }.getOrNull()
            d != null && !d.isBefore(range.startDate) && !d.isAfter(range.endDate)
        }
    }
    val phoneMins = currentLogs.filter { it.category == "Phone usage" }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val waterMlTotal = currentLogs.filter { it.category == "Water" }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val studyMinutes = currentLogs.filter { it.category == "Study" }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val hasHabits = remember(refreshKey) { HabitRoutineRepository.getHabits(ctx).isNotEmpty() }
    val habitsScore = remember(range, refreshKey) {
        HabitRoutineRepository.calculateOverallHabitsCompletionRate(ctx, range.startDate, range.endDate).toInt().coerceIn(0, 100)
    }
    val sparklineDates = remember(range) {
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(range.startDate, range.endDate).toInt() + 1
        val count = totalDays.coerceIn(1, 7)
        (0 until count).map { index ->
            range.startDate.plusDays((index.toLong() * (totalDays - 1).coerceAtLeast(0) / (count - 1).coerceAtLeast(1)).toLong())
        }.distinct()
    }
    val sparklineValues = remember(currentLogs, sparklineDates) {
        sparklineDates.map { date -> currentLogs.count { it.date == date.toString() }.toFloat() }
    }

    // Pillar scores use actual task/habit records and the user's configured daily targets.
    val productivityScore = if (periodTasks.isEmpty()) 0 else periodTasks.count { it.isCompleted } * 100 / periodTasks.size
    val avgPhone = phoneMins / days
    // No configured screen-time target exists here, so do not manufacture a score from arbitrary thresholds.
    val screenScore: Int? = null
    val waterTarget = (Goals.waterTarget(ctx).toLong() * days).coerceAtLeast(1L)
    val studyTarget = (Goals.studyTarget(ctx).toLong() * days).coerceAtLeast(1L)
    val waterScore = if (waterMlTotal == 0) 0 else ((waterMlTotal.toLong() * 100) / waterTarget).toInt().coerceIn(0, 100)
    val learningScore = if (studyMinutes == 0) 0 else ((studyMinutes.toLong() * 100) / studyTarget).toInt().coerceIn(0, 100)
    val availablePillars = buildList {
        if (periodTasks.isNotEmpty()) add(productivityScore)
        if (hasHabits) add(habitsScore)
        // Screen time is unavailable in this score until a user-defined target exists.
        if (waterMlTotal > 0) add(waterScore)
        if (studyMinutes > 0) add(learningScore)
    }
    val hasData = availablePillars.isNotEmpty()
    val overallScore = if (hasData) availablePillars.average().toInt() else 0

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title & Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Life Balance Score",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$overallScore",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (hasData && previousLogs.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            val prevCount = previousLogs.size
                            val pct = if (prevCount > 0) ((currentLogs.size - prevCount) * 100 / prevCount) else 0
                            val isUp = pct >= 0
                            Surface(
                                color = if (isUp) EmeraldIncome.copy(alpha = 0.18f) else CoralExpense.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(if (isUp) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = if (isUp) EmeraldIncome else CoralExpense, modifier = Modifier.size(12.dp))
                                    Text("Logs ${if (isUp) "+" else ""}$pct%", color = if (isUp) EmeraldIncome else CoralExpense, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Smooth Neon Sparkline
                Canvas(modifier = Modifier.size(width = 110.dp, height = 45.dp)) {
                    if (hasData && sparklineValues.any { it > 0f }) {
                        val maxCount = (sparklineValues.maxOrNull() ?: 0f).coerceAtLeast(1f)
                        val points = sparklineValues.map { it / maxCount }
                        if (points.size == 1) {
                            drawCircle(TealChart, radius = 3.dp.toPx(), center = Offset(size.width / 2f, size.height * (1f - points.first())))
                        } else if (points.size > 1) {
                            val stepX = size.width / (points.size - 1)
                            val path = Path()
                            val fillPath = Path()
                            points.forEachIndexed { i, p ->
                                val x = i * stepX
                                val y = size.height * (1f - p)
                                if (i == 0) {
                                    path.moveTo(x, y)
                                    fillPath.moveTo(x, size.height)
                                    fillPath.lineTo(x, y)
                                } else {
                                    val prevX = (i - 1) * stepX
                                    val prevY = size.height * (1f - points[i - 1])
                                    val cx = (prevX + x) / 2f
                                    path.cubicTo(cx, prevY, cx, y, x, y)
                                    fillPath.cubicTo(cx, prevY, cx, y, x, y)
                                }
                            }
                            fillPath.lineTo(size.width, size.height)
                            fillPath.close()
                            drawPath(fillPath, brush = Brush.verticalGradient(listOf(TealChart.copy(alpha = 0.35f), Color.Transparent)))
                            drawPath(path, color = TealChart, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                }
            }

            HorizontalDivider(color = CardBorder)

            // 5 Pillar Circular Progress Rings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PillarProgressItem("Productivity", productivityScore, PurpleNeon)
                PillarProgressItem("Habits", habitsScore, EmeraldIncome)
                PillarProgressItem("Screen", screenScore, CyanBrand)
                PillarProgressItem("Water", waterScore, AmberWarning)
                PillarProgressItem("Learning", learningScore, PinkAccent)
            }
        }
    }
}

@Composable
private fun PillarProgressItem(label: String, score: Int?, tint: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 4.dp.toPx()
                drawCircle(color = CardInner, style = Stroke(strokeW))
                score?.let {
                    drawArc(
                        color = tint,
                        startAngle = -90f,
                        sweepAngle = (it.coerceIn(0, 100) / 100f) * 360f,
                        useCenter = false,
                        style = Stroke(strokeW, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                score?.toString() ?: "—",
                color = if (score == null) TextMuted else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            label,
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

// ------------------------------------------
// Weekly Activity Heatmap Card
// ------------------------------------------
@Composable
private fun WeeklyActivityHeatmapCard(
    logs: List<LogEntry>,
    range: PeriodRange,
    onOpenCategory: (InsightCategory) -> Unit
) {
    val isYearView = range.displayLabel == "This Year" || java.time.temporal.ChronoUnit.DAYS.between(range.startDate, range.endDate) > 180
    val isSingleDay = range.startDate == range.endDate

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isYearView) "Yearly Activity Heatmap" else "Activity Heatmap",
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = PurpleNeon.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        range.displayLabel,
                        color = PurpleNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            val matrixRows = listOf(
                Triple("Tasks", InsightCategory.PRODUCTIVITY, PurpleNeon),
                Triple("Habits", InsightCategory.HABITS, EmeraldIncome),
                Triple("Screen", InsightCategory.SCREEN, CyanBrand),
                Triple("Money", InsightCategory.MONEY, AmberWarning),
                Triple("Learning", InsightCategory.LEARNING, PinkAccent)
            )

            if (isYearView) {
                val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                // Header
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(55.dp))
                    months.forEach { m ->
                        Text(
                            m,
                            color = TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                matrixRows.forEach { (label, category, color) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCategory(category) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(55.dp))
                        (1..12).forEach { monthIdx ->
                            val count = logs.count { entry ->
                                try {
                                    val d = LocalDate.parse(entry.date)
                                    d.monthValue == monthIdx && when (category) {
                                        InsightCategory.PRODUCTIVITY -> entry.category == "Care" || entry.title.contains("Task", ignoreCase = true)
                                        InsightCategory.HABITS -> entry.category == "Care" || entry.category == "Face routine"
                                        InsightCategory.SCREEN -> entry.category.equals("Phone usage", ignoreCase = true)
                                        InsightCategory.MONEY -> entry.category == "Expense" || entry.category == "Income"
                                        InsightCategory.LEARNING -> entry.category == "Study"
                                        else -> true
                                    }
                                } catch (_: Exception) { false }
                            }
                            val intensity = when {
                                count >= 10 -> 1.0f
                                count >= 5 -> 0.75f
                                count >= 1 -> 0.45f
                                else -> 0f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 1.5.dp)
                                    .height(20.dp)
                                    .background(color.copy(alpha = intensity), RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            } else {
                val days = if (isSingleDay) {
                    (6 downTo 0).map { range.endDate.minusDays(it.toLong()) }
                } else {
                    (0..6).map { range.startDate.plusDays(it.toLong()) }
                }
                val dayHeaders = days.map { it.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }

                // Header
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(55.dp))
                    dayHeaders.forEach { d ->
                        Text(
                            d,
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                matrixRows.forEach { (label, category, color) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCategory(category) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(55.dp))
                        days.forEachIndexed { index, day ->
                            val dateStr = day.toString()
                            val count = logs.count { entry ->
                                entry.date == dateStr && when (category) {
                                    InsightCategory.PRODUCTIVITY -> entry.category == "Care" || entry.title.contains("Task", ignoreCase = true)
                                    InsightCategory.HABITS -> entry.category == "Care" || entry.category == "Face routine"
                                    InsightCategory.SCREEN -> entry.category.equals("Phone usage", ignoreCase = true)
                                    InsightCategory.MONEY -> entry.category == "Expense" || entry.category == "Income"
                                    InsightCategory.LEARNING -> entry.category == "Study"
                                    else -> true
                                }
                            }
                            val intensity = when {
                                count >= 3 -> 1.0f
                                count >= 2 -> 0.75f
                                count >= 1 -> 0.50f
                                else -> 0f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                                    .height(22.dp)
                                    .background(color.copy(alpha = intensity), RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------
// Key Insights Card (with Comparison Indicators)
// ------------------------------------------
@Composable
private fun KeyInsightsCard(
    currentLogs: List<LogEntry>,
    previousLogs: List<LogEntry>,
    range: PeriodRange,
    onSeeAllClick: () -> Unit
) {
    val hasData = currentLogs.isNotEmpty()

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Key Insights",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (hasData) {
                    Text(
                        "Compare >",
                        color = CyanBrand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onSeeAllClick)
                    )
                }
            }

            if (!hasData) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No activity logged yet for this period. As you log habits, expenses, and study sessions, your key insights will appear here.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val taskEntries = currentLogs.filter { it.category == "Care" || it.title.contains("Task", ignoreCase = true) }
                val prevTasks = previousLogs.filter { it.category == "Care" || it.title.contains("Task", ignoreCase = true) }
                val currentExpenses = currentLogs.filter { it.category == "Expense" }.sumOf { MoneyAmounts.value(it.amount) }
                val prevExpenses = previousLogs.filter { it.category == "Expense" }.sumOf { MoneyAmounts.value(it.amount) }
                val currentStudy = currentLogs.filter { it.category == "Study" }.sumOf { it.amount.toIntOrNull() ?: 0 }
                val prevStudy = previousLogs.filter { it.category == "Study" }.sumOf { it.amount.toIntOrNull() ?: 0 }

                if (taskEntries.isNotEmpty()) {
                    val taskDiff = if (prevTasks.isNotEmpty()) ((taskEntries.size - prevTasks.size) * 100 / prevTasks.size) else null
                    InsightItemRow(
                        icon = if (taskDiff == null || taskDiff >= 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        iconBg = if (taskDiff == null || taskDiff >= 0) EmeraldIncome else CoralExpense,
                        title = "${taskEntries.size} tasks logged this period",
                        delta = taskDiff?.let { "${if (it >= 0) "+" else ""}$it%" } ?: "No baseline",
                        isPositive = taskDiff == null || taskDiff >= 0
                    )
                }

                if (currentExpenses > 0) {
                    val expDiff = if (prevExpenses > 0) (((currentExpenses - prevExpenses) / prevExpenses) * 100).toInt() else null
                    InsightItemRow(
                        icon = if (expDiff != null && expDiff <= 0) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        iconBg = if (expDiff != null && expDiff <= 0) EmeraldIncome else CoralExpense,
                        title = "Expenses total ₹${String.format(Locale.US, "%,.0f", currentExpenses)}",
                        delta = expDiff?.let { "${if (it >= 0) "+" else ""}$it%" } ?: "No baseline",
                        isPositive = expDiff != null && expDiff <= 0
                    )
                }

                val curWater = currentLogs.filter { it.category == "Water" }.sumOf { it.amount.toIntOrNull() ?: 0 }
                val prevWater = previousLogs.filter { it.category == "Water" }.sumOf { it.amount.toIntOrNull() ?: 0 }
                if (curWater > 0) {
                    val wDiff = if (prevWater > 0) ((curWater - prevWater) * 100 / prevWater) else null
                    InsightItemRow(
                        icon = if (wDiff == null || wDiff >= 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        iconBg = if (wDiff == null || wDiff >= 0) CyanBrand else CoralExpense,
                        title = "${String.format(Locale.US, "%.1f", curWater / 1000f)}L water logged",
                        delta = wDiff?.let { "${if (it >= 0) "+" else ""}$it%" } ?: "No baseline",
                        isPositive = wDiff == null || wDiff >= 0
                    )
                }

                if (currentStudy > 0) {
                    val studyDiff = if (prevStudy > 0) (((currentStudy - prevStudy) * 100) / prevStudy) else null
                    InsightItemRow(
                        icon = Icons.Default.ArrowUpward,
                        iconBg = PurpleNeon,
                        title = "${currentStudy / 60}h ${currentStudy % 60}m study time recorded",
                        delta = studyDiff?.let { "${if (it >= 0) "+" else ""}$it%" } ?: "No baseline",
                        isPositive = studyDiff == null || studyDiff >= 0
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightItemRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    delta: String,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardInner)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBg.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconBg, modifier = Modifier.size(16.dp))
        }
        Text(
            title,
            color = TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            delta,
            color = if (isPositive) EmeraldIncome else CoralExpense,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ==========================================
// SECTION 2: PRODUCTIVITY INSIGHTS (SCREEN 2)
// ==========================================
@Composable
private fun ProductivityInsightsSection(
    currentLogs: List<LogEntry>,
    previousLogs: List<LogEntry>,
    range: PeriodRange,
    onNavigate: (String) -> Unit,
    refreshKey: Int
) {
    val context = LocalContext.current
    val todayStr = remember(refreshKey) { java.time.LocalDate.now().toString() }
    val realTasks = remember(context, refreshKey) { TaskRepository.getTasks(context) }
    val periodTasks = remember(realTasks, range) {
        realTasks.filter {
            val d = runCatching { LocalDate.parse(it.dueDate) }.getOrNull()
            d != null && !d.isBefore(range.startDate) && !d.isAfter(range.endDate)
        }
    }
    val completedTasks = remember(periodTasks) { periodTasks.filter { it.isCompleted } }
    val pendingTasks = remember(periodTasks, todayStr) { periodTasks.filter { !it.isCompleted && it.dueDate >= todayStr } }
    val overdueTasks = remember(periodTasks, todayStr) { periodTasks.filter { !it.isCompleted && it.dueDate < todayStr } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Task Completion Stacked Bar Chart Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Task Completion", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChartLegendDot(EmeraldIncome, "Completed")
                        ChartLegendDot(BlueAccent, "Pending")
                        ChartLegendDot(CoralExpense, "Overdue")
                    }
                }

                // Stacked Bar Chart (Dynamic period)
                StackedTaskBarChart(realTasks, range)

                // 3 Summary Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val previousTasks = remember(refreshKey, range) { TaskRepository.getTasks(context).filter { task ->
                        val date = runCatching { LocalDate.parse(task.dueDate) }.getOrNull()
                        date != null && !date.isBefore(range.prevStartDate) && !date.isAfter(range.prevEndDate)
                    } }
                    val previousCompletedCount = previousTasks.count { it.isCompleted }
                    val completedDelta = if (previousTasks.isEmpty()) "No baseline" else {
                        val delta = completedTasks.size - previousCompletedCount
                        "${if (delta > 0) "+" else ""}$delta vs previous"
                    }
                    MetricTrendCard("${completedTasks.size}", "Completed", completedDelta, EmeraldIncome, Icons.Default.CheckCircle, Modifier.weight(1f))
                    MetricTrendCard("${pendingTasks.size}", "Pending", if (pendingTasks.isNotEmpty()) "${pendingTasks.size}" else "0", BlueAccent, Icons.Default.HourglassEmpty, Modifier.weight(1f))
                    MetricTrendCard("${overdueTasks.size}", "Overdue", if (overdueTasks.isNotEmpty()) "${overdueTasks.size}" else "0", CoralExpense, Icons.Default.Warning, Modifier.weight(1f))
                }
            }
        }

        // 2. Productivity Timing Hourly Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Productivity Timing", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (completedTasks.isNotEmpty()) {
                        Surface(
                            color = PurpleNeon.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Active sessions",
                                color = PurpleNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 24h Hourly Histogram Bars
                ProductivityHourlyChart(completedTasks)
            }
        }

        // 3. Task Categories Donut Chart Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Task Categories", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                if (realTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No task categories to display.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    val catGroups = remember(realTasks) {
                        realTasks.groupBy { if (it.category.isNotBlank()) it.category else "General" }
                    }
                    val catColors = listOf(PurpleNeon, CyanBrand, PinkAccent, EmeraldIncome, AmberWarning)
                    val totalTasks = realTasks.size

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier.size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeW = 12.dp.toPx()
                                var currentAngle = -90f
                                catGroups.entries.toList().forEachIndexed { idx, entry ->
                                    val sweep = (entry.value.size.toFloat() / totalTasks) * 360f
                                    val col = catColors[idx % catColors.size]
                                    drawArc(col, currentAngle, sweep, false, style = Stroke(strokeW))
                                    currentAngle += sweep
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalTasks", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Total Tasks", color = TextMuted, fontSize = 8.sp)
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            catGroups.entries.toList().take(5).forEachIndexed { idx, entry ->
                                val pct = (entry.value.size * 100 / totalTasks)
                                val col = catColors[idx % catColors.size]
                                CategoryProgressRow(col, entry.key, "$pct%")
                            }
                        }
                    }
                }
            }
        }

        // 4. Top Tasks List Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Top Tasks", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "See All >",
                        color = CyanBrand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigate("Tasks") }
                    )
                }

                if (realTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No tasks created yet.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    val colors = listOf(PurpleNeon, BlueAccent, CyanBrand, EmeraldIncome, AmberWarning)
                    realTasks.take(5).forEachIndexed { idx, task ->
                        val col = colors[idx % colors.size]
                        val progress = if (task.isCompleted) 1f else if (task.subtasks.isNotEmpty()) {
                            task.subtasks.count { it.isCompleted }.toFloat() / task.subtasks.size
                        } else 0f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardInner)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                task.title,
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                color = col,
                                trackColor = CardBg,
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (task.isCompleted) "Done" else "${(progress * 100).toInt()}%",
                                color = col,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StackedTaskBarChart(
    tasks: List<TaskItem> = emptyList(),
    range: PeriodRange? = null
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No task activity recorded yet.", color = TextMuted, fontSize = 12.sp)
        }
        return
    }

    val todayStr = remember { LocalDate.now().toString() }
    val isYearView = range != null && (range.displayLabel == "This Year" || java.time.temporal.ChronoUnit.DAYS.between(range.startDate, range.endDate) > 180)
    val isSingleDay = range != null && range.startDate == range.endDate

    val labels: List<String>
    val data: List<Triple<Float, Float, Float>> // (comp, pend, over)

    if (isYearView) {
        labels = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        data = (1..12).map { monthIdx ->
            val monthTasks = tasks.filter {
                try { LocalDate.parse(it.dueDate).monthValue == monthIdx } catch (_: Exception) { false }
            }
            val comp = monthTasks.count { it.isCompleted }.toFloat()
            val over = monthTasks.count { !it.isCompleted && it.dueDate < todayStr }.toFloat()
            val pend = monthTasks.count { !it.isCompleted && it.dueDate >= todayStr }.toFloat()
            Triple(comp, pend, over)
        }
    } else {
        val days = if (isSingleDay && range != null) {
            (6 downTo 0).map { range.endDate.minusDays(it.toLong()) }
        } else if (range != null) {
            (0..6).map { range.startDate.plusDays(it.toLong()) }
        } else {
            val now = LocalDate.now()
            val start = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            (0..6).map { start.plusDays(it.toLong()) }
        }
        labels = days.map { it.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
        data = days.map { day ->
            val dStr = day.toString()
            val dayTasks = tasks.filter { it.dueDate == dStr }
            val comp = dayTasks.count { it.isCompleted }.toFloat()
            val over = dayTasks.count { !it.isCompleted && dStr < todayStr }.toFloat()
            val pend = dayTasks.count { !it.isCompleted && dStr >= todayStr }.toFloat()
            Triple(comp, pend, over)
        }
    }

    val maxTotal = (data.maxOfOrNull { it.first + it.second + it.third } ?: 1f).coerceAtLeast(1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEachIndexed { i, (comp, pend, over) ->
                val total = comp + pend + over
                val barHeight = if (total > 0f) ((total / maxTotal) * 75f).coerceIn(8f, 75f) else 3f
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .width(if (isYearView) 12.dp else 18.dp)
                            .height(barHeight.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (total == 0f) {
                            Box(Modifier.fillMaxSize().background(CardInner))
                        } else {
                            if (over > 0f) {
                                Box(Modifier.fillMaxWidth().weight(over).background(CoralExpense))
                            }
                            if (pend > 0f) {
                                Box(Modifier.fillMaxWidth().weight(pend).background(BlueAccent))
                            }
                            if (comp > 0f) {
                                Box(Modifier.fillMaxWidth().weight(comp).background(EmeraldIncome))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEach { d ->
                Text(
                    d,
                    color = TextMuted,
                    fontSize = if (isYearView) 8.sp else 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProductivityHourlyChart(completedTasks: List<TaskItem> = emptyList()) {
    if (completedTasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Complete tasks to discover your peak productivity hours.", color = TextMuted, fontSize = 12.sp)
        }
        return
    }
    val hours = listOf(1, 0, 0, 0, 1, 2, 4, 6, 8, 12, 16, 14, 11, 8, 9, 10, 12, 9, 7, 5, 4, 2, 1, 0)
    val maxH = hours.maxOrNull()?.toFloat() ?: 1f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            hours.forEachIndexed { idx, h ->
                val isPeak = idx in 10..13
                val barColor = if (isPeak) PurpleNeon else CyanBrand.copy(alpha = 0.45f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(((h / maxH) * 55f).coerceAtLeast(3f).dp)
                        .background(barColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("12 AM", "6 AM", "12 PM", "6 PM").forEach {
                Text(it, color = TextMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, color = TextMuted, fontSize = 9.sp)
    }
}

@Composable
private fun CategoryProgressRow(color: Color, label: String, percent: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(label, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Text(percent, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricTrendCard(
    value: String,
    title: String,
    trend: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CardInner,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Text(trend, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ==========================================
// SECTION 3: HABITS & ROUTINES INSIGHTS (SCREEN 3)
// ==========================================
@Composable
private fun HabitsRoutinesInsightsSection(
    currentLogs: List<LogEntry>,
    previousLogs: List<LogEntry>,
    range: PeriodRange,
    onNavigate: (String) -> Unit,
    refreshKey: Int
) {
    val context = LocalContext.current
    val today = remember(refreshKey) { java.time.LocalDate.now() }
    val isYearView = range.displayLabel == "This Year" || java.time.temporal.ChronoUnit.DAYS.between(range.startDate, range.endDate) > 180
    val isSingleDay = range.startDate == range.endDate

    val matrixDays = remember(range) {
        if (isSingleDay) {
            (6 downTo 0).map { range.endDate.minusDays(it.toLong()) }
        } else if (isYearView) {
            (1..12).map { range.startDate.withMonth(it).withDayOfMonth(1) }
        } else {
            (0..6).map { range.startDate.plusDays(it.toLong()) }
        }
    }

    val habits = remember(refreshKey) { HabitRoutineRepository.getHabits(context) }
    val overallConsistency = remember(habits, range) {
        if (habits.isEmpty()) 0.0 else HabitRoutineRepository.calculateOverallHabitsCompletionRate(context, range.startDate, range.endDate)
    }

    val targetDate = if (isSingleDay) range.endDate else today
    val scheduledForPeriod = remember(habits, targetDate) {
        HabitRoutineRepository.getScheduledHabitsForDate(context, targetDate)
    }
    val completedCount = remember(scheduledForPeriod, targetDate) {
        scheduledForPeriod.count { h ->
            HabitRoutineRepository.getCompletion(context, h.id, targetDate.toString())?.status == "completed"
        }
    }

    val longestActiveStreak = remember(habits, targetDate) {
        habits.maxOfOrNull { h ->
            HabitRoutineRepository.calculateHabitStreak(context, h.id, targetDate).second
        } ?: 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (habits.isEmpty()) {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Spa, null, tint = EmeraldIncome, modifier = Modifier.size(36.dp))
                    Text("No Habit Records Yet", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Create your habits and complete daily targets to view analytics, weekly consistency matrix, and streaks.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { onNavigate("Habits & Routines") },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldIncome),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Go to Habits", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // 1. Habit Overview 3-Card Row
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Habit Overview", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Consistency Ring
                        Surface(
                            color = CardInner,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        val strokeW = 4.dp.toPx()
                                        drawCircle(CardBg, style = Stroke(strokeW))
                                        drawArc(TealChart, -90f, (overallConsistency.toFloat() / 100f) * 360f, false, style = Stroke(strokeW, cap = StrokeCap.Round))
                                    }
                                    Text("${overallConsistency.toInt()}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Overall", color = TextMuted, fontSize = 9.sp)
                            }
                        }

                        // Completed Today / Selected Period
                        Surface(
                            color = CardInner,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(EmeraldIncome.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = EmeraldIncome, modifier = Modifier.size(18.dp))
                                }
                                Text("$completedCount/${scheduledForPeriod.size}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(if (isSingleDay) range.displayLabel else "Completed", color = TextMuted, fontSize = 9.sp)
                            }
                        }

                        // Longest Streak
                        Surface(
                            color = CardInner,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(AmberWarning.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Whatshot, null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                                }
                                Text("$longestActiveStreak", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Best Streak", color = TextMuted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            // 2. Habit Completion Matrix Card
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (isYearView) "Yearly Habit Completion" else "${range.displayLabel} Habit Completion",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width(110.dp))
                        if (isYearView) {
                            listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec").forEach { d ->
                                Text(d, color = TextMuted, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            }
                        } else {
                            matrixDays.map { it.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }.forEach { d ->
                                Text(d, color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    habits.take(6).forEach { habit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                habit.name,
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(110.dp)
                            )
                            matrixDays.forEach { day ->
                                val isDone = if (isYearView) {
                                    val startM = day.withDayOfMonth(1)
                                    val endM = day.withDayOfMonth(day.lengthOfMonth())
                                    HabitRoutineRepository.calculateHabitCompletionRate(context, habit.id, startM, endM) > 0.0
                                } else {
                                    HabitRoutineRepository.getCompletion(context, habit.id, day.toString())?.status == "completed"
                                }
                                val isScheduled = HabitRoutineRepository.isHabitScheduled(habit, day)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .size(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(11.dp)
                                            .background(
                                                if (isDone) EmeraldIncome else if (isScheduled) CardInner else Color.Transparent,
                                                CircleShape
                                            )
                                            .border(1.dp, if (isDone) EmeraldIncome else if (isScheduled) CardBorder else Color.Transparent, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Top Performing Habits Card
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Top Performing Habits", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("See All >", color = CyanBrand, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onNavigate("Habits & Routines") })
                    }

                    val rankedHabits = remember(habits) {
                        habits.map { h ->
                            val rate = HabitRoutineRepository.calculateHabitCompletionRate(context, h.id, today.minusDays(29), today).toInt()
                            val color = HabitRoutineIconHelper.parseColor(h.colorHex)
                            Triple(h.name, rate, color)
                        }.sortedByDescending { it.second }.take(5)
                    }

                    rankedHabits.forEach { (name, pct, color) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardInner)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("$pct%", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                color = color,
                                trackColor = CardBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }

            // 4. Habit Streak Calendar Card
            HabitStreakCalendarCard()
        }
    }
}

@Composable
private fun HabitStreakCalendarCard() {
    val context = LocalContext.current
    val month = java.time.YearMonth.now()
    val firstDay = month.atDay(1)
    val offset = firstDay.dayOfWeek.value - 1
    val completions = remember(month) {
        HabitRoutineRepository.getCompletions(context)
            .filter { it.status == "completed" }
            .mapNotNull { record -> runCatching { LocalDate.parse(record.date) }.getOrNull() }
            .toSet()
    }
    val activeDays = completions.filter { it.year == month.year && it.monthValue == month.monthValue }
        .map { it.dayOfMonth }.toSet()
    val totalCells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    val cells = (0 until totalCells).map { index ->
        val day = index - offset + 1
        day.takeIf { it in 1..month.lengthOfMonth() }
    }

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Habit Streak Calendar", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())), color = CyanBrand, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                    Text(it, color = TextMuted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    week.forEach { day ->
                        Box(modifier = Modifier.weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                            if (day == null) {
                                Spacer(Modifier.size(28.dp))
                            } else {
                                val isActive = day in activeDays
                                Box(
                                    modifier = Modifier.size(28.dp).background(
                                        if (isActive) EmeraldIncome.copy(alpha = 0.85f) else CardInner,
                                        CircleShape
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$day", color = if (isActive) Color.Black else TextWhite, fontSize = 10.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
            Text("${activeDays.size} days with completed habit records · based on saved history", color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ScreenTimeInsightsSection(
    currentLogs: List<LogEntry>,
    previousLogs: List<LogEntry>,
    range: PeriodRange,
    context: Context,
    refreshKey: Int
) {
    val hasPermission = remember(refreshKey) { checkUsageAccessPermission(context) }
    val bounds = remember(range) {
        val zone = java.time.ZoneId.systemDefault()
        val startMs = range.startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = range.endDate.atTime(java.time.LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        startMs to endMs
    }
    val appUsageList = remember(hasPermission, bounds, refreshKey) {
        if (!hasPermission) emptyList()
        else ScreenTimeRepository.queryAppUsage(context, bounds.first, bounds.second)
    }
    val systemMetrics = remember(hasPermission, bounds, refreshKey) {
        if (!hasPermission) Triple(0, 0, 0)
        else ScreenTimeRepository.querySystemMetrics(context, bounds.first, bounds.second)
    }
    var showAllApps by remember { mutableStateOf(false) }
    // Compare actual usage over the selected dates with the equivalent previous-period buckets.
    val weeklyTrend = remember(hasPermission, range, refreshKey) {
        if (!hasPermission) emptyList()
        else {
            val zone = java.time.ZoneId.systemDefault()
            val totalDays = (java.time.temporal.ChronoUnit.DAYS.between(range.startDate, range.endDate).toInt() + 1).coerceAtLeast(1)
            val bucketCount = totalDays.coerceAtMost(7)
            val bucketStarts = (0 until bucketCount).map { index ->
                range.startDate.plusDays((index.toLong() * (totalDays - 1) / (bucketCount - 1).coerceAtLeast(1)).toLong())
            }.distinct()
            bucketStarts.mapIndexed { index, bucketStart ->
                val nextStart = bucketStarts.getOrNull(index + 1)
                val bucketEnd = if (totalDays <= 7) bucketStart else (nextStart?.minusDays(1) ?: range.endDate).coerceAtLeast(bucketStart)
                val offsetStart = java.time.temporal.ChronoUnit.DAYS.between(range.startDate, bucketStart)
                val offsetEnd = java.time.temporal.ChronoUnit.DAYS.between(range.startDate, bucketEnd)
                val previousBucketStart = range.prevStartDate.plusDays(offsetStart)
                val previousBucketEnd = range.prevStartDate.plusDays(offsetEnd)
                fun minutes(start: LocalDate, end: LocalDate): Int {
                    val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
                    val until = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    return (ScreenTimeRepository.queryAppUsage(context, from, until).sumOf { it.totalTimeMs } / 60_000L).toInt()
                }
                val currentEnd = if (bucketEnd.isAfter(LocalDate.now())) LocalDate.now() else bucketEnd
                val currentMinutes = if (bucketStart.isAfter(LocalDate.now())) 0 else minutes(bucketStart, currentEnd)
                val previousMinutes = minutes(previousBucketStart, previousBucketEnd)
                DailyTrendPoint(
                    dayOfWeek = bucketStart.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)),
                    date = bucketStart,
                    thisPeriodMinutes = currentMinutes,
                    previousPeriodMinutes = previousMinutes
                )
            }
        }
    }

    val totalMs = remember(appUsageList) { appUsageList.sumOf { it.totalTimeMs } }
    val totalHours = totalMs / (1000 * 60 * 60)
    val totalMins = (totalMs / (1000 * 60)) % 60
    val screenTimeDisplay = if (totalMs > 0) "${totalHours}h ${totalMins}m" else "0h 0m"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Permission Banner if not enabled
        if (!hasPermission) {
            Surface(
                color = AmberWarning.copy(alpha = 0.15f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Usage Access Recommended", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Grant permission to read exact device screen time.", color = TextMuted, fontSize = 10.sp)
                    }
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Enable", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 1. Screen Time Overview Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Screen Time Overview", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(screenTimeDisplay, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(if (totalMs > 0) "Total time active on device" else "No screen time recorded", color = TextMuted, fontSize = 10.sp)
                    }
                }

                HorizontalDivider(color = CardBorder)

                // 3 Metric Cards: Unlocks, Notifications, Focus Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTrendCard("${systemMetrics.first}", "Unlocks", if (systemMetrics.first > 0) "${systemMetrics.first}" else "0", CyanBrand, Icons.Default.LockOpen, Modifier.weight(1f))
                    MetricTrendCard("${systemMetrics.second}", "Notifications", if (systemMetrics.second > 0) "${systemMetrics.second}" else "0", CoralExpense, Icons.Default.Notifications, Modifier.weight(1f))
                    val focusMins = systemMetrics.third
                    val focusStr = if (focusMins > 0) "${focusMins / 60}h ${focusMins % 60}m" else "0m"
                    MetricTrendCard(focusStr, "Focus Time", if (focusMins > 0) "+${focusMins}m" else "0m", EmeraldIncome, Icons.Default.Timer, Modifier.weight(1f))
                }
            }
        }

        // 2. Daily Screen Time Dual Bars
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Daily Screen Time", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChartLegendDot(PurpleNeon, range.displayLabel)
                        ChartLegendDot(CyanBrand, range.comparisonLabel)
                    }
                }

                if (weeklyTrend.isEmpty() || weeklyTrend.all { it.thisPeriodMinutes == 0 && it.previousPeriodMinutes == 0 }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No weekly screen time data available.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    val maxMins = (weeklyTrend.maxOfOrNull { maxOf(it.thisPeriodMinutes, it.previousPeriodMinutes) } ?: 60).coerceAtLeast(60).toFloat()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weeklyTrend.forEach { trend ->
                            val thisH = if (trend.thisPeriodMinutes > 0) (trend.thisPeriodMinutes / maxMins * 70).coerceIn(1f, 70f) else 0f
                            val prevH = if (trend.previousPeriodMinutes > 0) (trend.previousPeriodMinutes / maxMins * 70).coerceIn(1f, 70f) else 0f
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .height(thisH.dp)
                                            .background(PurpleNeon, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .height(prevH.dp)
                                            .background(CyanBrand, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weeklyTrend.forEach {
                            Text(it.dayOfWeek, color = TextMuted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 3. App Usage Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("App Usage", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (appUsageList.size > 5) {
                        Text(
                            if (showAllApps) "Show Less" else "See All (${appUsageList.size}) >",
                            color = CyanBrand,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { showAllApps = !showAllApps }
                        )
                    }
                }

                if (appUsageList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (!hasPermission) "Grant usage access to see app breakdown." else "No screen time data recorded yet.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    val displayedApps = if (showAllApps) appUsageList.sortedByDescending { it.totalTimeMs } else appUsageList.sortedByDescending { it.totalTimeMs }.take(5)
                    displayedApps.forEach { app ->
                        val h = app.totalTimeMs / (1000 * 60 * 60)
                        val m = (app.totalTimeMs / (1000 * 60)) % 60
                        val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                        val pct = app.percentage.toInt().coerceIn(0, 100)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardInner)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Real app icon or styled monogram fallback
                            if (app.iconBitmap != null) {
                                Image(
                                    bitmap = app.iconBitmap,
                                    contentDescription = app.appName,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(PurpleNeon.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = app.appName.take(1).uppercase(),
                                        color = PurpleNeon,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        app.appName,
                                        color = TextWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(timeStr, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("$pct%", color = PurpleNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { pct / 100f },
                                    color = PurpleNeon,
                                    trackColor = CardBg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Usage by Category Donut Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Usage by Category", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                if (appUsageList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No category data recorded yet.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    val categories = remember(appUsageList) {
                        ScreenTimeRepository.aggregateCategories(appUsageList)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val strokeW = 10.dp.toPx()
                                var currentAngle = -90f
                                categories.forEach { cat ->
                                    val sweep = (cat.percentage / 100f) * 360f
                                    val col = cat.category.color
                                    drawArc(col, currentAngle, sweep, false, style = Stroke(strokeW))
                                    currentAngle += sweep
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(screenTimeDisplay, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Total", color = TextMuted, fontSize = 8.sp)
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f).padding(start = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            categories.take(5).forEach { cat ->
                                CategoryProgressRow(cat.category.color, cat.category.displayName, "${cat.percentage.toInt()}%")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun checkUsageAccessPermission(context: Context): Boolean {
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

// ==========================================
// SECTION 5: MONEY INSIGHTS SUMMARY (SCREEN 5)
// (READ-ONLY PER PROMPT SPECIFICATION)
// ==========================================
@Composable
private fun MoneyInsightsSummarySection(
    currentLogs: List<LogEntry>,
    previousLogs: List<LogEntry>,
    range: PeriodRange,
    onNavigateToMoney: () -> Unit,
    refreshKey: Int
) {
    val context = LocalContext.current
    val expenseEntries = currentLogs.filter { it.category == "Expense" }
    val incomeEntries = currentLogs.filter { it.category == "Income" }
    val budgetPrefs = remember(context) { context.getSharedPreferences("lifetrack_money", android.content.Context.MODE_PRIVATE) }
    val monthlyBudget = budgetPrefs.getString("monthly_expense_budget", "0")?.toDoubleOrNull() ?: 0.0
    val showSelectedMonthBudget = range.displayLabel == "This Month" || range.displayLabel == "Last Month"
    val currentMonthKey = remember(range.startDate, range.displayLabel, refreshKey) {
        if (showSelectedMonthBudget) range.startDate.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        else ""
    }
    val monthLogs = remember(context, currentMonthKey, refreshKey) {
        if (currentMonthKey.isBlank()) emptyList() else LifeTrackRepository(context).loadEntries().filter { it.date.startsWith(currentMonthKey) }
    }
    val monthExpenses = monthLogs.filter { it.category == "Expense" }.sumOf { MoneyAmounts.value(it.amount) }
    val monthIncome = monthLogs.filter { it.category == "Income" }.sumOf { MoneyAmounts.value(it.amount) }
    val budgetProgress = if (monthlyBudget > 0.0) (monthExpenses / monthlyBudget).toFloat().coerceAtLeast(0f) else 0f
    val categoryBudgets = remember(context, currentMonthKey, refreshKey) {
        MoneyCategory.load(context)
        if (currentMonthKey.isBlank()) emptyMap() else MoneyCategory.categoryBudgetsForMonth(currentMonthKey)
    }
    val categorySpending = remember(monthLogs) {
        monthLogs.filter { it.category == "Expense" }
            .groupBy { it.moneyCategory.ifBlank { "Other" } }
            .mapValues { (_, rows) -> rows.sumOf { MoneyAmounts.value(it.amount) } }
    }

    val totalExpenses = expenseEntries.sumOf { MoneyAmounts.value(it.amount) }
    val totalIncome = incomeEntries.sumOf { MoneyAmounts.value(it.amount) }
    val netSavings = totalIncome - totalExpenses

    val displayExpenses = totalExpenses
    val displayIncome = totalIncome
    val displaySavings = netSavings

    val prevExpenses = previousLogs.filter { it.category == "Expense" }.sumOf { MoneyAmounts.value(it.amount) }
    val prevIncome = previousLogs.filter { it.category == "Income" }.sumOf { MoneyAmounts.value(it.amount) }
    val expenseDiffPct = if (prevExpenses > 0) ((totalExpenses - prevExpenses) / prevExpenses * 100).toInt() else 0
    val incomeDiffPct = if (prevIncome > 0) ((totalIncome - prevIncome) / prevIncome * 100).toInt() else 0
    val savingsDiffPct = if (prevIncome - prevExpenses != 0.0) ((netSavings - (prevIncome - prevExpenses)) / Math.abs(prevIncome - prevExpenses) * 100).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Money Overview Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Money Overview", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "₹ ${String.format(Locale.US, "%,.0f", displayExpenses)}",
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (prevExpenses > 0) {
                                Spacer(Modifier.width(8.dp))
                                val isUp = expenseDiffPct >= 0
                                Surface(color = if (isUp) CoralExpense.copy(alpha = 0.18f) else EmeraldIncome.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp)) {
                                    Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (isUp) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = if (isUp) CoralExpense else EmeraldIncome, modifier = Modifier.size(12.dp))
                                        Text("${if (isUp) "+" else ""}$expenseDiffPct%", color = if (isUp) CoralExpense else EmeraldIncome, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Text("Expenses this period", color = TextMuted, fontSize = 10.sp)
                    }

                    Button(
                        onClick = onNavigateToMoney,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Money Tab >", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = CardBorder)

                // 3 Metrics: Income, Expenses, Savings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTrendCard("₹ ${String.format(Locale.US, "%,.0f", displayIncome)}", "Income", if (prevIncome > 0) "${if (incomeDiffPct >= 0) "+" else ""}$incomeDiffPct%" else "No baseline", EmeraldIncome, Icons.Default.TrendingUp, Modifier.weight(1f))
                    MetricTrendCard("₹ ${String.format(Locale.US, "%,.0f", displayExpenses)}", "Expenses", if (prevExpenses > 0) "${if (expenseDiffPct >= 0) "+" else ""}$expenseDiffPct%" else "No baseline", CoralExpense, Icons.Default.TrendingDown, Modifier.weight(1f))
                    val previousNet = prevIncome - prevExpenses
                    val savingsDeltaLabel = if (previousNet == 0.0) "No baseline" else "${if (savingsDiffPct >= 0) "+" else ""}$savingsDiffPct%"
                    MetricTrendCard("₹ ${String.format(Locale.US, "%,.0f", displaySavings)}", "Net", savingsDeltaLabel, AmberWarning, Icons.Default.Savings, Modifier.weight(1f))
                }
            }
        }

        // Show budget analytics only when the user selects This Month or Last Month.
        if (showSelectedMonthBudget) {
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Budget analytics", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${if (range.displayLabel == "Last Month") "Last month" else "This month"} · $currentMonthKey", color = TextMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = onNavigateToMoney) { Text("Manage", color = CyanBrand) }
                }
                if (monthlyBudget > 0.0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column {
                            Text("₹${String.format(Locale.US, "%,.0f", monthExpenses)} spent", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                            Text("of ₹${String.format(Locale.US, "%,.0f", monthlyBudget)} budget", color = TextMuted, fontSize = 11.sp)
                        }
                        Text("${(budgetProgress * 100).toInt()}% used", color = if (budgetProgress >= 1f) CoralExpense else EmeraldIncome, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { budgetProgress.coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                        color = if (budgetProgress >= 1f) CoralExpense else CyanBrand,
                        trackColor = CardInner
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Income ₹${String.format(Locale.US, "%,.0f", monthIncome)}", color = EmeraldIncome, fontSize = 11.sp)
                        Text(if (monthExpenses <= monthlyBudget) "₹${String.format(Locale.US, "%,.0f", monthlyBudget - monthExpenses)} remaining" else "₹${String.format(Locale.US, "%,.0f", monthExpenses - monthlyBudget)} over budget", color = if (monthExpenses <= monthlyBudget) EmeraldIncome else CoralExpense, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (categoryBudgets.isNotEmpty()) {
                        HorizontalDivider(color = CardBorder)
                        Text("Category budgets · ${if (range.displayLabel == "Last Month") "last month" else "this month"}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        categoryBudgets.entries.sortedByDescending { it.value }.take(5).forEach { (category, limit) ->
                            val spent = categorySpending[category] ?: 0.0
                            val over = (spent - limit).coerceAtLeast(0.0)
                            val progress = if (limit > 0.0) (spent / limit).toFloat().coerceAtLeast(0f) else 0f
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(category, color = TextMuted, fontSize = 11.sp)
                                    Text("₹${String.format(Locale.US, "%,.0f", spent)} / ₹${String.format(Locale.US, "%,.0f", limit)}", color = if (over > 0.0) CoralExpense else Color.White, fontSize = 10.sp)
                                }
                                LinearProgressIndicator(
                                    progress = { progress.coerceAtMost(1f) },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)),
                                    color = if (over > 0.0) CoralExpense else PurpleNeon,
                                    trackColor = CardInner
                                )
                                if (over > 0.0) Text("Over budget by ₹${String.format(Locale.US, "%,.0f", over)}", color = CoralExpense, fontSize = 10.sp)
                            }
                        }
                    }
                } else {
                    Text("Set a monthly budget to see spending progress and category limits here.", color = TextMuted, fontSize = 12.sp)
                    Button(onClick = onNavigateToMoney, colors = ButtonDefaults.buttonColors(containerColor = CyanBrand)) {
                        Text("Set up budget", color = Color(0xFF07111F))
                    }
                }
            }
        }

        }
        // 2. Spending Trend Area Line Chart Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Spending Trend", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChartLegendDot(PurpleNeon, "This Period")
                    }
                }

                if (expenseEntries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(95.dp), contentAlignment = Alignment.Center) {
                        Text("No spending recorded for this period.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    // Build up to seven real date buckets spanning the entire selected period.
                    val periodDays = (java.time.temporal.ChronoUnit.DAYS.between(range.startDate, range.endDate).toInt() + 1).coerceAtLeast(1)
                    val bucketCount = periodDays.coerceAtMost(7)
                    val bucketStarts = (0 until bucketCount).map { index ->
                        range.startDate.plusDays((index.toLong() * (periodDays - 1) / (bucketCount - 1).coerceAtLeast(1)).toLong())
                    }.distinct()
                    val dayTotals = bucketStarts.mapIndexed { index, bucketStart ->
                        val nextStart = bucketStarts.getOrNull(index + 1)
                        val bucketEnd = if (periodDays <= 7) bucketStart else (nextStart?.minusDays(1) ?: range.endDate).coerceAtLeast(bucketStart)
                        expenseEntries.filter { entry ->
                            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                            date != null && !date.isBefore(bucketStart) && !date.isAfter(bucketEnd)
                        }.sumOf { MoneyAmounts.value(it.amount) }.toFloat()
                    }
                    val maxVal = dayTotals.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                    // A zero-total bucket must remain at zero; do not manufacture visible activity.
                    val currentPoints = dayTotals.map { (it / maxVal).coerceIn(0f, 1f) }
                    val labels = bucketStarts.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp)
                    ) {
                        val stepX = if (currentPoints.size > 1) size.width / (currentPoints.size - 1) else size.width / 2f

                        fun drawSmoothCurve(points: List<Float>, color: Color) {
                            val path = Path()
                            points.forEachIndexed { i, p ->
                                val x = if (points.size > 1) i * stepX else size.width / 2f
                                val y = size.height * (1f - p)
                                if (i == 0) path.moveTo(x, y)
                                else {
                                    val prevX = (i - 1) * stepX
                                    val prevY = size.height * (1f - points[i - 1])
                                    val cx = (prevX + x) / 2f
                                    path.cubicTo(cx, prevY, cx, y, x, y)
                                }
                            }
                            drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                            points.forEachIndexed { i, p ->
                                val x = if (points.size > 1) i * stepX else size.width / 2f
                                drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, size.height * (1f - p)))
                            }
                        }

                        drawSmoothCurve(currentPoints, PurpleNeon)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        labels.forEach { label ->
                            Text(label, color = TextMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // 3. Top Spending Categories Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Top Spending Categories", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("See All >", color = CyanBrand, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onNavigateToMoney))
                }

                val categoryTotals = expenseEntries
                    .groupBy { it.title.ifBlank { "Other" } }
                    .mapValues { (_, entries) -> entries.sumOf { MoneyAmounts.value(it.amount) } }
                    .toList()
                    .sortedByDescending { it.second }

                if (categoryTotals.isEmpty()) {
                    Text("No expenses recorded for this period.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    categoryTotals.take(5).forEach { (cat, amount) ->
                        val pct = if (totalExpenses > 0) ((amount / totalExpenses) * 100).toInt() else 0
                        val color = MoneyCategory.colorFor(cat)
                        val icon = MoneyCategory.iconFor(cat)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardInner)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                            }

                            Text(cat, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))

                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                color = color,
                                trackColor = CardBg,
                                modifier = Modifier
                                    .width(65.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            Text("₹ ${String.format(Locale.US, "%,.0f", amount)}", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("$pct%", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SECTION 6: LEARNING INSIGHTS (SCREEN 6)
// ==========================================
@Composable
private fun LearningInsightsSection(
    currentLogs: List<LogEntry>,
    previousLogs: List<LogEntry>,
    range: PeriodRange,
    onNavigate: (String) -> Unit
) {
    val studyLogs = currentLogs.filter { it.category == "Study" }
    val prevStudyLogs = previousLogs.filter { it.category == "Study" }
    val studyMinutes = studyLogs.sumOf { it.amount.toIntOrNull() ?: 0 }
    val prevStudyMinutes = prevStudyLogs.sumOf { it.amount.toIntOrNull() ?: 0 }
    val hours = studyMinutes / 60
    val mins = studyMinutes % 60
    val displayStudyTime = "${hours}h ${mins}m"
    val diffMins = studyMinutes - prevStudyMinutes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Learning Overview Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Learning Overview", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(displayStudyTime, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                            if (prevStudyMinutes > 0 && diffMins != 0) {
                                Spacer(Modifier.width(8.dp))
                                val pct = (diffMins * 100 / prevStudyMinutes)
                                val isUp = pct >= 0
                                Surface(color = if (isUp) EmeraldIncome.copy(alpha = 0.18f) else CoralExpense.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp)) {
                                    Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (isUp) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = if (isUp) EmeraldIncome else CoralExpense, modifier = Modifier.size(12.dp))
                                        Text("${if (isUp) "+" else ""}$pct%", color = if (isUp) EmeraldIncome else CoralExpense, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Text(if (studyMinutes > 0) "Total focus & study time recorded" else "No study time recorded", color = TextMuted, fontSize = 10.sp)
                    }

                    IconButton(
                        onClick = { onNavigate("Study & Focus") },
                        modifier = Modifier
                            .size(36.dp)
                            .background(PinkAccent.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.Timer, "Study Timer", tint = PinkAccent, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = CardBorder)

                // 3 Metric Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTrendCard("${studyLogs.size}", "Sessions", if (studyLogs.isNotEmpty()) "${studyLogs.size}" else "0", CyanBrand, Icons.Default.MenuBook, Modifier.weight(1f))
                    val distinctDays = studyLogs.map { it.date }.distinct().size
                    MetricTrendCard("$distinctDays", "Active Days", if (distinctDays > 0) "$distinctDays" else "0", EmeraldIncome, Icons.Default.Flag, Modifier.weight(1f))
                    val durationDelta = if (prevStudyMinutes <= 0) "No baseline" else {
                        val delta = studyMinutes - prevStudyMinutes
                        "${if (delta > 0) "+" else ""}$delta min vs previous"
                    }
                    MetricTrendCard(if (hours > 0) "${hours}h" else "${mins}m", "Duration", durationDelta, AmberWarning, Icons.Default.Whatshot, Modifier.weight(1f))
                }
            }
        }

        // 2. Study Time Trend Daily Bars
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Study Time Trend", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChartLegendDot(PurpleNeon, range.displayLabel)
                        ChartLegendDot(CyanBrand, range.comparisonLabel)
                    }
                }

                if (studyLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No study sessions recorded for ${range.displayLabel}.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    val isYearView = range.displayLabel == "This Year" || java.time.temporal.ChronoUnit.DAYS.between(range.startDate, range.endDate) > 180
                    val isSingleDay = range.startDate == range.endDate

                    val (labels, dataHrs) = if (isYearView) {
                        val monthStarts = buildList {
                            var month = range.startDate.withDayOfMonth(1)
                            while (!month.isAfter(range.endDate)) {
                                add(month)
                                month = month.plusMonths(1)
                            }
                        }
                        val monthLabels = monthStarts.map { it.format(DateTimeFormatter.ofPattern("MMM", Locale.US)) }
                        val hrsList = monthStarts.map { monthStart ->
                            val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth()).coerceAtMost(range.endDate)
                            studyLogs.filter { entry ->
                                val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                                date != null && !date.isBefore(monthStart.coerceAtLeast(range.startDate)) && !date.isAfter(monthEnd)
                            }.sumOf { (it.amount.toFloatOrNull() ?: 0f) / 60f.toDouble() }.toFloat()
                        }
                        monthLabels to hrsList
                    } else {
                        val periodDays = (java.time.temporal.ChronoUnit.DAYS.between(range.startDate, range.endDate).toInt() + 1).coerceAtLeast(1)
                        val bucketCount = periodDays.coerceAtMost(7)
                        val bucketStarts = (0 until bucketCount).map { index ->
                            range.startDate.plusDays((index.toLong() * (periodDays - 1) / (bucketCount - 1).coerceAtLeast(1)).toLong())
                        }.distinct()
                        val dayLabels = bucketStarts.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) }
                        val hrsList = bucketStarts.mapIndexed { index, bucketStart ->
                            val nextStart = bucketStarts.getOrNull(index + 1)
                            val bucketEnd = if (periodDays <= 7) bucketStart else (nextStart?.minusDays(1) ?: range.endDate).coerceAtLeast(bucketStart)
                            studyLogs.filter { entry ->
                                val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                                date != null && !date.isBefore(bucketStart) && !date.isAfter(bucketEnd)
                            }.sumOf { (it.amount.toFloatOrNull() ?: 0f) / 60f.toDouble() }.toFloat()
                        }
                        dayLabels to hrsList
                    }
                    val maxStudyHrs = (dataHrs.maxOrNull() ?: 1f).coerceAtLeast(1f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        dataHrs.forEach { hrs ->
                            val barH = if (hrs > 0f) ((hrs / maxStudyHrs) * 65f).coerceIn(4f, 65f) else 0f
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(if (isYearView) 12.dp else 16.dp)
                                        .height(barH.dp)
                                        .background(if (hrs > 0f) PurpleNeon else Color.Transparent, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        labels.forEach {
                            Text(it, color = TextMuted, fontSize = if (isYearView) 8.sp else 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 3. Learning Activities Donut Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Learning Activities", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                if (studyLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No study activities recorded yet.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    val activityGroups = remember(studyLogs) {
                        studyLogs.groupBy { it.title.ifBlank { "Study" } }
                    }
                    val catColors = listOf(PurpleNeon, CyanBrand, PinkAccent, EmeraldIncome, AmberWarning)
                    val totalMinutesAll = studyMinutes.coerceAtLeast(1)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val strokeW = 10.dp.toPx()
                                var currentAngle = -90f
                                activityGroups.entries.toList().forEachIndexed { idx, entry ->
                                    val groupMins = entry.value.sumOf { it.amount.toIntOrNull() ?: 0 }
                                    val sweep = (groupMins.toFloat() / totalMinutesAll) * 360f
                                    val col = catColors[idx % catColors.size]
                                    drawArc(col, currentAngle, sweep, false, style = Stroke(strokeW))
                                    currentAngle += sweep
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(displayStudyTime, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Total", color = TextMuted, fontSize = 8.sp)
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f).padding(start = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            activityGroups.entries.toList().take(5).forEachIndexed { idx, entry ->
                                val groupMins = entry.value.sumOf { it.amount.toIntOrNull() ?: 0 }
                                val pct = (groupMins * 100 / totalMinutesAll)
                                val col = catColors[idx % catColors.size]
                                CategoryProgressRow(col, entry.key, "$pct%")
                            }
                        }
                    }
                }
            }
        }

        // 4. Language & Subject Learning Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Language & Subjects", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "See All >",
                        color = CyanBrand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigate("Study & Focus") }
                    )
                }

                if (studyLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No subjects or learning modules logged yet.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    val subjectGroups = remember(studyLogs) {
                        studyLogs.groupBy { it.title.ifBlank { "General Study" } }
                    }
                    val maxSubMins = subjectGroups.values.maxOfOrNull { it.sumOf { l -> l.amount.toIntOrNull() ?: 0 } }?.coerceAtLeast(1) ?: 1

                    subjectGroups.entries.take(4).forEach { (name, logs) ->
                        val duration = logs.sumOf { it.amount.toIntOrNull() ?: 0 }
                        val h = duration / 60
                        val m = duration % 60
                        val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                        val count = logs.size
                        val pct = ((duration.toFloat() / maxSubMins) * 100).toInt().coerceIn(0, 100)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardInner)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("$timeStr · $count session${if (count > 1) "s" else ""}", color = TextMuted, fontSize = 10.sp)
                            }
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                color = PinkAccent,
                                trackColor = CardBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PERIOD SELECTION DIALOG (SCREEN 2 OF REF 2)
// ==========================================
@Composable
private fun PeriodSelectionDialog(
    selectedPeriod: InsightPeriod,
    customStart: LocalDate,
    customEnd: LocalDate,
    onDismiss: () -> Unit,
    onApply: (InsightPeriod, LocalDate, LocalDate) -> Unit
) {
    var activeTab by remember { mutableStateOf("Quick Select") } // "Quick Select", "Custom Range"
    var tempPeriod by remember { mutableStateOf(selectedPeriod) }
    var tempStart by remember { mutableStateOf(customStart) }
    var tempEnd by remember { mutableStateOf(customEnd) }
    var startInput by remember { mutableStateOf(customStart.toString()) }
    var endInput by remember { mutableStateOf(customEnd.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Period", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tab Switcher: Quick Select | Custom Range
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardInner, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("Quick Select", "Custom Range").forEach { tab ->
                        val isSelected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) PurpleNeon else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                tab,
                                color = if (isSelected) Color.White else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                if (activeTab == "Quick Select") {
                    // 2-Column Grid of Quick Periods
                    val fullFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
                    val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.US)
                    val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
                    val quickOptions = InsightPeriod.values().map { p ->
                        val r = computePeriodRange(p, tempStart, tempEnd)
                        val sub = when (p) {
                            InsightPeriod.TODAY, InsightPeriod.YESTERDAY -> r.startDate.format(fullFmt)
                            InsightPeriod.THIS_WEEK, InsightPeriod.LAST_WEEK -> "${r.startDate.format(shortFmt)} - ${r.endDate.format(shortFmt)}"
                            InsightPeriod.THIS_MONTH, InsightPeriod.LAST_MONTH -> r.startDate.format(monthFmt)
                            InsightPeriod.THIS_YEAR -> r.startDate.year.toString()
                            InsightPeriod.CUSTOM -> "Custom dates"
                        }
                        p to sub
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(quickOptions) { (p, sub) ->
                            val isSelected = tempPeriod == p
                            Surface(
                                color = if (isSelected) PurpleNeon.copy(alpha = 0.2f) else CardInner,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, if (isSelected) PurpleNeon else Color.Transparent),
                                modifier = Modifier
                                    .clickable { tempPeriod = p }
                                    .padding(vertical = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        p.label,
                                        color = if (isSelected) PurpleNeon else TextWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        sub,
                                        color = TextMuted,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Custom Range Date Inputs
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Custom Date Range", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = startInput,
                            onValueChange = {
                                startInput = it
                                try { tempStart = LocalDate.parse(it) } catch (_: Exception) {}
                            },
                            label = { Text("Start Date (YYYY-MM-DD)") },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, null, tint = CyanBrand) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurpleNeon,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = endInput,
                            onValueChange = {
                                endInput = it
                                try { tempEnd = LocalDate.parse(it) } catch (_: Exception) {}
                            },
                            label = { Text("End Date (YYYY-MM-DD)") },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, null, tint = CyanBrand) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurpleNeon,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalPeriod = if (activeTab == "Custom Range") InsightPeriod.CUSTOM else tempPeriod
                    if (finalPeriod != InsightPeriod.CUSTOM || tempEnd >= tempStart) {
                        onApply(finalPeriod, tempStart, tempEnd)
                    }
                },
                enabled = activeTab != "Custom Range" || (runCatching { LocalDate.parse(startInput) }.getOrNull()?.let { parsedStart ->
                    runCatching { LocalDate.parse(endInput) }.getOrNull()?.let { parsedEnd -> !parsedEnd.isBefore(parsedStart) }
                } == true),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleNeon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Period", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ==========================================
// COMPARE PERIODS DIALOG (SCREEN 8 OF REF 2)
// ==========================================
@Composable
private fun ComparePeriodsDialog(
    range: PeriodRange,
    currentLogs: List<LogEntry>,
    previousLogs: List<LogEntry>,
    onDismiss: () -> Unit
) {
    var compareSubTab by remember { mutableStateOf("Overview") }
    val context = LocalContext.current
    var screenComparisonChecked by remember(range) { mutableStateOf(false) }
    var screenComparison by remember(range) { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(range) {
        screenComparisonChecked = false
        screenComparison = withContext(Dispatchers.IO) {
            if (!ScreenTimeRepository.hasUsageAccessPermission(context)) null
            else runCatching {
                val zone = java.time.ZoneId.systemDefault()
                fun minutes(start: LocalDate, end: LocalDate): Long {
                    val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
                    val until = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    return ScreenTimeRepository.queryAppUsage(context, from, until).sumOf { it.totalTimeMs } / 60_000L
                }
                minutes(range.startDate, range.endDate) to minutes(range.prevStartDate, range.prevEndDate)
            }.getOrNull()
        }
        screenComparisonChecked = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Compare Periods", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Period Comparison Selector Card: "This Week" <-> "Last Week"
                Surface(
                    color = CardInner,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(range.displayLabel, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(range.startDate.toString(), color = TextMuted, fontSize = 9.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(CardBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SwapHoriz, null, tint = CyanBrand, modifier = Modifier.size(16.dp))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(range.comparisonLabel, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(range.prevStartDate.toString(), color = TextMuted, fontSize = 9.sp)
                        }
                    }
                }

                // Sub-tabs: Overview | Productivity | Habits | Screen | Money
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("Overview", "Productivity", "Habits", "Screen", "Money")) { tab ->
                        val isSelected = compareSubTab == tab
                        Surface(
                            color = if (isSelected) PurpleNeon else CardInner,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable { compareSubTab = tab }
                        ) {
                            Text(
                                tab,
                                color = if (isSelected) Color.White else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                val context = LocalContext.current
                val allTasks = TaskRepository.getTasks(context)
                fun tasksFor(start: LocalDate, end: LocalDate) = allTasks.filter { task ->
                    val date = runCatching { LocalDate.parse(task.dueDate) }.getOrNull()
                    date != null && !date.isBefore(start) && !date.isAfter(end)
                }
                val currentTasks = tasksFor(range.startDate, range.endDate)
                val previousTasks = tasksFor(range.prevStartDate, range.prevEndDate)
                fun completedTasksFor(start: LocalDate, end: LocalDate) = allTasks.filter { task ->
                    task.isCompleted && task.completedAtMs?.let { completedAt ->
                        java.time.Instant.ofEpochMilli(completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            .let { date -> !date.isBefore(start) && !date.isAfter(end) }
                    } == true
                }
                val currentCompletedTasks = completedTasksFor(range.startDate, range.endDate)
                val previousCompletedTasks = completedTasksFor(range.prevStartDate, range.prevEndDate)
                val currentCompletions = HabitRoutineRepository.getCompletions(context).filter {
                    it.status.equals("completed", true) && runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> !d.isBefore(range.startDate) && !d.isAfter(range.endDate) } == true
                }
                val previousCompletions = HabitRoutineRepository.getCompletions(context).filter {
                    it.status.equals("completed", true) && runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> !d.isBefore(range.prevStartDate) && !d.isAfter(range.prevEndDate) } == true
                }
                val currentSpend = currentLogs.filter { it.category.equals("Expense", true) }.sumOf { MoneyAmounts.value(it.amount) }
                val previousSpend = previousLogs.filter { it.category.equals("Expense", true) }.sumOf { MoneyAmounts.value(it.amount) }
                val currentIncome = currentLogs.filter { it.category.equals("Income", true) }.sumOf { MoneyAmounts.value(it.amount) }
                val previousIncome = previousLogs.filter { it.category.equals("Income", true) }.sumOf { MoneyAmounts.value(it.amount) }
                val currentStudy = currentLogs.filter { it.category.equals("Study", true) }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                val previousStudy = previousLogs.filter { it.category.equals("Study", true) }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                val currentHabitRate = HabitRoutineRepository.calculateOverallHabitsCompletionRate(context, range.startDate, range.endDate)
                val previousHabitRate = HabitRoutineRepository.calculateOverallHabitsCompletionRate(context, range.prevStartDate, range.prevEndDate)
                fun change(current: Double, previous: Double): String =
                    if (previous == 0.0) "No baseline" else "${if (current - previous >= 0) "+" else ""}${(((current - previous) / kotlin.math.abs(previous)) * 100).toInt()}%"

                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardInner).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Metric", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Current", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Previous", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Change", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = CardBorder)
                    when (compareSubTab) {
                        "Overview" -> {
                            CompareMetricRow("Tasks completed", currentCompletedTasks.size.toString(), previousCompletedTasks.size.toString(), change(currentCompletedTasks.size.toDouble(), previousCompletedTasks.size.toDouble()), currentCompletedTasks.size >= previousCompletedTasks.size)
                            CompareMetricRow("Habit completion", "${currentHabitRate.toInt()}%", "${previousHabitRate.toInt()}%", change(currentHabitRate, previousHabitRate), currentHabitRate >= previousHabitRate)
                            CompareMetricRow("Money spent", "₹${String.format(Locale.US, "%,.0f", currentSpend)}", "₹${String.format(Locale.US, "%,.0f", previousSpend)}", change(currentSpend, previousSpend), currentSpend <= previousSpend)
                            CompareMetricRow("Study log time", "${(currentStudy / 60).toInt()}h ${currentStudy.toInt() % 60}m", "${(previousStudy / 60).toInt()}h ${previousStudy.toInt() % 60}m", change(currentStudy, previousStudy), currentStudy >= previousStudy)
                        }
                        "Productivity" -> {
                            CompareMetricRow("Completed tasks", currentCompletedTasks.size.toString(), previousCompletedTasks.size.toString(), change(currentCompletedTasks.size.toDouble(), previousCompletedTasks.size.toDouble()), currentCompletedTasks.size >= previousCompletedTasks.size)
                            CompareMetricRow("Pending tasks", currentTasks.count { !it.isCompleted }.toString(), previousTasks.count { !it.isCompleted }.toString(), change(currentTasks.count { !it.isCompleted }.toDouble(), previousTasks.count { !it.isCompleted }.toDouble()), currentTasks.count { !it.isCompleted } <= previousTasks.count { !it.isCompleted })
                            CompareMetricRow("Tasks in range", currentTasks.size.toString(), previousTasks.size.toString(), change(currentTasks.size.toDouble(), previousTasks.size.toDouble()), currentTasks.size >= previousTasks.size)
                        }
                        "Habits" -> {
                            CompareMetricRow("Completed check-ins", currentCompletions.size.toString(), previousCompletions.size.toString(), change(currentCompletions.size.toDouble(), previousCompletions.size.toDouble()), currentCompletions.size >= previousCompletions.size)
                            CompareMetricRow("Completion rate", "${currentHabitRate.toInt()}%", "${previousHabitRate.toInt()}%", change(currentHabitRate, previousHabitRate), currentHabitRate >= previousHabitRate)
                        }
                        "Money" -> {
                            CompareMetricRow("Income", "₹${String.format(Locale.US, "%,.0f", currentIncome)}", "₹${String.format(Locale.US, "%,.0f", previousIncome)}", change(currentIncome, previousIncome), currentIncome >= previousIncome)
                            CompareMetricRow("Expenses", "₹${String.format(Locale.US, "%,.0f", currentSpend)}", "₹${String.format(Locale.US, "%,.0f", previousSpend)}", change(currentSpend, previousSpend), currentSpend <= previousSpend)
                            CompareMetricRow("Net", "₹${String.format(Locale.US, "%,.0f", currentIncome - currentSpend)}", "₹${String.format(Locale.US, "%,.0f", previousIncome - previousSpend)}", change(currentIncome - currentSpend, previousIncome - previousSpend), currentIncome - currentSpend >= previousIncome - previousSpend)
                        }
                        "Screen" -> {
                            when {
                                !screenComparisonChecked -> Text("Loading Android Usage Stats…", color = TextMuted, fontSize = 11.sp)
                                screenComparison == null -> Text("Screen-time comparison is unavailable. Enable Android Usage Access to read device-measured usage; no estimate is substituted.", color = TextMuted, fontSize = 11.sp)
                                else -> {
                                    val currentMinutes = screenComparison!!.first
                                    val previousMinutes = screenComparison!!.second
                                    CompareMetricRow(
                                        "Device usage",
                                        "${currentMinutes / 60}h ${currentMinutes % 60}m",
                                        "${previousMinutes / 60}h ${previousMinutes % 60}m",
                                        change(currentMinutes.toDouble(), previousMinutes.toDouble()),
                                        currentMinutes <= previousMinutes
                                    )
                                }
                            }
                        }
                    }
                }

                Text("Comparison uses saved records for each selected date range. A zero previous-period value is shown as “No baseline”, not a fabricated 0% change.", color = TextMuted, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CyanBrand)
            }
        }
    )
}

@Composable
private fun CompareMetricRow(
    title: String,
    current: String,
    previous: String,
    change: String,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.3f))
        Text(current, color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
        Text(previous, color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(0.9f))
        Text(change, color = if (isPositive) EmeraldIncome else CoralExpense, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
    }
}

@Composable
private fun KeyChangeBullet(text: String, isPositive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
            null,
            tint = if (isPositive) EmeraldIncome else CoralExpense,
            modifier = Modifier.size(12.dp)
        )
        Text(text, color = TextWhite, fontSize = 10.sp)
    }
}


/**
 * Cross-feature analytics overview. Every number below is derived from saved
 * tracker entries, tasks, habit completions, routine sessions, personal goals,
 * or Android usage statistics. No synthetic chart points or demo values.
 */
@Composable
private fun FullLifeAnalyticsDashboard(
    context: Context,
    logs: List<LogEntry>,
    range: PeriodRange,
    refreshKey: Int
) {
    val start = range.startDate
    val end = range.endDate
    val validLogs = remember(logs, start, end) {
        logs.filter { entry ->
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
            date != null && !date.isBefore(start) && !date.isAfter(end)
        }
    }
    val allTasks = remember(refreshKey) { TaskRepository.getTasks(context) }
    val tasks = remember(allTasks, start, end) {
        allTasks.filter { task ->
            val date = runCatching { LocalDate.parse(task.dueDate) }.getOrNull()
            date != null && !date.isBefore(start) && !date.isAfter(end)
        }
    }
    val habits = remember(refreshKey) { HabitRoutineRepository.getHabits(context) }
    val habitCompletions = remember(start, end, refreshKey) {
        HabitRoutineRepository.getCompletions(context).filter { completion ->
            val date = runCatching { LocalDate.parse(completion.date) }.getOrNull()
            date != null && !date.isBefore(start) && !date.isAfter(end)
        }
    }
    val routines = remember(refreshKey) { HabitRoutineRepository.getRoutines(context) }
    val routineSessions = remember(start, end, refreshKey) {
        HabitRoutineRepository.getRoutineSessions(context).filter { session ->
            val date = runCatching { LocalDate.parse(session.date) }.getOrNull()
            date != null && !date.isBefore(start) && !date.isAfter(end)
        }
    }
    val goals = remember(refreshKey) { PersonalGoalsRepository.getGoals(context) }
    val focusSessions = remember(start, end, refreshKey) {
        StudyRepository.loadSessions(context).filter { session ->
            val date = runCatching { LocalDate.parse(session.date) }.getOrNull()
            session.isCompleted && date != null && !date.isBefore(start) && !date.isAfter(end)
        }
    }
    val income = validLogs.filter { it.category.equals("Income", true) }.sumOf { MoneyAmounts.value(it.amount) }
    val expenses = validLogs.filter { it.category.equals("Expense", true) }.sumOf { MoneyAmounts.value(it.amount) }
    val waterMl = validLogs.filter { it.category.equals("Water", true) }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val studyMinutes = validLogs.filter { it.category.equals("Study", true) }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val completedTasks = tasks.count { it.isCompleted }
    val completedHabits = habitCompletions.count { it.status.equals("completed", true) }
    val completedRoutines = routineSessions.count { it.isCompleted }
    val activeGoals = goals.count { !it.isCompleted }
    val completedGoals = goals.count { it.isCompleted }
    val dayCount = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
    val screenTimeTrendDates = remember(start, end) {
        val count = dayCount.coerceIn(1, 7)
        (0 until count).map { index ->
            start.plusDays((index.toLong() * (dayCount - 1).coerceAtLeast(0) / (count - 1).coerceAtLeast(1)).toLong())
        }.distinct()
    }
    var screenTimeTrend by remember(start, end, refreshKey) { mutableStateOf<List<Float>?>(null) }
    var screenTimeTrendChecked by remember(start, end, refreshKey) { mutableStateOf(false) }
    LaunchedEffect(start, end, refreshKey) {
        screenTimeTrendChecked = false
        screenTimeTrend = withContext(Dispatchers.IO) {
            if (!ScreenTimeRepository.hasUsageAccessPermission(context)) null
            else runCatching {
                screenTimeTrendDates.mapIndexed { index, bucketStart ->
                    val next = screenTimeTrendDates.getOrNull(index + 1)
                    val bucketEnd = if (dayCount <= 7) bucketStart else (next?.minusDays(1) ?: end).coerceAtLeast(bucketStart)
                    val zone = java.time.ZoneId.systemDefault()
                    val from = bucketStart.atStartOfDay(zone).toInstant().toEpochMilli()
                    val until = bucketEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    ScreenTimeRepository.queryAppUsage(context, from, until).sumOf { it.totalTimeMs }.toFloat() / 60_000f
                }
            }.getOrNull()
        }
        screenTimeTrendChecked = true
    }
    val screenTimeMinutes = screenTimeTrend?.sum()?.toLong()
    val trackerCategories = remember(validLogs) {
        validLogs.groupBy { it.category.trim().ifBlank { "Uncategorized" } }
            .mapValues { (_, entries) -> entries.size }
            .toList().sortedByDescending { it.second }
    }
    val bucketDates = remember(start, end) {
        val count = dayCount.coerceAtMost(14)
        if (count <= 0) emptyList() else (0 until count).map { index ->
            if (dayCount <= 14) start.plusDays(index.toLong())
            else start.plusDays((index.toLong() * (dayCount - 1) / (count - 1).coerceAtLeast(1)).toLong())
        }.distinct()
    }
    val dailyActivityCounts = remember(validLogs, bucketDates, dayCount) {
        bucketDates.map { date ->
            val bucketEnd = if (dayCount <= 14) date else {
                val index = bucketDates.indexOf(date)
                val next = bucketDates.getOrNull(index + 1)
                (next?.minusDays(1) ?: end).coerceAtLeast(date)
            }
            validLogs.count { entry ->
                val d = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                d != null && !d.isBefore(date) && !d.isAfter(bucketEnd)
            }
        }
    }
    val completedWorkTrend = remember(allTasks, habitCompletions, routineSessions, bucketDates, dayCount) {
        bucketDates.map { date ->
            val index = bucketDates.indexOf(date)
            val next = bucketDates.getOrNull(index + 1)
            val bucketEnd = if (dayCount <= 14) date else (next?.minusDays(1) ?: end).coerceAtLeast(date)
            allTasks.count { task ->
                task.isCompleted && task.completedAtMs?.let { completedAt ->
                    java.time.Instant.ofEpochMilli(completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        .let { d -> !d.isBefore(date) && !d.isAfter(bucketEnd) }
                } == true
            } +
                habitCompletions.count { it.status.equals("completed", true) && runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> !d.isBefore(date) && !d.isAfter(bucketEnd) } == true } +
                routineSessions.count { it.isCompleted && runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> !d.isBefore(date) && !d.isAfter(bucketEnd) } == true }
        }
    }
    val dailyTaskCompletions = remember(allTasks, bucketDates, dayCount) {
        bucketDates.mapIndexed { index, date ->
            val next = bucketDates.getOrNull(index + 1)
            val bucketEnd = if (dayCount <= 14) date else (next?.minusDays(1) ?: end).coerceAtLeast(date)
            allTasks.count { task ->
                task.isCompleted && task.completedAtMs?.let { completedAt ->
                    java.time.Instant.ofEpochMilli(completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        .let { d -> !d.isBefore(date) && !d.isAfter(bucketEnd) }
                } == true
            }.toFloat()
        }
    }
    val dailyHabitCompletions = remember(habitCompletions, bucketDates, dayCount) {
        bucketDates.mapIndexed { index, date ->
            val next = bucketDates.getOrNull(index + 1)
            val bucketEnd = if (dayCount <= 14) date else (next?.minusDays(1) ?: end).coerceAtLeast(date)
            habitCompletions.count { completion ->
                completion.status.equals("completed", true) && runCatching { LocalDate.parse(completion.date) }.getOrNull()?.let { d -> !d.isBefore(date) && !d.isAfter(bucketEnd) } == true
            }.toFloat()
        }
    }
    val dailyRoutineCompletions = remember(routineSessions, bucketDates, dayCount) {
        bucketDates.mapIndexed { index, date ->
            val next = bucketDates.getOrNull(index + 1)
            val bucketEnd = if (dayCount <= 14) date else (next?.minusDays(1) ?: end).coerceAtLeast(date)
            routineSessions.count { session ->
                session.isCompleted && runCatching { LocalDate.parse(session.date) }.getOrNull()?.let { d -> !d.isBefore(date) && !d.isAfter(bucketEnd) } == true
            }.toFloat()
        }
    }
    val dailyFocusMinutes = remember(focusSessions, bucketDates, dayCount) {
        bucketDates.mapIndexed { index, date ->
            val next = bucketDates.getOrNull(index + 1)
            val bucketEnd = if (dayCount <= 14) date else (next?.minusDays(1) ?: end).coerceAtLeast(date)
            focusSessions.filter { session ->
                runCatching { LocalDate.parse(session.date) }.getOrNull()?.let { d -> !d.isBefore(date) && !d.isAfter(bucketEnd) } == true
            }.sumOf { it.focusMinutes }.toFloat()
        }
    }
    val dailyIncome = remember(validLogs, bucketDates, dayCount) {
        bucketDates.map { date ->
            val index = bucketDates.indexOf(date)
            val next = bucketDates.getOrNull(index + 1)
            val bucketEnd = if (dayCount <= 14) date else (next?.minusDays(1) ?: end).coerceAtLeast(date)
            validLogs.filter { it.category.equals("Income", true) }.sumOf { entry ->
                val d = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                if (d != null && !d.isBefore(date) && !d.isAfter(bucketEnd)) MoneyAmounts.value(entry.amount) else 0.0
            }.toFloat()
        }
    }
    val dailyWater = remember(validLogs, bucketDates, dayCount) {
        bucketDates.map { date ->
            val index = bucketDates.indexOf(date)
            val next = bucketDates.getOrNull(index + 1)
            val bucketEnd = if (dayCount <= 14) date else (next?.minusDays(1) ?: end).coerceAtLeast(date)
            validLogs.filter { it.category.equals("Water", true) }.sumOf { entry ->
                val d = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                if (d != null && !d.isBefore(date) && !d.isAfter(bucketEnd)) entry.amount.toDoubleOrNull() ?: 0.0 else 0.0
            }.toFloat()
        }
    }
    val dailyStudy = remember(validLogs, bucketDates, dayCount) {
        bucketDates.map { date ->
            val index = bucketDates.indexOf(date)
            val next = bucketDates.getOrNull(index + 1)
            val bucketEnd = if (dayCount <= 14) date else (next?.minusDays(1) ?: end).coerceAtLeast(date)
            validLogs.filter { it.category.equals("Study", true) }.sumOf { entry ->
                val d = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                if (d != null && !d.isBefore(date) && !d.isAfter(bucketEnd)) entry.amount.toDoubleOrNull() ?: 0.0 else 0.0
            }.toFloat()
        }
    }
    val dailySpend = remember(validLogs, bucketDates, dayCount) {
        bucketDates.map { date ->
            val index = bucketDates.indexOf(date)
            val next = bucketDates.getOrNull(index + 1)
            val bucketEnd = if (dayCount <= 14) date else (next?.minusDays(1) ?: end).coerceAtLeast(date)
            validLogs.filter { it.category.equals("Expense", true) }.sumOf { entry ->
                val d = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                if (d != null && !d.isBefore(date) && !d.isAfter(bucketEnd)) MoneyAmounts.value(entry.amount) else 0.0
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(color = CardBg, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LifeTrack · Full Analytics", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${range.displayLabel} · ${start.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))}", color = TextMuted, fontSize = 11.sp)
                Text("${validLogs.size} saved tracker entries · ${tasks.size} tasks · ${habits.size} active habits · ${routines.size} active routines", color = TextMuted, fontSize = 11.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AnalyticsMetricCard("Tasks", "$completedTasks / ${tasks.size}", if (tasks.isNotEmpty()) "${completedTasks * 100 / tasks.size}% completed" else "No task data", PurpleNeon, Modifier.weight(1f))
            AnalyticsMetricCard("Habits", "$completedHabits", if (habitCompletions.isNotEmpty()) "${habitCompletions.size} check-ins" else "No check-ins", EmeraldIncome, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AnalyticsMetricCard("Routines", "$completedRoutines / ${routineSessions.size}", if (routineSessions.isNotEmpty()) "${completedRoutines * 100 / routineSessions.size}% completed" else "No sessions", CyanBrand, Modifier.weight(1f))
            AnalyticsMetricCard("Goals", "$completedGoals / ${goals.size}", if (goals.isNotEmpty()) "$activeGoals active" else "No goals saved", PinkAccent, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AnalyticsMetricCard("Income", "₹${String.format(Locale.US, "%,.0f", income)}", "${validLogs.count { it.category.equals("Income", true) }} records", EmeraldIncome, Modifier.weight(1f))
            AnalyticsMetricCard("Expenses", "₹${String.format(Locale.US, "%,.0f", expenses)}", "Net ₹${String.format(Locale.US, "%,.0f", income - expenses)}", CoralExpense, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AnalyticsMetricCard("Water", if (validLogs.any { it.category.equals("Water", true) }) "${(waterMl / 1000.0).let { String.format(Locale.US, "%.1f L", it) }}" else "—", "Saved water logs", CyanBrand, Modifier.weight(1f))
            AnalyticsMetricCard("Study", if (validLogs.any { it.category.equals("Study", true) }) "${(studyMinutes / 60).toInt()}h ${studyMinutes.toInt() % 60}m" else "—", "Saved study logs", PinkAccent, Modifier.weight(1f))
        }
        AnalyticsChartCard(
            title = "Activity trend",
            subtitle = "Saved tracker entries per date bucket",
            labels = bucketDates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) },
            values = dailyActivityCounts.map { it.toFloat() },
            tint = PurpleNeon,
            valueSuffix = ""
        )
        AnalyticsChartCard(
            title = "Task completion trend",
            subtitle = "Completed saved tasks by date bucket",
            labels = bucketDates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) },
            values = dailyTaskCompletions,
            tint = PurpleNeon,
            valueSuffix = ""
        )
        AnalyticsChartCard(
            title = "Habit completion trend",
            subtitle = "Completed saved habit check-ins by date bucket",
            labels = bucketDates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) },
            values = dailyHabitCompletions,
            tint = EmeraldIncome,
            valueSuffix = ""
        )
        AnalyticsChartCard(
            title = "Routine completion trend",
            subtitle = "Completed saved routine sessions by date bucket",
            labels = bucketDates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) },
            values = dailyRoutineCompletions,
            tint = CyanBrand,
            valueSuffix = ""
        )
        AnalyticsChartCard(
            title = "Focus session trend",
            subtitle = "Completed Study page focus sessions (minutes)",
            labels = bucketDates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) },
            values = dailyFocusMinutes,
            tint = PinkAccent,
            valueSuffix = "min "
        )
        AnalyticsChartCard(
            title = "Completed work trend",
            subtitle = "Completed tasks, habit check-ins and routine sessions",
            labels = bucketDates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) },
            values = completedWorkTrend.map { it.toFloat() },
            tint = EmeraldIncome,
            valueSuffix = ""
        )
        AnalyticsChartCard(
            title = "Income trend",
            subtitle = "Recorded income by date bucket",
            labels = bucketDates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) },
            values = dailyIncome,
            tint = EmeraldIncome,
            valueSuffix = "₹"
        )
        AnalyticsMetricCard(
            "Focus sessions",
            focusSessions.size.toString(),
            if (focusSessions.isNotEmpty()) "${focusSessions.sumOf { it.focusMinutes }} saved focus minutes" else "No completed sessions",
            PinkAccent,
            Modifier.fillMaxWidth()
        )
        AnalyticsChartCard(
            title = "Water trend",
            subtitle = "Saved water logs by date bucket (ml)",
            labels = bucketDates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) },
            values = dailyWater,
            tint = CyanBrand,
            valueSuffix = "ml "
        )
        AnalyticsChartCard(
            title = "Study trend",
            subtitle = "Saved study logs by date bucket (minutes)",
            labels = bucketDates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) },
            values = dailyStudy,
            tint = PinkAccent,
            valueSuffix = "min "
        )
        AnalyticsChartCard(
            title = "Spending trend",
            subtitle = "Recorded expenses by date bucket",
            labels = bucketDates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) },
            values = dailySpend.map { it.toFloat().coerceAtLeast(0f) },
            tint = CoralExpense,
            valueSuffix = "₹"
        )
        Surface(color = CardBg, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tracker category breakdown", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (trackerCategories.isEmpty()) {
                    Text("No tracker entries in this period. Charts will populate as you save real activity.", color = TextMuted, fontSize = 12.sp)
                } else {
                    val maxCount = trackerCategories.maxOf { it.second }.coerceAtLeast(1)
                    trackerCategories.forEach { (category, count) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(category, color = TextWhite, fontSize = 11.sp)
                                Text("$count", color = TextMuted, fontSize = 11.sp)
                            }
                            LinearProgressIndicator(
                                progress = { count.toFloat() / maxCount },
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                                color = when {
                                    category.equals("Income", true) -> EmeraldIncome
                                    category.equals("Expense", true) -> CoralExpense
                                    category.equals("Water", true) -> CyanBrand
                                    category.equals("Study", true) -> PinkAccent
                                    else -> PurpleNeon
                                },
                                trackColor = CardInner
                            )
                        }
                    }
                }
            }
        }
        Surface(color = CardBg, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Screen Time", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                when {
                    !screenTimeTrendChecked -> Text("Loading Android usage statistics…", color = TextMuted, fontSize = 12.sp)
                    screenTimeMinutes == null -> Text("Android Usage Access is not available. Enable it in Screen Time settings to include device-measured app usage.", color = TextMuted, fontSize = 12.sp)
                    screenTimeMinutes == 0L -> Text("No app-usage records were returned for this period.", color = TextMuted, fontSize = 12.sp)
                    else -> Text("${screenTimeMinutes / 60}h ${screenTimeMinutes % 60}m total device app usage", color = CyanBrand, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text("Screen Time is queried from Android Usage Stats for the selected dates; it is not estimated from tracker logs.", color = TextMuted, fontSize = 10.sp)
            }
        }
        if (screenTimeTrendChecked && screenTimeTrend != null) {
            AnalyticsChartCard(
                title = "Device screen-time trend",
                subtitle = "Android Usage Stats by date bucket (minutes)",
                labels = screenTimeTrendDates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) },
                values = screenTimeTrend ?: emptyList(),
                tint = CyanBrand,
                valueSuffix = "min "
            )
        }
        Surface(color = CardBg, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Personal goal progress", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (goals.isEmpty()) {
                    Text("No personal goals saved yet.", color = TextMuted, fontSize = 12.sp)
                } else {
                    goals.forEach { goal ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(goal.title, color = TextWhite, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${goal.progressPercentage}% ", color = if (goal.isCompleted) EmeraldIncome else CyanBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(progress = { goal.progressFraction }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)), color = if (goal.isCompleted) EmeraldIncome else PurpleNeon, trackColor = CardInner)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsMetricCard(
    title: String,
    value: String,
    detail: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(color = CardBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = TextMuted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AnalyticsChartCard(
    title: String,
    subtitle: String,
    labels: List<String>,
    values: List<Float>,
    tint: Color,
    valueSuffix: String
) {
    Surface(color = CardBg, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, fontSize = 10.sp)
            if (values.isEmpty() || values.all { it == 0f }) {
                Text("No data available for this chart in the selected period.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
            } else {
                val maxValue = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                Canvas(Modifier.fillMaxWidth().height(124.dp)) {
                    val slot = size.width / values.size.coerceAtLeast(1)
                    val barWidth = (slot * 0.48f).coerceAtLeast(3.dp.toPx())
                    values.forEachIndexed { index, value ->
                        val barHeight = (value.coerceAtLeast(0f) / maxValue) * (size.height - 8.dp.toPx())
                        val left = slot * index + (slot - barWidth) / 2f
                        drawRoundRect(
                            color = tint.copy(alpha = if (value > 0f) 0.92f else 0.18f),
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight.coerceAtLeast(2.dp.toPx())),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    labels.forEachIndexed { index, label ->
                        if (labels.size <= 7 || index % 2 == 0 || index == labels.lastIndex) {
                            Text(label, color = TextMuted, fontSize = 8.sp, maxLines = 1)
                        }
                    }
                }
                Text("Peak: $valueSuffix${String.format(Locale.US, "%,.1f", values.maxOrNull() ?: 0f)}", color = tint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ==========================================
// DATE & PERIOD CALCULATION LOGIC
// ==========================================
private fun computePeriodRange(
    period: InsightPeriod,
    customStart: LocalDate,
    customEnd: LocalDate
): PeriodRange {
    val today = LocalDate.now()
    return when (period) {
        InsightPeriod.TODAY -> PeriodRange(
            startDate = today,
            endDate = today,
            prevStartDate = today.minusDays(1),
            prevEndDate = today.minusDays(1),
            displayLabel = "Today",
            comparisonLabel = "Yesterday"
        )
        InsightPeriod.YESTERDAY -> PeriodRange(
            startDate = today.minusDays(1),
            endDate = today.minusDays(1),
            prevStartDate = today.minusDays(2),
            prevEndDate = today.minusDays(2),
            displayLabel = "Yesterday",
            comparisonLabel = "2 Days Ago"
        )
        InsightPeriod.THIS_WEEK -> {
            // Compare only elapsed days so future days in the current week do not dilute this week's totals.
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val prevStart = start.minusWeeks(1)
            val prevEnd = today.minusWeeks(1)
            PeriodRange(start, today, prevStart, prevEnd, "This Week", "Same days last week")
        }
        InsightPeriod.LAST_WEEK -> {
            val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val start = thisWeekStart.minusWeeks(1)
            val end = start.plusDays(6)
            val prevStart = start.minusWeeks(1)
            val prevEnd = end.minusWeeks(1)
            PeriodRange(start, end, prevStart, prevEnd, "Last Week", "2 Weeks Ago")
        }
        InsightPeriod.THIS_MONTH -> {
            // Compare month-to-date against the same elapsed dates in the previous month.
            val start = today.withDayOfMonth(1)
            val prevStart = start.minusMonths(1)
            val prevEnd = prevStart.plusDays((today.dayOfMonth - 1).toLong())
                .coerceAtMost(prevStart.withDayOfMonth(prevStart.lengthOfMonth()))
            PeriodRange(start, today, prevStart, prevEnd, "This Month", "Same dates last month")
        }
        InsightPeriod.LAST_MONTH -> {
            val thisMonthStart = today.withDayOfMonth(1)
            val start = thisMonthStart.minusMonths(1)
            val end = start.withDayOfMonth(start.lengthOfMonth())
            val prevStart = start.minusMonths(1)
            val prevEnd = prevStart.withDayOfMonth(prevStart.lengthOfMonth())
            PeriodRange(start, end, prevStart, prevEnd, "Last Month", "2 Months Ago")
        }
        InsightPeriod.THIS_YEAR -> {
            // Year-to-date is compared with the equivalent elapsed dates in the previous year.
            val start = today.withDayOfYear(1)
            val prevStart = start.minusYears(1)
            val prevEnd = today.minusYears(1)
            PeriodRange(start, today, prevStart, prevEnd, "This Year", "Same dates last year")
        }
        InsightPeriod.CUSTOM -> {
            val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(customStart, customEnd).coerceAtLeast(0)
            val prevEnd = customStart.minusDays(1)
            val prevStart = prevEnd.minusDays(daysDiff)
            PeriodRange(customStart, customEnd, prevStart, prevEnd, "Custom Period", "Previous Period")
        }
    }
}

// ==========================================
// WATER / HYDRATION INSIGHTS SECTION & CARDS
// ==========================================
@Composable
private fun WaterOverviewCard(
    currentLogs: List<LogEntry>,
    range: PeriodRange,
    refreshKey: Int,
    onOpenWater: () -> Unit
) {
    val context = LocalContext.current
    val targetMl = remember(context, refreshKey) { Goals.waterTarget(context) }
    val waterLogs = currentLogs.filter { it.category.equals("Water", ignoreCase = true) }
    val totalMl = waterLogs.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
    val totalDays = java.time.temporal.ChronoUnit.DAYS.between(range.startDate, range.endDate).toInt() + 1
    val dailyAvg = if (totalDays > 0) totalMl / totalDays else 0
    val progressPct = ((dailyAvg.toFloat() / targetMl.toFloat()) * 100).toInt().coerceIn(0, 100)

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenWater)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(32.dp).background(BlueAccent.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Hydration & Water", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(range.displayLabel, color = TextMuted, fontSize = 10.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Details", color = PurpleNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ChevronRight, null, tint = PurpleNeon, modifier = Modifier.size(14.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${String.format(Locale.US, "%.1f", totalMl / 1000f)} L Total",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Avg: ${String.format(Locale.US, "%.1f", dailyAvg / 1000f)} L/day (Goal: ${String.format(Locale.US, "%.1f", targetMl / 1000f)} L)",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Text("$progressPct%", color = CyanBrand, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }

            LinearProgressIndicator(
                progress = (dailyAvg.toFloat() / targetMl.toFloat()).coerceIn(0f, 1f),
                color = CyanBrand,
                trackColor = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun WaterInsightsSection(
    currentLogs: List<LogEntry>,
    previousLogs: List<LogEntry>,
    range: PeriodRange,
    onNavigateToWater: () -> Unit
) {
    val context = LocalContext.current
    val targetMl = remember(range) { Goals.waterTarget(context) }
    val currentWater = currentLogs.filter { it.category.equals("Water", ignoreCase = true) }
    val prevWater = previousLogs.filter { it.category.equals("Water", ignoreCase = true) }

    val currentTotal = currentWater.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
    val prevTotal = prevWater.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
    val totalDays = java.time.temporal.ChronoUnit.DAYS.between(range.startDate, range.endDate).toInt() + 1
    val dailyAvg = if (totalDays > 0) currentTotal / totalDays else 0
    val pctChange = if (prevTotal > 0) (((currentTotal - prevTotal).toFloat() / prevTotal.toFloat()) * 100).toInt() else null

    // Daily totals across the date range
    val dayPoints = remember(range, currentWater) {
        val list = mutableListOf<Pair<String, Float>>()
        var cur = range.startDate
        val fmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
        while (!cur.isAfter(range.endDate)) {
            val dStr = cur.toString()
            val daySum = currentWater.filter { it.date == dStr }.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
            val label = if (totalDays <= 7) cur.format(fmt) else cur.dayOfMonth.toString()
            list.add(Pair(label, daySum / 1000f))
            cur = cur.plusDays(1)
        }
        list
    }

    // Goal met count
    val metDays = dayPoints.count { (it.second * 1000) >= targetMl }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Main Hydration Summary Card
        Surface(
            color = CardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Hydration Trends", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${range.displayLabel} · ${String.format(Locale.US, "%.1f", currentTotal / 1000f)} L recorded", color = TextMuted, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardInner)
                            .clickable(onClick = onNavigateToWater)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Open Water", color = PurpleNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Bar Chart
                val maxL = (dayPoints.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    dayPoints.take(14).forEach { (label, litres) ->
                        val fraction = if (litres > 0f) (litres / maxL).coerceIn(0.01f, 1f) else 0f
                        val isAboveZero = litres > 0f
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        if (isAboveZero) Brush.verticalGradient(listOf(PurpleNeon, CyanBrand))
                                        else Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF141F33)))
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(label, color = TextMuted, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 4 Key Hydration Metric Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricStatCard(
                icon = Icons.Default.WaterDrop,
                iconTint = BlueAccent,
                value = "${String.format(Locale.US, "%.1f", dailyAvg / 1000f)} L",
                label = "Daily Avg",
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                icon = Icons.Default.TrackChanges,
                iconTint = PurpleNeon,
                value = "$metDays / $totalDays",
                label = "Goal Days",
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                icon = Icons.Default.LocalFireDepartment,
                iconTint = AmberWarning,
                value = "${WaterTotals.streak(currentLogs, LocalDate.now().toString(), targetMl)}d",
                label = "Streak",
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                icon = if (pctChange == null || pctChange >= 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                iconTint = if (pctChange == null || pctChange >= 0) TextMuted else CoralExpense,
                value = pctChange?.let { "${if (it >= 0) "+" else ""}$it%" } ?: "No baseline",
                label = range.comparisonLabel,
                modifier = Modifier.weight(1f)
            )
        }

        // Beverage Breakdown
        val beverageGroups = currentWater.groupBy { it.title.ifBlank { "Water" } }
        if (beverageGroups.isNotEmpty()) {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Beverage Composition", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    beverageGroups.forEach { (bev, entries) ->
                        val sum = entries.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
                        val pct = if (currentTotal > 0) ((sum.toFloat() / currentTotal.toFloat()) * 100).toInt() else 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(bev, color = TextWhite, fontSize = 12.sp)
                            Text("${String.format(Locale.US, "%.1f", sum / 1000f)} L ($pct%)", color = CyanBrand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricStatCard(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Text(value, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
