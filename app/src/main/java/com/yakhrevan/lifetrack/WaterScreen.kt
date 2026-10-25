package com.yakhrevan.lifetrack

import android.app.TimePickerDialog
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
private val AmberWarning   = Color(0xFFF59E0B)
private val CoralAccent    = Color(0xFFF43F5E)

// ==========================================
// 2. MAIN COMPOSABLE: WaterScreen
// ==========================================
@Composable
fun WaterScreen(
    logs: List<LogEntry>,
    today: String,
    onNavigate: (String) -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onWaterLogged: (amountMl: Int, drinkType: String, note: String) -> Unit = { _, _, _ -> },
    onDeleteLogEntry: (entryId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Local refresh trigger for instant reactive updates across database changes
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Loaded settings and entries from single source of truth
    val settings = remember(refreshTrigger) { WaterRepository.loadSettings(context) }
    val entries = remember(refreshTrigger, logs) { WaterRepository.loadEntries(context, logs) }

    // Date viewing state (allows user to navigate between Today and past days)
    var selectedDate by remember { mutableStateOf(today) }
    val isViewingToday = selectedDate == today
    val displayedEntries = remember(entries, selectedDate) { entries.filter { it.date == selectedDate } }

    // Comprehensive analytics derived strictly from real records
    val analytics = remember(entries, logs, settings.dailyGoalMl, refreshTrigger) {
        WaterRepository.calculateAnalytics(entries, logs, settings.dailyGoalMl)
    }

    // Recommendation calculation from user profile and settings
    val profilePrefs = remember { context.getSharedPreferences("lifetrack_profile", Context.MODE_PRIVATE) }
    // Weather is shared with Home; Water never maintains a separate manually-entered city.
    val sharedWeatherCity = profilePrefs.getString("weather_city", null)?.trim().orEmpty()
    val sharedWeatherLabel = profilePrefs.getString("weather_city_label", null).orEmpty()
    val sharedWeatherTemperature = remember(sharedWeatherLabel) {
        Regex("""(-?\d+(?:\.\d+)?)°C""").find(sharedWeatherLabel)
            ?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
            ?.takeIf { it in -10..55 }
    }
    val userWeight = remember {
        profilePrefs.getInt("profile_weight_kg", 0).takeIf { it in 30..250 }
            ?: profilePrefs.getString("weight", null)?.toFloatOrNull()?.toInt()?.takeIf { it in 30..250 }
            ?: 70
    }
    val userAge = remember {
        profilePrefs.getInt("profile_age", 0).takeIf { it in 10..120 }
            ?: profilePrefs.getString("age", null)?.toIntOrNull()?.takeIf { it in 10..120 }
            ?: 28
    }

    val recommendation = remember(userWeight, userAge, sharedWeatherTemperature, settings.isMedicalRestriction, settings.dailyGoalMl, refreshTrigger) {
        WaterRepository.calculateRecommendation(
            weightKg = userWeight,
            age = userAge,
            isMale = true,
            activityLevel = settings.activityLevel,
            climate = sharedWeatherTemperature?.let { temp ->
                if (temp >= 30) "Hot" else if (temp >= 24) "Warm" else "Moderate"
            } ?: "Moderate",
            isPregnantOrNursing = settings.isPregnantOrNursing,
            isMedicalRestriction = settings.isMedicalRestriction,
            prescribedTargetMl = settings.dailyGoalMl
        )
    }

    // Reminders state
    val (nextReminderTime, nextReminderSubtitle) = remember(settings, refreshTrigger) {
        WaterRepository.calculateNextReminderTime(settings)
    }

    // Selected Analytics Filter Tab (Today, Week, Month, Year)
    var selectedAnalyticsTab by remember { mutableStateOf("Week") }
    val periodAnalytics = remember(selectedAnalyticsTab, entries, logs, settings.dailyGoalMl, refreshTrigger) {
        WaterRepository.calculatePeriodAnalytics(selectedAnalyticsTab, entries, logs, settings.dailyGoalMl)
    }

    // Dialogs state
    var showEditGoalDialog by remember { mutableStateOf(false) }
    var showSmartRecommendationDialog by remember { mutableStateOf(false) }
    var showCustomDrinkDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showAllDrinkLog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<WaterEntry?>(null) }
    var deletingEntry by remember { mutableStateOf<WaterEntry?>(null) }

    // Goal and progress values
    val goalMl = settings.dailyGoalMl
    val loggedMl = if (isViewingToday) analytics.todayTotalMl else displayedEntries.sumOf { it.amountMl }
    val remainingMl = (goalMl - loggedMl).coerceAtLeast(0)
    val progressFraction = (loggedMl.toFloat() / goalMl.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val progressPercent = (progressFraction * 100).toInt()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Header with LifeTrack branding, Weather chip, Calendar, and Settings
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Life",
                                    color = TextWhite,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Track",
                                    color = PurpleLight,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                "Water · Stay hydrated • Be healthy",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Weather Chip (e.g. 28°C Chennai)
                            if (settings.weatherEnabled) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(CardInner)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.WbSunny,
                                        contentDescription = "Weather",
                                        tint = AmberWarning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            sharedWeatherTemperature?.let { "${it}°C" } ?: "Unavailable",
                                            color = TextWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            sharedWeatherCity.ifBlank { "Home weather" },
                                            color = TextMuted,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            // Calendar Date Picker Shortcut
                            IconButton(
                                onClick = { showDatePickerDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(CardInner, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Select Date", tint = TextWhite, modifier = Modifier.size(18.dp))
                            }

                            // Settings Shortcut
                            IconButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(CardInner, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Water Settings", tint = TextWhite, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Historical Date Notice (if viewing past day)
                if (!isViewingToday) {
                    item {
                        Surface(
                            color = CardInner,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BlueAccent.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Viewing records for: $selectedDate",
                                    color = BlueAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Return to Today",
                                    color = PurpleLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { selectedDate = today }
                                )
                            }
                        }
                    }
                }

                // 2. Today's Progress Card (Matching Reference Design)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Circular Progress Ring
                                Box(
                                    modifier = Modifier.size(116.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeWidth = 9.dp.toPx()
                                        drawCircle(
                                            color = Color(0xFF141F33),
                                            style = Stroke(strokeWidth)
                                        )
                                        drawArc(
                                            brush = Brush.sweepGradient(
                                                listOf(PurplePrimary, CyanBrand, BlueAccent, PurpleLight)
                                            ),
                                            startAngle = -90f,
                                            sweepAngle = progressFraction * 360f,
                                            useCenter = false,
                                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.WaterDrop,
                                            contentDescription = null,
                                            tint = BlueAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            settings.unit.format(loggedMl),
                                            color = TextWhite,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "of ${settings.unit.format(goalMl)}",
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            "$progressPercent%",
                                            color = PurpleLight,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Right Side: Daily Goal, Remaining, Edit Button
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.TrackChanges, contentDescription = null, tint = PurpleLight, modifier = Modifier.size(16.dp))
                                            Text("Daily Goal", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }

                                        // Edit Button
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CardInner)
                                                .clickable { showEditGoalDialog = true }
                                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Edit", color = PurpleLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Text(
                                        settings.unit.format(goalMl),
                                        color = TextWhite,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )

                                    Text(
                                        if (remainingMl > 0) "${settings.unit.format(remainingMl)} remaining" else "Daily goal reached! 🎉",
                                        color = if (remainingMl > 0) TextMuted else EmeraldAccent,
                                        fontSize = 11.sp,
                                        fontWeight = if (remainingMl > 0) FontWeight.Normal else FontWeight.SemiBold
                                    )

                                    // Linear Progress Bar
                                    LinearProgressIndicator(
                                        progress = progressFraction,
                                        color = CyanBrand,
                                        trackColor = Color(0xFF1E293B),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                }
                            }

                            // Smart Goal Suggestion Chip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardInner)
                                    .clickable { showSmartRecommendationDialog = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (settings.isMedicalRestriction) Icons.Default.Healing else Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = if (settings.isMedicalRestriction) CoralAccent else AmberWarning,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        if (settings.isMedicalRestriction) {
                                            "Prescribed Fluid Restriction: ${settings.unit.format(goalMl)}"
                                        } else {
                                            "Suggested for you: ${settings.unit.format(recommendation.suggestedMinMl)} – ${settings.unit.format(recommendation.suggestedMaxMl)}"
                                        },
                                        color = TextWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(Icons.Default.Info, contentDescription = "Info", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // 3. Quick Add Water Row (150, 250, 350, 500 mL, + Custom)
                item {
                    Text("Quick Add Water", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(settings.quickAddPresets) { amount ->
                            Box(
                                modifier = Modifier
                                    .width(70.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CardBg)
                                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                                    .clickable {
                                        WaterRepository.addEntry(context, amount, DrinkType.WATER, selectedDate)
                                        onWaterLogged(amount, "Water", "")
                                        refreshTrigger++
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocalDrink,
                                        contentDescription = null,
                                        tint = CyanBrand,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        "$amount",
                                        color = TextWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        settings.unit.label,
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        // + Custom Button
                        item {
                            Box(
                                modifier = Modifier
                                    .width(70.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.verticalGradient(listOf(PurplePrimary, VioletBrand))
                                    )
                                    .clickable { showCustomDrinkDialog = true }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        "Custom",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Amount",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Today's Drink Log Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (isViewingToday) "Today's Drink Log" else "$selectedDate Log",
                                    color = TextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (displayedEntries.size > 3) {
                                    Text(
                                        if (showAllDrinkLog) "Collapse" else "See All (${displayedEntries.size}) >",
                                        color = PurpleLight,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { showAllDrinkLog = !showAllDrinkLog }
                                    )
                                }
                            }

                            if (displayedEntries.isEmpty()) {
                                Text(
                                    if (isViewingToday) "No drinks recorded yet today. Tap a quick add cup above to log your intake."
                                    else "No drinks recorded on $selectedDate.",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                val displayList = if (showAllDrinkLog) displayedEntries else displayedEntries.take(3)
                                displayList.forEach { entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CardInner)
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color(entry.drinkType.colorHex).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                getDrinkIcon(entry.drinkType),
                                                contentDescription = null,
                                                tint = Color(entry.drinkType.colorHex),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                entry.time,
                                                color = TextWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                if (entry.note.isNotBlank()) "${entry.drinkType.label} · ${entry.note}" else entry.drinkType.label,
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }

                                        Text(
                                            settings.unit.format(entry.amountMl),
                                            color = TextWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        IconButton(
                                            onClick = { editingEntry = entry },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextMuted, modifier = Modifier.size(15.dp))
                                        }

                                        IconButton(
                                            onClick = { deletingEntry = entry },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CoralAccent, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. History & Analytics Card (Matching Reference Design)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Filter Tabs: Today, Week, Month, Year
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardInner, RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("Today", "Week", "Month", "Year").forEach { tab ->
                                    val isSel = tab.equals(selectedAnalyticsTab, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) PurplePrimary else Color.Transparent)
                                            .clickable { selectedAnalyticsTab = tab }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            tab,
                                            color = if (isSel) Color.White else TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Chart & Stat Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic Bar Chart
                                Column(modifier = Modifier.weight(1f)) {
                                    val maxL = maxOf(periodAnalytics.chartPoints.maxOfOrNull { it.second } ?: 1f, 3.0f)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        periodAnalytics.chartPoints.forEach { (label, litres) ->
                                            val fraction = if (litres > 0f) (litres / maxL).coerceIn(0.04f, 1f) else 0.025f
                                            val isAboveZero = litres > 0.05f
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.weight(1f).fillMaxHeight()
                                            ) {
                                                Box(
                                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                                    contentAlignment = Alignment.BottomCenter
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(if (periodAnalytics.chartPoints.size > 8) 8.dp else 14.dp)
                                                            .fillMaxHeight(fraction)
                                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                            .background(
                                                                if (isAboveZero) {
                                                                    Brush.verticalGradient(listOf(PurplePrimary, CyanBrand))
                                                                } else {
                                                                    Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF141F33)))
                                                                }
                                                            )
                                                    )
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(label, color = TextMuted, fontSize = if (periodAnalytics.chartPoints.size > 8) 7.sp else 9.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }

                                // Stat Card (e.g. This Week / 12.8 L / ↑ 12% vs last week)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(CardInner)
                                        .padding(14.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(periodAnalytics.periodLabel, color = TextMuted, fontSize = 11.sp)
                                        Text(
                                            "${String.format(Locale.US, "%.1f", periodAnalytics.totalMl / 1000f)} L",
                                            color = TextWhite,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val isPositive = periodAnalytics.percentageVsPrevious >= 0
                                            Icon(
                                                if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = if (isPositive) EmeraldAccent else CoralAccent,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                "${Math.abs(periodAnalytics.percentageVsPrevious)}% ${periodAnalytics.comparisonLabel}",
                                                color = if (isPositive) EmeraldAccent else CoralAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Smart Reminders & Reminder Settings (2 Cards Side by Side)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Card: Next Reminder
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, tint = PurpleLight, modifier = Modifier.size(16.dp))
                                    Text("Next Reminder", color = TextMuted, fontSize = 11.sp)
                                }

                                Text(
                                    nextReminderTime,
                                    color = TextWhite,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    nextReminderSubtitle,
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )

                                // Snooze Button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardInner)
                                        .clickable {
                                            snoozeWaterReminder(context, settings.snoozeMinutes)
                                            refreshTrigger++
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = PurpleLight, modifier = Modifier.size(12.dp))
                                        Text("Snooze (${settings.snoozeMinutes}m)", color = PurpleLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Right Card: Reminder Toggle & Schedule
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.LocalDrink, contentDescription = null, tint = CyanBrand, modifier = Modifier.size(16.dp))
                                        Text("Reminders", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Switch(
                                        checked = settings.remindersEnabled,
                                        onCheckedChange = { isEnabled ->
                                            WaterRepository.saveSettings(context, settings.copy(remindersEnabled = isEnabled))
                                            refreshTrigger++
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = CyanBrand,
                                            uncheckedTrackColor = Color(0xFF1E293B)
                                        ),
                                        modifier = Modifier.size(width = 36.dp, height = 20.dp)
                                    )
                                }

                                Text(
                                    "${formatHour(settings.activeStartHour)} – ${formatHour(settings.activeEndHour)}",
                                    color = TextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    if (settings.reminderIntervalMinutes >= 60) "Every ${settings.reminderIntervalMinutes / 60} hours" else "Every ${settings.reminderIntervalMinutes} minutes",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // 7. Quick Insights (4 Metric Cards matching visual reference)
                item {
                    Text("Insights", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InsightCard(
                            icon = Icons.Default.WaterDrop,
                            iconTint = BlueAccent,
                            value = "${String.format(Locale.US, "%.1f", analytics.dailyAverageMl / 1000f)} L",
                            label = "Daily Avg",
                            modifier = Modifier.weight(1f)
                        )
                        InsightCard(
                            icon = Icons.Default.TrackChanges,
                            iconTint = PurpleLight,
                            value = "${analytics.goalDaysPercent}%",
                            label = "Goal Days",
                            modifier = Modifier.weight(1f)
                        )
                        InsightCard(
                            icon = Icons.Default.LocalFireDepartment,
                            iconTint = AmberWarning,
                            value = "${analytics.currentStreakDays} days",
                            label = "Current Streak",
                            modifier = Modifier.weight(1f)
                        )
                        InsightCard(
                            icon = Icons.Default.BarChart,
                            iconTint = CyanBrand,
                            value = "${analytics.thisMonthTotalL} L",
                            label = "This Month",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Bottom Navigation Bar matching Visual Reference
            WaterBottomNavigationBar(
                onNavigateHome = onNavigateHome,
                onNavigateWater = { /* Already on water */ },
                onQuickAdd = { showCustomDrinkDialog = true },
                onNavigateInsights = { onNavigate("Insights") },
                onNavigateMore = { onNavigate("More") }
            )
        }
    }

    // ==========================================
    // 3. DIALOGS & MODALS
    // ==========================================

    // Edit Goal Dialog (With Clinical Restriction Support)
    if (showEditGoalDialog) {
        var goalInput by remember { mutableStateOf(goalMl.toString()) }
        var isMedicalRestriction by remember { mutableStateOf(settings.isMedicalRestriction) }
        var restrictionReason by remember { mutableStateOf(settings.medicalRestrictionReason) }

        AlertDialog(
            onDismissRequest = { showEditGoalDialog = false },
            title = { Text("Daily Hydration Target", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter target in mL or choose a standard volume:", color = TextMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it.filter(Char::isDigit) },
                        label = { Text("Daily Target (mL)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1800, 2200, 2500, 3000).forEach { pMl ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardInner)
                                    .clickable { goalInput = pMl.toString() }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${pMl / 1000f}L", color = PurpleLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Divider(color = CardBorder)

                    // Medical Fluid Restriction Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clinician-Prescribed Restriction", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Lock target & disable extra fluid suggestions", color = TextMuted, fontSize = 10.sp)
                        }
                        Switch(
                            checked = isMedicalRestriction,
                            onCheckedChange = { isMedicalRestriction = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CoralAccent
                            )
                        )
                    }

                    if (isMedicalRestriction) {
                        OutlinedTextField(
                            value = restrictionReason,
                            onValueChange = { restrictionReason = it },
                            label = { Text("Clinical Reason (e.g. Renal, Cardiac)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                                focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = goalInput.toIntOrNull()?.coerceIn(500, 10000) ?: 2500
                        WaterRepository.saveSettings(
                            context,
                            settings.copy(
                                dailyGoalMl = parsed,
                                isMedicalRestriction = isMedicalRestriction,
                                medicalRestrictionReason = restrictionReason
                            )
                        )
                        refreshTrigger++
                        showEditGoalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Save Target")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditGoalDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBg
        )
    }

    // Smart Goal Recommendation Modal
    if (showSmartRecommendationDialog) {
        AlertDialog(
            onDismissRequest = { showSmartRecommendationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (recommendation.isMedicalRestricted) Icons.Default.Healing else Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = if (recommendation.isMedicalRestricted) CoralAccent else AmberWarning
                    )
                    Text("Hydration Guidance", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (recommendation.isMedicalRestricted) {
                            "Prescribed Fluid Target: ${settings.unit.format(recommendation.suggestedMinMl)}"
                        } else {
                            "Estimated Guideline Range: ${settings.unit.format(recommendation.suggestedMinMl)} – ${settings.unit.format(recommendation.suggestedMaxMl)}"
                        },
                        color = CyanBrand,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        recommendation.explanation,
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    Divider(color = CardBorder)

                    Text("Factors Evaluated:", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    recommendation.factors.forEach { factor ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(5.dp).background(PurpleLight, CircleShape))
                            Text(factor, color = TextMuted, fontSize = 11.sp)
                        }
                    }

                    Text(
                        "LifeTrack recommendations are educational estimates based on dietary guidelines. Never exceed clinician-prescribed limits if treating medical conditions.",
                        color = AmberWarning.copy(alpha = 0.85f),
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mid = (recommendation.suggestedMinMl + recommendation.suggestedMaxMl) / 2
                        WaterRepository.saveSettings(context, settings.copy(dailyGoalMl = mid))
                        refreshTrigger++
                        showSmartRecommendationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Apply Suggestion")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmartRecommendationDialog = false }) { Text("Close", color = TextMuted) }
            },
            containerColor = CardBg
        )
    }

    // Custom Drink Logging Dialog (Supports Beverages, Custom Time, and Past Dates)
    if (showCustomDrinkDialog) {
        var customAmount by remember { mutableStateOf("250") }
        var selectedDrink by remember { mutableStateOf(DrinkType.WATER) }
        var noteInput by remember { mutableStateOf("") }
        var logDate by remember { mutableStateOf(selectedDate) }

        AlertDialog(
            onDismissRequest = { showCustomDrinkDialog = false },
            title = { Text("Log Beverage", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Beverage Types Horizontal Scroll
                    Text("Drink Type", color = TextMuted, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(DrinkType.values()) { dType ->
                            val isSel = dType == selectedDrink
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Color(dType.colorHex).copy(alpha = 0.35f) else CardInner)
                                    .border(1.dp, if (isSel) Color(dType.colorHex) else CardBorder, RoundedCornerShape(10.dp))
                                    .clickable { selectedDrink = dType }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(getDrinkIcon(dType), null, tint = Color(dType.colorHex), modifier = Modifier.size(14.dp))
                                    Text(dType.label, color = if (isSel) TextWhite else TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Beverage Hydration factor explanation
                    if (!selectedDrink.isPlainWater) {
                        Text(
                            "${selectedDrink.label} provides ~${(selectedDrink.hydrationFactor * 100).toInt()}% net hydration.",
                            color = CyanBrand,
                            fontSize = 10.sp
                        )
                    }

                    OutlinedTextField(
                        value = customAmount,
                        onValueChange = { customAmount = it.filter(Char::isDigit) },
                        label = { Text("Volume (${settings.unit.label})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Optional Note (e.g. Post-workout, Herbal)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = customAmount.toIntOrNull()?.coerceIn(10, 5000) ?: 250
                        WaterRepository.addEntry(context, amount, selectedDrink, logDate, note = noteInput)
                        onWaterLogged(amount, selectedDrink.label, noteInput)
                        refreshTrigger++
                        showCustomDrinkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Add Drink")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDrinkDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBg
        )
    }

    // Edit Drink Entry Dialog
    if (editingEntry != null) {
        val entry = editingEntry!!
        var editAmount by remember { mutableStateOf(entry.amountMl.toString()) }
        var editDrink by remember { mutableStateOf(entry.drinkType) }
        var editNote by remember { mutableStateOf(entry.note) }

        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text("Edit Entry", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it.filter(Char::isDigit) },
                        label = { Text("Volume (mL)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = { Text("Note") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = editAmount.toIntOrNull()?.coerceIn(10, 5000) ?: entry.amountMl
                        WaterRepository.updateEntry(context, entry.copy(amountMl = amt, drinkType = editDrink, note = editNote))
                        refreshTrigger++
                        editingEntry = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBg
        )
    }

    // Delete Drink Confirmation Dialog
    if (deletingEntry != null) {
        val entry = deletingEntry!!
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text("Delete Entry?", color = TextWhite) },
            text = { Text("Remove ${entry.amountMl} mL (${entry.drinkType.label}) recorded at ${entry.time}?", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        WaterRepository.deleteEntry(context, entry.id)
                        onDeleteLogEntry(entry.id)
                        refreshTrigger++
                        deletingEntry = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralAccent)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBg
        )
    }

    // Date Picker Selector Dialog
    if (showDatePickerDialog) {
        AlertDialog(
            onDismissRequest = { showDatePickerDialog = false },
            title = { Text("Browse Records by Date", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select a date to view past drinks or add retroactive records:", color = TextMuted, fontSize = 12.sp)

                    val recentDates = (0..6).map { LocalDate.now().minusDays(it.toLong()).toString() }
                    recentDates.forEach { d ->
                        val isSel = d == selectedDate
                        val dTotal = WaterRepository.getDayTotal(entries, logs, d)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) PurplePrimary.copy(alpha = 0.25f) else CardInner)
                                .border(1.dp, if (isSel) PurplePrimary else CardBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedDate = d
                                    showDatePickerDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (d == today) "Today ($d)" else d, color = if (isSel) TextWhite else TextMuted, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            Text("${dTotal} mL", color = CyanBrand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDatePickerDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Close")
                }
            },
            containerColor = CardBg
        )
    }

    // Comprehensive Water Settings Dialog
    if (showSettingsDialog) {
        var reminderInterval by remember { mutableIntStateOf(settings.reminderIntervalMinutes) }
        var customIntervalInput by remember { mutableStateOf(settings.reminderIntervalMinutes.toString()) }
        var remindersOn by remember { mutableStateOf(settings.remindersEnabled) }
        var quietHoursOn by remember { mutableStateOf(settings.quietHoursEnabled) }
        var snoozeDuration by remember { mutableIntStateOf(settings.snoozeMinutes) }
        var activeStartHour by remember { mutableIntStateOf(settings.activeStartHour) }
        var activeEndHour by remember { mutableIntStateOf(settings.activeEndHour) }
        var unitSel by remember { mutableStateOf(settings.unit) }
        var actLevel by remember { mutableStateOf(settings.activityLevel) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Water Settings", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Preferred Unit
                    Text("Preferred Measurement Unit", color = TextMuted, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WaterUnit.values().forEach { u ->
                            val isSel = u == unitSel
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) PurplePrimary else CardInner)
                                    .clickable { unitSel = u }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(u.label, color = if (isSel) Color.White else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Divider(color = CardBorder)

                    // Reminders toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hydration Reminders", color = TextWhite, fontSize = 13.sp)
                        Switch(
                            checked = remindersOn,
                            onCheckedChange = { remindersOn = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PurplePrimary
                            )
                        )
                    }

                    if (remindersOn) {
                        Text("Reminder Window", color = TextMuted, fontSize = 11.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, hour, _ -> activeStartHour = hour },
                                        activeStartHour,
                                        0,
                                        false
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Start · ${formatHour(activeStartHour)}", fontSize = 11.sp) }
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, hour, _ -> activeEndHour = hour },
                                        activeEndHour,
                                        0,
                                        false
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("End · ${formatHour(activeEndHour)}", fontSize = 11.sp) }
                        }
                        Text("Reminder Interval", color = TextMuted, fontSize = 11.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(45, 60, 90, 120, 180).forEach { mins ->
                                val isSel = mins == reminderInterval
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) CyanBrand else CardInner)
                                        .clickable {
                                            reminderInterval = mins
                                            customIntervalInput = mins.toString()
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (mins < 60) "${mins}m" else "${mins / 60}h",
                                        color = if (isSel) Color.White else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customIntervalInput,
                            onValueChange = { input ->
                                customIntervalInput = input.filter(Char::isDigit).take(3)
                                customIntervalInput.toIntOrNull()?.takeIf { it in 15..360 }?.let { reminderInterval = it }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Custom interval (15–360 minutes)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = CardBorder,
                                focusedContainerColor = CardInner,
                                unfocusedContainerColor = CardInner
                            )
                        )

                        Text("Snooze Duration", color = TextMuted, fontSize = 11.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                val isSel = mins == snoozeDuration
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) PurpleLight else CardInner)
                                        .clickable { snoozeDuration = mins }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${mins}m",
                                        color = if (isSel) Color.White else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = CardBorder)

                    // Quiet hours toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quiet hours outside reminder window", color = TextWhite, fontSize = 13.sp)
                        Switch(
                            checked = quietHoursOn,
                            onCheckedChange = { quietHoursOn = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyanBrand
                            )
                        )
                    }

                    Divider(color = CardBorder)

                    // Activity Level Selector
                    Text("Daily Activity Level", color = TextMuted, fontSize = 11.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Sedentary", "Moderate", "Active").forEach { act ->
                            val isSel = act.equals(actLevel, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) EmeraldAccent else CardInner)
                                    .clickable { actLevel = act }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    act,
                                    color = if (isSel) Color.White else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = activeStartHour < activeEndHour &&
                        (!remindersOn || customIntervalInput.toIntOrNull()?.let { it in 15..360 } == true),
                    onClick = {
                        WaterRepository.saveSettings(
                            context,
                            settings.copy(
                                unit = unitSel,
                                remindersEnabled = remindersOn,
                                quietHoursEnabled = quietHoursOn,
                                reminderIntervalMinutes = reminderInterval,
                                snoozeMinutes = snoozeDuration,
                                activeStartHour = activeStartHour,
                                activeEndHour = activeEndHour,
                                activityLevel = actLevel
                            )
                        )
                        refreshTrigger++
                        showSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBg
        )
    }


}

// ==========================================
// 4. BOTTOM NAVIGATION BAR
// ==========================================
@Composable
private fun WaterBottomNavigationBar(
    onNavigateHome: () -> Unit,
    onNavigateWater: () -> Unit,
    onQuickAdd: () -> Unit,
    onNavigateInsights: () -> Unit,
    onNavigateMore: () -> Unit
) {
    Surface(
        color = Color(0xFF080C16),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home
            BottomNavCol(icon = Icons.Default.Home, label = "Home", selected = false, onClick = onNavigateHome)

            // Water (Active)
            BottomNavCol(icon = Icons.Default.WaterDrop, label = "Water", selected = true, onClick = onNavigateWater)

            // Center FAB (+)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFA855F7))
                        )
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable(onClick = onQuickAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Quick Add Drink",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Insights
            BottomNavCol(icon = Icons.Default.BarChart, label = "Insights", selected = false, onClick = onNavigateInsights)

            // More
            BottomNavCol(icon = Icons.Default.GridView, label = "More", selected = false, onClick = onNavigateMore)
        }
    }
}

@Composable
private fun BottomNavCol(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (selected) Color(0xFF2E1A47) else Color.Transparent,
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) PurpleLight else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            label,
            color = if (selected) PurpleLight else TextMuted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ==========================================
// 5. HELPER COMPOSABLES & FORMATTERS
// ==========================================

@Composable
private fun InsightCard(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Text(value, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

private fun formatHour(hour: Int): String {
    val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12) "AM" else "PM"
    return "$h:00 $amPm"
}

private fun getDrinkIcon(type: DrinkType): ImageVector {
    return when (type) {
        DrinkType.TEA -> Icons.Default.EmojiFoodBeverage
        DrinkType.COFFEE -> Icons.Default.LocalCafe
        DrinkType.JUICE -> Icons.Default.LocalBar
        DrinkType.MILK -> Icons.Default.Icecream
        DrinkType.SODA -> Icons.Default.LocalDrink
        DrinkType.SPORTS -> Icons.Default.FitnessCenter
        DrinkType.ELECTROLYTES -> Icons.Default.ElectricBolt
        DrinkType.HERBAL_TEA -> Icons.Default.Spa
        DrinkType.WATER -> Icons.Default.WaterDrop
    }
}
