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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID

// ==========================================
// 1. DESIGN TOKENS & PALETTE
// ==========================================
private val DarkBg         = Color(0xFF070B14)
private val CardBg         = Color(0xFF0E1626)
private val CardInner      = Color(0xFF141F33)
private val CardBorder     = Color(0x1AFFFFFF)
private val TextWhite      = Color(0xFFF1F5F9)
private val TextMuted      = Color(0xFF8896AB)
private val PurplePrimary  = Color(0xFFA855F7)
private val VioletTab      = Color(0xFF6366F1)
private val EmeraldHabit   = Color(0xFF10B981)
private val CyanRoutine    = Color(0xFF06B6D4)
private val BlueStudy      = Color(0xFF3B82F6)
private val CoralEvent     = Color(0xFFF43F5E)
private val AmberWarning   = Color(0xFFF59E0B)

// ==========================================
// 2. DATA MODELS & ENUMS
// ==========================================
enum class CalendarViewMode(val label: String) {
    MONTH("Month"),
    WEEK("Week"),
    DAY("Day"),
    AGENDA("Agenda")
}

enum class ActivityType(val label: String, val color: Color, val icon: ImageVector) {
    TASK("Tasks", PurplePrimary, Icons.Default.CheckCircle),
    HABIT("Habits", EmeraldHabit, Icons.Default.Spa),
    ROUTINE("Routines", CyanRoutine, Icons.Default.Schedule),
    STUDY("Study", BlueStudy, Icons.Default.MenuBook),
    EVENT("Events", CoralEvent, Icons.Default.Event)
}

enum class RepeatOption(val label: String) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    CUSTOM("Custom...")
}

data class CalendarItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val type: ActivityType,
    val date: String, // "YYYY-MM-DD"
    val startTime: String = "09:00", // "09:00 AM" or "09:00"
    val endTime: String = "10:00",
    val isAllDay: Boolean = false,
    val location: String = "",
    val reminder: String = "10 minutes before",
    val repeatOption: RepeatOption = RepeatOption.NONE,
    val isCompleted: Boolean = false,
    val countOrDuration: String = ""
)

// Persistence helper for Calendar activities
object CalendarStorage {
    private const val PREFS = "lifetrack_calendar"
    private const val KEY_ACTIVITIES = "calendar_activities"

    fun load(context: Context): List<CalendarItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ACTIVITIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<CalendarItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val title = obj.optString("title", "").trim()
                val date = obj.optString("date", "")
                val type = runCatching { ActivityType.valueOf(obj.optString("type", "")) }.getOrNull()
                if (title.isBlank() || runCatching { LocalDate.parse(date) }.isFailure || type == null) {
                    continue
                }
                val repeat = runCatching { RepeatOption.valueOf(obj.optString("repeatOption", RepeatOption.NONE.name)) }
                    .getOrDefault(RepeatOption.NONE)
                list.add(
                    CalendarItem(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        title = title,
                        description = obj.optString("description", ""),
                        type = type,
                        date = date,
                        startTime = obj.optString("startTime", ""),
                        endTime = obj.optString("endTime", ""),
                        isAllDay = obj.optBoolean("isAllDay", false),
                        location = obj.optString("location", ""),
                        reminder = obj.optString("reminder", ""),
                        repeatOption = repeat,
                        isCompleted = obj.optBoolean("isCompleted", false),
                        countOrDuration = obj.optString("countOrDuration", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, items: List<CalendarItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("description", item.description)
                put("type", item.type.name)
                put("date", item.date)
                put("startTime", item.startTime)
                put("endTime", item.endTime)
                put("isAllDay", item.isAllDay)
                put("location", item.location)
                put("reminder", item.reminder)
                put("repeatOption", item.repeatOption.name)
                put("isCompleted", item.isCompleted)
                put("countOrDuration", item.countOrDuration)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVITIES, arr.toString())
            .apply()
    }

    /**
     * Dynamically combines user calendar items with user-scheduled habits and routines
     * from HabitRoutineRepository, avoiding any hardcoded or fake seeds.
     */
    fun getDynamicScheduledOccurrences(
        context: Context,
        start: LocalDate,
        end: LocalDate,
        logs: List<LogEntry> = emptyList()
    ): List<CalendarItem> {
        val result = mutableListOf<CalendarItem>()
        // Read each repository once. These helpers decode their full SharedPreferences
        // JSON payload, so calling them inside the day loop caused repeated disk/JSON
        // work and could freeze the Compose UI while the More > Calendar transition ran.
        val habits = HabitRoutineRepository.getHabits(context)
        val routines = HabitRoutineRepository.getRoutines(context)
        val completionsByKey = HabitRoutineRepository.getCompletions(context)
            .associateBy { "${it.habitId}_${it.date}" }
        val sessions = HabitRoutineRepository.getRoutineSessions(context)
        val completedRoutineKeys = sessions
            .filter { it.isCompleted }
            .mapTo(mutableSetOf()) { "${it.routineId}_${it.date}" }
        val tasks = TaskRepository.getTasks(context)

        var day = start
        while (!day.isAfter(end)) {
            val dateStr = day.toString()
            // Habits for this day
            habits.filter { HabitRoutineRepository.isHabitScheduled(it, day) }.forEach { habit ->
                val comp = completionsByKey["${habit.id}_${dateStr}"]
                result.add(
                    CalendarItem(
                        id = "habit_${habit.id}_$dateStr",
                        title = habit.name,
                        description = habit.description,
                        type = ActivityType.HABIT,
                        date = dateStr,
                        startTime = habit.reminders.firstOrNull { it.enabled }?.time.orEmpty(),
                        endTime = "",
                        isCompleted = comp?.status == "completed",
                        countOrDuration = if (habit.targetType == "Quantity") "${habit.targetAmount.toInt()} ${habit.unit}" else ""
                    )
                )
            }

            // Routines for this day
            routines.filter { HabitRoutineRepository.isRoutineScheduled(it, day) }.forEach { routine ->
                val isCompleted = "${routine.id}_${dateStr}" in completedRoutineKeys
                result.add(
                    CalendarItem(
                        id = "routine_${routine.id}_$dateStr",
                        title = routine.name,
                        description = routine.description,
                        type = ActivityType.ROUTINE,
                        date = dateStr,
                        startTime = routine.startTime,
                        endTime = "",
                        isCompleted = isCompleted,
                        countOrDuration = "${routine.steps.size} steps"
                    )
                )
            }

            // Tasks for this day
            val tasksForDay = tasks.filter { task ->
                val startDate = runCatching { LocalDate.parse(task.startDate ?: task.dueDate) }.getOrNull() ?: return@filter false
                if (day.isBefore(startDate)) return@filter false
                when (task.repeatType) {
                    "Daily" -> true
                    "Weekly" -> {
                        val weekdays = task.reminderDays
                        if (weekdays.isNotEmpty()) day.dayOfWeek.value in weekdays
                        else day.dayOfWeek == startDate.dayOfWeek
                    }
                    "Monthly" -> day.dayOfMonth == startDate.dayOfMonth
                    else -> task.dueDate == dateStr
                }
            }
            tasksForDay.forEach { task ->
                result.add(
                    CalendarItem(
                        id = "task_${task.id}_$dateStr",
                        title = task.title,
                        description = task.description,
                        type = ActivityType.TASK,
                        date = dateStr,
                        startTime = task.dueTime.orEmpty(),
                        endTime = "",
                        isCompleted = task.isCompleted,
                        countOrDuration = if (task.subtasks.isNotEmpty()) "${task.subtasks.count { it.isCompleted }}/${task.subtasks.size}" else ""
                    )
                )
            }

            // Study sessions come from saved logs, not fabricated calendar entries.
            logs.filter { it.category.equals("Study", ignoreCase = true) && it.date == dateStr }.forEach { log ->
                result.add(
                    CalendarItem(
                        id = "study_log_${log.date}_${log.title.hashCode()}_${log.amount.hashCode()}",
                        title = log.title.ifBlank { "Study session" },
                        description = log.note,
                        type = ActivityType.STUDY,
                        date = dateStr,
                        startTime = "",
                        endTime = "",
                        isAllDay = true,
                        isCompleted = true,
                        countOrDuration = log.amount.toIntOrNull()?.let { "${it} min" } ?: log.amount
                    )
                )
            }

            day = day.plusDays(1)
        }
        return result
    }
}

// ==========================================
// 3. MAIN COMPOSABLE: CalendarScreen
// ==========================================
@Composable
fun CalendarScreen(
    logs: List<LogEntry>,
    onAddEntry: (String) -> Unit = {},
    onEditEntry: (LogEntry) -> Unit = {},
    onDeleteEntry: (LogEntry) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onNavigateHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Activities list (loaded from SharedPreferences)
    var activities by remember { mutableStateOf(CalendarStorage.load(context)) }
    var calendarDataRevision by remember { mutableIntStateOf(0) }

    fun persistActivities(updated: List<CalendarItem>) {
        activities = updated
        CalendarStorage.save(context, updated)
    }

    // Calendar completion actions write to the source feature repository.
    fun toggleCalendarItem(item: CalendarItem) {
        val dateSuffix = "_${item.date}"
        when {
            item.id.startsWith("habit_") -> {
                val habitId = item.id.removePrefix("habit_").removeSuffix(dateSuffix)
                if (item.isCompleted) HabitRoutineRepository.undoHabitCompletion(context, habitId, item.date)
                else HabitRoutineRepository.markHabitComplete(context, habitId, item.date)
                calendarDataRevision += 1
            }
            item.id.startsWith("routine_") -> {
                val routineId = item.id.removePrefix("routine_").removeSuffix(dateSuffix)
                HabitRoutineRepository.setRoutineCompletion(context, routineId, item.date, !item.isCompleted)
                calendarDataRevision += 1
            }
            item.id.startsWith("task_") -> {
                val taskId = item.id.removePrefix("task_").removeSuffix(dateSuffix)
                TaskRepository.toggleTaskCompletion(context, taskId)
                calendarDataRevision += 1
            }
            item.id.startsWith("study_log_") -> Unit
            else -> {
                persistActivities(activities.map { existing ->
                    if (existing.id == item.id) existing.copy(isCompleted = !existing.isCompleted) else existing
                })
            }
        }
    }

    // View Mode: Month, Week, Day, Agenda
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }

    // Navigation date state
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }

    // Filter toggles (Screen 10)
    var filterTasks by remember { mutableStateOf(true) }
    var filterHabits by remember { mutableStateOf(true) }
    var filterRoutines by remember { mutableStateOf(true) }
    var filterStudy by remember { mutableStateOf(true) }
    var filterEvents by remember { mutableStateOf(true) }
    var showCompleted by remember { mutableStateOf(true) }

    // Modals & Bottom sheets
    var showQuickAddMenu by remember { mutableStateOf(false) }
    var showAddEventModal by remember { mutableStateOf(false) }
    var preselectedTypeForAdd by remember { mutableStateOf(ActivityType.EVENT) }
    var editingActivity by remember { mutableStateOf<CalendarItem?>(null) }
    var selectedActivityForDetails by remember { mutableStateOf<CalendarItem?>(null) }
    var showDateDetailsModal by remember { mutableStateOf(false) }
    var showFiltersModal by remember { mutableStateOf(false) }
    var showDailySummaryModal by remember { mutableStateOf(false) }

    // Dynamically retrieve user scheduled occurrences from HabitRoutineRepository
    val monthStart = currentYearMonth.atDay(1).minusDays(7)
    val monthEnd = currentYearMonth.atEndOfMonth().plusDays(7)
    // Build scheduled occurrences off the main thread so navigation and transition
    // animations remain responsive even when the user has extensive history.
    val dynamicOccurrences by produceState(
        initialValue = emptyList<CalendarItem>(),
        currentYearMonth,
        calendarDataRevision,
        logs
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CalendarStorage.getDynamicScheduledOccurrences(context, monthStart, monthEnd, logs)
        }
    }

    val combinedActivities = remember(activities, dynamicOccurrences) {
        (activities + dynamicOccurrences).distinctBy { it.id }
    }

    // Filtered activities
    val visibleActivities = remember(
        combinedActivities, filterTasks, filterHabits, filterRoutines,
        filterStudy, filterEvents, showCompleted
    ) {
        combinedActivities.filter { item ->
            val typeAllowed = when (item.type) {
                ActivityType.TASK -> filterTasks
                ActivityType.HABIT -> filterHabits
                ActivityType.ROUTINE -> filterRoutines
                ActivityType.STUDY -> filterStudy
                ActivityType.EVENT -> filterEvents
            }
            val completionAllowed = showCompleted || !item.isCompleted
            typeAllowed && completionAllowed
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Bar Header
            CalendarTopBar(
                viewMode = viewMode,
                onBack = onNavigateHome,
                onNotificationsClick = { onNavigate("Settings") },
                onCalendarClick = { showDailySummaryModal = true },
                onFilterClick = { showFiltersModal = true }
            )

            // 2. View Mode Switcher Pills: Month | Week | Day | Agenda
            CalendarViewSwitcher(
                selectedMode = viewMode,
                onSelectMode = { viewMode = it }
            )

            // 3. View Content
            Box(modifier = Modifier.weight(1f)) {
                when (viewMode) {
                    CalendarViewMode.MONTH -> {
                        MonthViewContent(
                            currentYearMonth = currentYearMonth,
                            selectedDate = selectedDate,
                            activities = visibleActivities,
                            onMonthChange = { currentYearMonth = it },
                            onSelectDate = { selectedDate = it },
                            onTodayClick = {
                                selectedDate = LocalDate.now()
                                currentYearMonth = YearMonth.now()
                            },
                            onToggleActivity = { item -> toggleCalendarItem(item) },
                            onActivityClick = { selectedActivityForDetails = it },
                            onViewAllClick = { showDateDetailsModal = true }
                        )
                    }
                    CalendarViewMode.WEEK -> {
                        WeekViewContent(
                            selectedDate = selectedDate,
                            activities = visibleActivities,
                            onSelectDate = { selectedDate = it },
                            onActivityClick = { selectedActivityForDetails = it }
                        )
                    }
                    CalendarViewMode.DAY -> {
                        DayViewContent(
                            selectedDate = selectedDate,
                            activities = visibleActivities,
                            onSelectDate = { selectedDate = it },
                            onToggleActivity = { item -> toggleCalendarItem(item) },
                            onActivityClick = { selectedActivityForDetails = it },
                            onEditDaySummary = { showDailySummaryModal = true }
                        )
                    }
                    CalendarViewMode.AGENDA -> {
                        AgendaViewContent(
                            selectedDate = selectedDate,
                            activities = visibleActivities,
                            onToggleActivity = { item -> toggleCalendarItem(item) },
                            onActivityClick = { selectedActivityForDetails = it }
                        )
                    }
                }
            }
        }

        // Floating Action Button (+) (Screens 2, 3, 4)
        FloatingActionButton(
            onClick = { showQuickAddMenu = true },
            containerColor = PurplePrimary,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 95.dp)
                .size(54.dp)
        ) {
            Icon(Icons.Default.Add, "Quick Add", modifier = Modifier.size(28.dp))
        }

        // ==========================================
        // OVERLAYS & MODALS
        // ==========================================

        // Screen 6: Quick Add Menu (Bottom Sheet)
        if (showQuickAddMenu) {
            QuickAddMenuSheet(
                onDismiss = { showQuickAddMenu = false },
                onSelectOption = { type ->
                    showQuickAddMenu = false
                    preselectedTypeForAdd = type
                    editingActivity = null
                    showAddEventModal = true
                }
            )
        }

        // Screen 7: Event Creation / Editing Modal
        if (showAddEventModal) {
            AddEditEventModal(
                initialType = preselectedTypeForAdd,
                existingItem = editingActivity,
                defaultDate = selectedDate,
                onDismiss = { showAddEventModal = false },
                onSave = { savedItem ->
                    val existingIndex = activities.indexOfFirst { it.id == savedItem.id }
                    val updated = if (existingIndex != -1) {
                        activities.toMutableList().apply { set(existingIndex, savedItem) }
                    } else {
                        listOf(savedItem) + activities
                    }
                    persistActivities(updated)
                    showAddEventModal = false
                }
            )
        }

        // Screen 9: Event Details Modal
        selectedActivityForDetails?.let { item ->
            EventDetailsModal(
                item = item,
                onDismiss = { selectedActivityForDetails = null },
                onEdit = {
                    selectedActivityForDetails = null
                    editingActivity = item
                    preselectedTypeForAdd = item.type
                    showAddEventModal = true
                },
                onDelete = {
                    persistActivities(activities.filterNot { it.id == item.id })
                    selectedActivityForDetails = null
                }
            )
        }

        // Screen 5: Date Details & Filter Sheet
        if (showDateDetailsModal) {
            DateDetailsModal(
                date = selectedDate,
                activities = combinedActivities.filter { it.date == selectedDate.toString() },
                onDismiss = { showDateDetailsModal = false },
                onToggleActivity = { item -> toggleCalendarItem(item) },
                onAddNewForDate = {
                    showDateDetailsModal = false
                    preselectedTypeForAdd = ActivityType.EVENT
                    editingActivity = null
                    showAddEventModal = true
                }
            )
        }

        // Screen 10: Calendar Filters Modal
        if (showFiltersModal) {
            CalendarFiltersModal(
                filterTasks = filterTasks,
                filterHabits = filterHabits,
                filterRoutines = filterRoutines,
                filterStudy = filterStudy,
                filterEvents = filterEvents,
                showCompleted = showCompleted,
                onDismiss = { showFiltersModal = false },
                onApply = { tasks, habits, routines, study, events, completed ->
                    filterTasks = tasks
                    filterHabits = habits
                    filterRoutines = routines
                    filterStudy = study
                    filterEvents = events
                    showCompleted = completed
                    showFiltersModal = false
                }
            )
        }

        // Screen 11: Daily Summary Modal
        if (showDailySummaryModal) {
            DailySummaryModal(
                date = selectedDate,
                activities = combinedActivities.filter { it.date == selectedDate.toString() },
                onDismiss = { showDailySummaryModal = false }
            )
        }
    }
}

// ==========================================
// 4. TOP BAR HEADER
// ==========================================
@Composable
private fun CalendarTopBar(
    viewMode: CalendarViewMode,
    onBack: () -> Unit,
    onNotificationsClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (viewMode == CalendarViewMode.MONTH) {
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
                        "Calendar",
                        color = PurplePrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        " · Plan · Schedule · Stay on track",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Calendar",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onFilterClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(CardInner, CircleShape)
            ) {
                Icon(Icons.Default.FilterList, "Filters", tint = TextWhite, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = onCalendarClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(CardInner, CircleShape)
            ) {
                Icon(Icons.Default.CalendarToday, "Daily Summary", tint = PurplePrimary, modifier = Modifier.size(17.dp))
            }
        }
    }
}

// ==========================================
// 5. VIEW MODE SWITCHER PILLS (Month | Week | Day | Agenda)
// ==========================================
@Composable
private fun CalendarViewSwitcher(
    selectedMode: CalendarViewMode,
    onSelectMode: (CalendarViewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(CardInner, RoundedCornerShape(14.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CalendarViewMode.values().forEach { mode ->
            val isSelected = selectedMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) VioletTab else Color.Transparent)
                    .clickable { onSelectMode(mode) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mode.label,
                    color = if (isSelected) Color.White else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ==========================================
// 6. MONTH VIEW CONTENT (SCREEN 1)
// ==========================================
@Composable
private fun MonthViewContent(
    currentYearMonth: YearMonth,
    selectedDate: LocalDate,
    activities: List<CalendarItem>,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onTodayClick: () -> Unit,
    onToggleActivity: (CalendarItem) -> Unit,
    onActivityClick: (CalendarItem) -> Unit,
    onViewAllClick: () -> Unit
) {
    val monthTitleFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US) }
    val today = remember { LocalDate.now() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // 1. Month Navigation Header (< October 2025 > [Today])
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onMonthChange(currentYearMonth.minusMonths(1)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, "Previous Month", tint = TextWhite)
                    }
                    Text(
                        currentYearMonth.format(monthTitleFormatter),
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    IconButton(
                        onClick = { onMonthChange(currentYearMonth.plusMonths(1)) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, "Next Month", tint = TextWhite)
                    }
                }

                Surface(
                    color = CardInner,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.clickable(onClick = onTodayClick)
                ) {
                    Text(
                        "Today",
                        color = CyanRoutine,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 2. Monthly Calendar Card (Days Header + 7x6 Grid)
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Mon - Sun Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { dayName ->
                            Text(
                                dayName,
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Compute grid dates
                    val firstDayOfMonth = currentYearMonth.atDay(1)
                    val daysInMonth = currentYearMonth.lengthOfMonth()
                    // 1 = Monday ... 7 = Sunday
                    val leadingOffset = firstDayOfMonth.dayOfWeek.value - 1
                    val prevMonth = currentYearMonth.minusMonths(1)
                    val daysInPrevMonth = prevMonth.lengthOfMonth()

                    val totalCells = ((leadingOffset + daysInMonth + 6) / 7) * 7

                    (0 until totalCells).chunked(7).forEach { week ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            week.forEach { cellIndex ->
                                val dayNum = cellIndex - leadingOffset + 1
                                val (cellDate, isCurrentMonth) = when {
                                    dayNum < 1 -> Pair(prevMonth.atDay(daysInPrevMonth + dayNum), false)
                                    dayNum > daysInMonth -> Pair(currentYearMonth.plusMonths(1).atDay(dayNum - daysInMonth), false)
                                    else -> Pair(currentYearMonth.atDay(dayNum), true)
                                }

                                val isSelected = cellDate == selectedDate
                                val isToday = cellDate == today
                                val dateStr = cellDate.toString()
                                val dayActivities = activities.filter { it.date == dateStr }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) PurplePrimary else Color.Transparent
                                        )
                                        .border(
                                            1.dp,
                                            if (!isSelected && isToday) CyanRoutine else Color.Transparent,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSelectDate(cellDate) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "${cellDate.dayOfMonth}",
                                        color = when {
                                            isSelected -> Color.White
                                            !isCurrentMonth -> TextMuted.copy(alpha = 0.35f)
                                            isToday -> CyanRoutine
                                            else -> TextWhite
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                                    )

                                    // Color-coded activity dots
                                    if (dayActivities.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            val distinctTypes = dayActivities.map { it.type }.distinct().take(4)
                                            distinctTypes.forEach { t ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(
                                                            if (isSelected) Color.White else t.color,
                                                            CircleShape
                                                        )
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(Modifier.height(8.dp))

                    // Activity Legend Pills (Tasks, Habits, Routines, Study, Events)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActivityType.values().forEach { t ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).background(t.color, CircleShape))
                                Text(t.label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // 3. Selected Date Agenda Section
        item {
            val selectedDateStr = selectedDate.toString()
            val selectedActivities = activities.filter { it.date == selectedDateStr }
            val prettyDate = if (selectedDate == today) {
                "Today, ${selectedDate.dayOfMonth} ${selectedDate.month.name.lowercase().replaceFirstChar { it.uppercase() }}"
            } else {
                "${selectedDate.dayOfMonth} ${selectedDate.month.name.lowercase().replaceFirstChar { it.uppercase() }}"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(prettyDate, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "View All >",
                        color = CyanRoutine,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onViewAllClick)
                    )
                }

                if (selectedActivities.isEmpty()) {
                    Surface(
                        color = CardInner,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                            Text("No activities scheduled for this date.", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    selectedActivities.forEach { item ->
                        CalendarScheduleRow(
                            item = item,
                            onToggle = { onToggleActivity(item) },
                            onClick = { onActivityClick(item) }
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------
// Calendar Schedule Item Row
// ------------------------------------------
@Composable
private fun CalendarScheduleRow(
    item: CalendarItem,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardInner)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Start Time
        Text(
            item.startTime,
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(55.dp)
        )

        // Type Icon Box
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(item.type.color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.type.icon, null, tint = item.type.color, modifier = Modifier.size(16.dp))
        }

        // Title & Location
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                color = if (item.isCompleted) TextMuted else TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.location.isNotBlank()) {
                Text(item.location, color = TextMuted, fontSize = 9.sp, maxLines = 1)
            }
        }

        // Duration or subtext badge
        if (item.countOrDuration.isNotBlank()) {
            Text(item.countOrDuration, color = TextMuted, fontSize = 10.sp)
        }

        // Completion checkbox
        IconButton(onClick = onToggle, enabled = item.type != ActivityType.STUDY, modifier = Modifier.size(24.dp)) {
            Icon(
                if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                "Toggle Completion",
                tint = if (item.isCompleted) EmeraldHabit else TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ==========================================
// 7. WEEK VIEW CONTENT (SCREEN 2)
// ==========================================
@Composable
private fun WeekViewContent(
    selectedDate: LocalDate,
    activities: List<CalendarItem>,
    onSelectDate: (LocalDate) -> Unit,
    onActivityClick: (CalendarItem) -> Unit
) {
    var weekStart by remember(selectedDate) {
        mutableStateOf(selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
    val weekEnd = weekStart.plusDays(6)
    val weekFormatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale.US) }
    val yearFormatter = remember { DateTimeFormatter.ofPattern("yyyy", Locale.US) }

    val daysOfWeek = remember(weekStart) {
        (0..6).map { weekStart.plusDays(it.toLong()) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Week range header (< 20 Oct - 26 Oct 2025 >)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ChevronLeft, "Previous Week", tint = TextWhite)
            }
            Text(
                "${weekStart.format(weekFormatter)} - ${weekEnd.format(weekFormatter)} ${weekEnd.format(yearFormatter)}",
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ChevronRight, "Next Week", tint = TextWhite)
            }
        }

        // 7-day horizontal strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysOfWeek.forEach { d ->
                val isSelected = d == selectedDate
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) VioletTab else CardInner)
                        .clickable { onSelectDate(d) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        d.dayOfWeek.name.take(3),
                        color = if (isSelected) Color.White else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${d.dayOfMonth}",
                        color = if (isSelected) Color.White else TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Time-based Vertical Schedule Timeline
        val hours = listOf("6 AM", "8 AM", "10 AM", "12 PM", "2 PM", "4 PM", "6 PM", "8 PM", "10 PM")
        val selectedDateStr = selectedDate.toString()
        val dayItems = activities.filter { it.date == selectedDateStr }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(hours) { hourLabel ->
                val matchingActivities = dayItems.filter { item ->
                    item.startTime.startsWith(hourLabel.take(2).trim()) ||
                    (hourLabel == "6 AM" && item.startTime.contains("06:") || item.startTime.contains("07:")) ||
                    (hourLabel == "8 AM" && item.startTime.contains("08:") || item.startTime.contains("09:")) ||
                    (hourLabel == "10 AM" && item.startTime.contains("10:") || item.startTime.contains("11:")) ||
                    (hourLabel == "12 PM" && item.startTime.contains("12:") || item.startTime.contains("01:")) ||
                    (hourLabel == "2 PM" && item.startTime.contains("02:") || item.startTime.contains("03:")) ||
                    (hourLabel == "4 PM" && item.startTime.contains("04:") || item.startTime.contains("05:")) ||
                    (hourLabel == "6 PM" && item.startTime.contains("06:") || item.startTime.contains("07:")) ||
                    (hourLabel == "8 PM" && item.startTime.contains("08:") || item.startTime.contains("09:"))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        hourLabel,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(48.dp).padding(top = 8.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (matchingActivities.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .border(1.dp, CardBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            )
                        } else {
                            matchingActivities.forEach { item ->
                                Surface(
                                    color = item.type.color.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, item.type.color.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onActivityClick(item) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("${item.startTime} - ${item.endTime}", color = item.type.color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        }
                                        if (item.countOrDuration.isNotBlank()) {
                                            Text(item.countOrDuration, color = TextWhite, fontSize = 10.sp)
                                        }
                                    }
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
// 8. DAY VIEW CONTENT (SCREEN 3)
// ==========================================
@Composable
private fun DayViewContent(
    selectedDate: LocalDate,
    activities: List<CalendarItem>,
    onSelectDate: (LocalDate) -> Unit,
    onToggleActivity: (CalendarItem) -> Unit,
    onActivityClick: (CalendarItem) -> Unit,
    onEditDaySummary: () -> Unit
) {
    val dayTitleFormatter = remember { DateTimeFormatter.ofPattern("EEE, d MMMM yyyy", Locale.US) }
    val dayItems = activities.filter { it.date == selectedDate.toString() }

    // Day summary metrics
    val totalTasks = dayItems.count { it.type == ActivityType.TASK }
    val compTasks = dayItems.count { it.type == ActivityType.TASK && it.isCompleted }
    val totalHabits = dayItems.count { it.type == ActivityType.HABIT }
    val compHabits = dayItems.count { it.type == ActivityType.HABIT && it.isCompleted }
    val totalRoutines = dayItems.count { it.type == ActivityType.ROUTINE }
    val compRoutines = dayItems.count { it.type == ActivityType.ROUTINE && it.isCompleted }
    val totalEvents = dayItems.count { it.type == ActivityType.EVENT }

    val stripDays = remember(selectedDate) {
        (-3..3).map { selectedDate.plusDays(it.toLong()) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // 1. Day Navigation Header (< Tue, 21 October 2025 > [Calendar])
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSelectDate(selectedDate.minusDays(1)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ChevronLeft, "Previous Day", tint = TextWhite)
                }
                Text(
                    selectedDate.format(dayTitleFormatter),
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onSelectDate(selectedDate.plusDays(1)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ChevronRight, "Next Day", tint = TextWhite)
                }
            }
        }

        // 2. Day Strip (19 Sun, 20 Mon, 21 Tue, etc.)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                stripDays.forEach { d ->
                    val isSelected = d == selectedDate
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PurplePrimary else CardInner)
                            .clickable { onSelectDate(d) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "${d.dayOfMonth}",
                            color = if (isSelected) Color.White else TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            d.dayOfWeek.name.take(3),
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Day Summary Card — values are derived from saved records only
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Day Summary", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Edit", color = CyanRoutine, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onEditDaySummary))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DaySummaryMetricBox(if (totalTasks > 0) "$compTasks/$totalTasks" else "—", "Tasks", PurplePrimary, Modifier.weight(1f))
                        DaySummaryMetricBox(if (totalHabits > 0) "$compHabits/$totalHabits" else "—", "Habits", EmeraldHabit, Modifier.weight(1f))
                        DaySummaryMetricBox(if (totalRoutines > 0) "$compRoutines/$totalRoutines" else "—", "Routines", CyanRoutine, Modifier.weight(1f))
                        DaySummaryMetricBox(totalEvents.toString(), "Events", CoralEvent, Modifier.weight(1f))
                    }
                }
            }
        }

        // 4. Vertical Timeline Schedule
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dayItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No activities scheduled for today.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    dayItems.forEach { item ->
                        CalendarScheduleRow(
                            item = item,
                            onToggle = { onToggleActivity(item) },
                            onClick = { onActivityClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySummaryMetricBox(value: String, title: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        color = CardInner,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, color = tint, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ==========================================
// 9. AGENDA VIEW CONTENT (SCREEN 4)
// ==========================================
@Composable
private fun AgendaViewContent(
    selectedDate: LocalDate,
    activities: List<CalendarItem>,
    onToggleActivity: (CalendarItem) -> Unit,
    onActivityClick: (CalendarItem) -> Unit
) {
    val today = remember { LocalDate.now() }
    val tomorrow = remember { today.plusDays(1) }

    // Group activities by date
    val grouped = remember(activities) {
        activities.groupBy { it.date }.toSortedMap()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // This Week Banner Card
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(VioletTab.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CalendarToday, null, tint = VioletTab, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text("This Week", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("20 Oct - 26 Oct 2025", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Grouped dates
        if (grouped.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("No agenda items scheduled.", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            grouped.forEach { (dateStr, itemsForDate) ->
                val date = try { LocalDate.parse(dateStr) } catch (_: Exception) { today }
                val headerTitle = when (date) {
                    today -> "Today · ${date.dayOfWeek.name.take(3)}, ${date.dayOfMonth} ${date.month.name.take(3)}"
                    tomorrow -> "Tomorrow · ${date.dayOfWeek.name.take(3)}, ${date.dayOfMonth} ${date.month.name.take(3)}"
                    else -> "${date.dayOfWeek.name.take(3)}, ${date.dayOfMonth} ${date.month.name.take(3)}"
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(headerTitle, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("${itemsForDate.size} items", color = TextMuted, fontSize = 10.sp)
                        }

                        itemsForDate.forEach { item ->
                            CalendarScheduleRow(
                                item = item,
                                onToggle = { onToggleActivity(item) },
                                onClick = { onActivityClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 10. QUICK ADD MENU SHEET (SCREEN 6)
// ==========================================
@Composable
private fun QuickAddMenuSheet(
    onDismiss: () -> Unit,
    onSelectOption: (ActivityType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Quick Add", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAddOptionItem(
                    title = "Add Task",
                    subtitle = "Create a new task",
                    icon = Icons.Default.Star,
                    color = PurplePrimary,
                    onClick = { onSelectOption(ActivityType.TASK) }
                )
                QuickAddOptionItem(
                    title = "Add Event",
                    subtitle = "Schedule an event or reminder",
                    icon = Icons.Default.Event,
                    color = CoralEvent,
                    onClick = { onSelectOption(ActivityType.EVENT) }
                )
                QuickAddOptionItem(
                    title = "Add Habit",
                    subtitle = "Add or schedule a habit",
                    icon = Icons.Default.Spa,
                    color = EmeraldHabit,
                    onClick = { onSelectOption(ActivityType.HABIT) }
                )
                QuickAddOptionItem(
                    title = "Add Study Session",
                    subtitle = "Plan a study or practice session",
                    icon = Icons.Default.MenuBook,
                    color = BlueStudy,
                    onClick = { onSelectOption(ActivityType.STUDY) }
                )
                QuickAddOptionItem(
                    title = "Add Routine Step",
                    subtitle = "Add a routine activity",
                    icon = Icons.Default.Checklist,
                    color = CyanRoutine,
                    onClick = { onSelectOption(ActivityType.ROUTINE) }
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun QuickAddOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = CardInner,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextMuted, fontSize = 10.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

// ==========================================
// 11. ADD / EDIT EVENT MODAL (SCREEN 7)
// ==========================================
@Composable
private fun AddEditEventModal(
    initialType: ActivityType,
    existingItem: CalendarItem?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (CalendarItem) -> Unit
) {
    var title by remember { mutableStateOf(existingItem?.title ?: "") }
    var type by remember { mutableStateOf(existingItem?.type ?: initialType) }
    var dateStr by remember { mutableStateOf(existingItem?.date ?: defaultDate.toString()) }
    var isAllDay by remember { mutableStateOf(existingItem?.isAllDay ?: false) }
    var startTime by remember { mutableStateOf(existingItem?.startTime ?: "09:00 AM") }
    var endTime by remember { mutableStateOf(existingItem?.endTime ?: "10:00 AM") }
    var location by remember { mutableStateOf(existingItem?.location ?: "") }
    var reminder by remember { mutableStateOf(existingItem?.reminder ?: "10 minutes before") }
    var repeatOption by remember { mutableStateOf(existingItem?.repeatOption ?: RepeatOption.NONE) }
    var notes by remember { mutableStateOf(existingItem?.description ?: "") }

    var showRepeatSelector by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (existingItem != null) "Edit ${type.label.dropLast(1)}" else "Add ${type.label.dropLast(1)}",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted)
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
                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title: e.g. Team Meeting", color = TextMuted, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurplePrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date Picker Input
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Date (YYYY-MM-DD)", color = TextMuted, fontSize = 10.sp) },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, null, tint = PurplePrimary, modifier = Modifier.size(16.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurplePrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // All Day Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("All Day", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = isAllDay,
                        onCheckedChange = { isAllDay = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                    )
                }

                // Start & End Time Fields
                if (!isAllDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = { Text("Start Time", color = TextMuted, fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = { Text("End Time", color = TextMuted, fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PurplePrimary,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Location Field
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    placeholder = { Text("Location (Optional)", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Place, null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurplePrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Repeat Option Selector
                Surface(
                    color = CardInner,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRepeatSelector = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Repeat, null, tint = PurplePrimary, modifier = Modifier.size(16.dp))
                            Text(repeatOption.label, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }

                // Notes Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Notes (Optional)", color = TextMuted, fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurplePrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val newItem = CalendarItem(
                            id = existingItem?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            description = notes.trim(),
                            type = type,
                            date = dateStr,
                            startTime = startTime,
                            endTime = endTime,
                            isAllDay = isAllDay,
                            location = location.trim(),
                            reminder = reminder,
                            repeatOption = repeatOption,
                            isCompleted = existingItem?.isCompleted ?: false
                        )
                        onSave(newItem)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (existingItem != null) "Update Activity" else "Save Activity", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )

    // Screen 8: Repeat Options Modal
    if (showRepeatSelector) {
        AlertDialog(
            onDismissRequest = { showRepeatSelector = false },
            containerColor = CardBg,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Repeat Event", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showRepeatSelector = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = TextMuted)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatOption.values().forEach { opt ->
                        val isSelected = repeatOption == opt
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) PurplePrimary.copy(alpha = 0.2f) else CardInner)
                                .clickable {
                                    repeatOption = opt
                                    showRepeatSelector = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(opt.label, color = if (isSelected) PurplePrimary else TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    repeatOption = opt
                                    showRepeatSelector = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PurplePrimary)
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// ==========================================
// 12. EVENT DETAILS MODAL (SCREEN 9)
// ==========================================
@Composable
private fun EventDetailsModal(
    item: CalendarItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(item.type.color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.type.icon, null, tint = item.type.color, modifier = Modifier.size(18.dp))
                    }
                    Text(item.title, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Date & Time
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CalendarToday, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    Text("${item.date} · ${item.startTime} - ${item.endTime}", color = TextWhite, fontSize = 12.sp)
                }

                // Location
                if (item.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Place, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Text(item.location, color = TextWhite, fontSize = 12.sp)
                    }
                }

                // Reminder
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Notifications, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    Text(item.reminder, color = TextWhite, fontSize = 12.sp)
                }

                // Description
                if (item.description.isNotBlank()) {
                    Surface(
                        color = CardInner,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(item.description, color = TextWhite, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = CoralEvent)
                }
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Edit", color = Color.White)
                }
            }
        }
    )
}

// ==========================================
// 13. DATE DETAILS MODAL (SCREEN 5)
// ==========================================
@Composable
private fun DateDetailsModal(
    date: LocalDate,
    activities: List<CalendarItem>,
    onDismiss: () -> Unit,
    onToggleActivity: (CalendarItem) -> Unit,
    onAddNewForDate: () -> Unit
) {
    var activeFilter by remember { mutableStateOf("All") }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US) }

    val filtered = remember(activities, activeFilter) {
        if (activeFilter == "All") activities
        else activities.filter { it.type.label == activeFilter }
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
                Text(date.format(formatter), color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Filter chips: All | Tasks | Habits | Routines | Study | Events
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("All", "Tasks", "Habits", "Routines", "Study", "Events")) { f ->
                        val isSelected = activeFilter == f
                        Surface(
                            color = if (isSelected) PurplePrimary else CardInner,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable { activeFilter = f }
                        ) {
                            Text(
                                f,
                                color = if (isSelected) Color.White else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // List of items
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No activities found for this filter.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered) { item ->
                            CalendarScheduleRow(
                                item = item,
                                onToggle = { onToggleActivity(item) },
                                onClick = {}
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAddNewForDate,
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Add to ${date.dayOfMonth} ${date.month.name.take(3)}", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ==========================================
// 14. CALENDAR FILTERS MODAL (SCREEN 10)
// ==========================================
@Composable
private fun CalendarFiltersModal(
    filterTasks: Boolean,
    filterHabits: Boolean,
    filterRoutines: Boolean,
    filterStudy: Boolean,
    filterEvents: Boolean,
    showCompleted: Boolean,
    onDismiss: () -> Unit,
    onApply: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    var tTasks by remember { mutableStateOf(filterTasks) }
    var tHabits by remember { mutableStateOf(filterHabits) }
    var tRoutines by remember { mutableStateOf(filterRoutines) }
    var tStudy by remember { mutableStateOf(filterStudy) }
    var tEvents by remember { mutableStateOf(filterEvents) }
    var tCompleted by remember { mutableStateOf(showCompleted) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filters", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterToggleRow("Tasks", PurplePrimary, tTasks) { tTasks = it }
                FilterToggleRow("Habits", EmeraldHabit, tHabits) { tHabits = it }
                FilterToggleRow("Routines", CyanRoutine, tRoutines) { tRoutines = it }
                FilterToggleRow("Study Sessions", BlueStudy, tStudy) { tStudy = it }
                FilterToggleRow("Events", CoralEvent, tEvents) { tEvents = it }
                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))
                FilterToggleRow("Show Completed", TextMuted, tCompleted) { tCompleted = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(tTasks, tHabits, tRoutines, tStudy, tEvents, tCompleted) },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Filters", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun FilterToggleRow(title: String, tint: Color, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(tint, CircleShape))
            Text(title, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
        )
    }
}

// ==========================================
// 15. DAILY SUMMARY MODAL (SCREEN 11)
// ==========================================
@Composable
private fun DailySummaryModal(
    date: LocalDate,
    activities: List<CalendarItem>,
    onDismiss: () -> Unit
) {
    val totalTasks = activities.count { it.type == ActivityType.TASK }
    val compTasks = activities.count { it.type == ActivityType.TASK && it.isCompleted }
    val totalHabits = activities.count { it.type == ActivityType.HABIT }
    val compHabits = activities.count { it.type == ActivityType.HABIT && it.isCompleted }
    val totalRoutines = activities.count { it.type == ActivityType.ROUTINE }
    val compRoutines = activities.count { it.type == ActivityType.ROUTINE && it.isCompleted }
    val totalStudy = activities.count { it.type == ActivityType.STUDY }
    val compStudy = activities.count { it.type == ActivityType.STUDY && it.isCompleted }
    val totalEvents = activities.count { it.type == ActivityType.EVENT }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Day Summary", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Circular Progress Rings Grid (2x2)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ProgressRingCard(if (totalTasks > 0) "$compTasks/$totalTasks" else "—", "Tasks", if (totalTasks > 0) compTasks.toFloat() / totalTasks else 0f, PurplePrimary, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    ProgressRingCard(if (totalHabits > 0) "$compHabits/$totalHabits" else "—", "Habits", if (totalHabits > 0) compHabits.toFloat() / totalHabits else 0f, EmeraldHabit, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ProgressRingCard(if (totalRoutines > 0) "$compRoutines/$totalRoutines" else "—", "Routines", if (totalRoutines > 0) compRoutines.toFloat() / totalRoutines else 0f, CyanRoutine, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    ProgressRingCard(if (totalStudy > 0) "$compStudy/$totalStudy" else "—", "Study", if (totalStudy > 0) compStudy.toFloat() / totalStudy else 0f, BlueStudy, Modifier.weight(1f))
                }

                // 2 Summary Metric Cards (Events Today & Total Time)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = CardInner,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(totalEvents.toString(), color = CoralEvent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Events Today", color = TextMuted, fontSize = 9.sp)
                        }
                    }
                    Surface(
                        color = CardInner,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                activities.filter { it.type == ActivityType.STUDY }
                                    .sumOf { it.countOrDuration.substringBefore(" ").toIntOrNull() ?: 0 }
                                    .let { minutes -> if (minutes <= 0) "—" else "${minutes / 60}h ${minutes % 60}m" },
                                color = CyanRoutine, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold
                            )
                            Text("Total Time", color = TextMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PurplePrimary)
            }
        }
    )
}

@Composable
private fun ProgressRingCard(value: String, title: String, progress: Float, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        color = CardInner,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val strokeW = 5.dp.toPx()
                    drawCircle(CardBg, style = Stroke(strokeW))
                    drawArc(tint, -90f, progress.coerceIn(0f, 1f) * 360f, false, style = Stroke(strokeW, cap = StrokeCap.Round))
                }
                Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}
