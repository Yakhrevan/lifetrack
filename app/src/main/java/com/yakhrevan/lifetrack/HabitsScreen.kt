package com.yakhrevan.lifetrack

import android.content.Context
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

// ==========================================
// 1. DESIGN TOKENS
// ==========================================
private val DarkBackground = Color(0xFF090D1A)
private val CardBackground = Color(0xFF121929)
private val CardInner = Color(0xFF1B2438)
private val CardBorder = Color(0x1FFFFFFF)
private val PurpleBrand = Color(0xFFA855F7)
private val CyanBrand = Color(0xFF06B6D4)
private val GreenBrand = Color(0xFF10B981)
private val TextWhite = Color(0xFFF1F5F9)
private val TextMuted = Color(0xFF94A3B8)

// ==========================================
// 2. HABITS SCREEN MAIN COMPOSABLE
// ==========================================

@Composable
fun HabitsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onHabitCompletedCallback: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Screen navigation state within Habits module
    // "list", "create", "edit", "details", "templates"
    var currentView by remember { mutableStateOf("list") }
    var selectedHabitId by remember { mutableStateOf<String?>(null) }
    var habitToEdit by remember { mutableStateOf<Habit?>(null) }

    val habits = remember(refreshTrigger) {
        HabitRoutineRepository.getHabits(context, includeArchived = false)
    }

    when (currentView) {
        "list" -> {
            HabitsMainListView(
                habits = habits,
                onRefresh = { refreshTrigger++ },
                onCreateHabitClick = {
                    habitToEdit = null
                    currentView = "create"
                },
                onHabitClick = { habit ->
                    selectedHabitId = habit.id
                    currentView = "details"
                },
                onOpenTemplates = {
                    currentView = "templates"
                },
                onHabitCompletedCallback = onHabitCompletedCallback,
                modifier = modifier
            )
        }
        "create", "edit" -> {
            CreateOrEditHabitView(
                initialHabit = habitToEdit,
                onSave = { habit ->
                    HabitRoutineRepository.saveHabit(context, habit)
                    refreshTrigger++
                    currentView = "list"
                },
                onCancel = {
                    currentView = if (selectedHabitId != null) "details" else "list"
                },
                modifier = modifier
            )
        }
        "details" -> {
            val habit = remember(selectedHabitId, refreshTrigger) {
                selectedHabitId?.let { HabitRoutineRepository.getHabitById(context, it) }
            }
            if (habit == null) {
                currentView = "list"
            } else {
                HabitDetailsView(
                    habit = habit,
                    onBack = { currentView = "list" },
                    onEdit = {
                        habitToEdit = habit
                        currentView = "edit"
                    },
                    onRefresh = { refreshTrigger++ },
                    onDeleted = {
                        selectedHabitId = null
                        refreshTrigger++
                        currentView = "list"
                    },
                    modifier = modifier
                )
            }
        }
        "templates" -> {
            HabitTemplatesView(
                onBack = { currentView = "list" },
                onSelectTemplate = { template ->
                    // Opens Create Habit prefilled with the template for user preview and editing!
                    habitToEdit = Habit(
                        name = template.name,
                        description = template.description,
                        iconName = template.iconName,
                        colorHex = template.colorHex,
                        category = template.category,
                        targetType = template.targetType,
                        targetAmount = template.targetAmount,
                        unit = template.unit,
                        frequencyType = template.frequencyType,
                        weeklyTarget = template.weeklyTarget
                    )
                    currentView = "create"
                },
                modifier = modifier
            )
        }
    }
}

// ==========================================
// 3. HABITS MAIN LIST VIEW (SCREEN 1)
// ==========================================

@Composable
private fun HabitsMainListView(
    habits: List<Habit>,
    onRefresh: () -> Unit,
    onCreateHabitClick: () -> Unit,
    onHabitClick: (Habit) -> Unit,
    onOpenTemplates: () -> Unit,
    onHabitCompletedCallback: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }
    var selectedFilter by remember { mutableStateOf("Today") } // Today, Yesterday, Upcoming, All, Completed
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    // Week days window around selected date
    val weekDays = remember(selectedDate) {
        val startOfWeek = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)
        (0..6).map { startOfWeek.plusDays(it.toLong()) }
    }

    // Filter habits according to schedule, search and tab
    val filteredHabits = remember(habits, selectedDate, selectedFilter, searchQuery) {
        habits.filter { habit ->
            // Search filter
            val matchesSearch = searchQuery.isBlank() ||
                    habit.name.contains(searchQuery, ignoreCase = true) ||
                    habit.category.contains(searchQuery, ignoreCase = true)

            if (!matchesSearch) return@filter false

            val isScheduledForDay = HabitRoutineRepository.isHabitScheduled(habit, selectedDate)
            val completion = HabitRoutineRepository.getCompletion(context, habit.id, selectedDate.toString())
            val isCompleted = completion?.status == "completed"

            when (selectedFilter) {
                "Today" -> HabitRoutineRepository.isHabitScheduled(habit, LocalDate.now())
                "Yesterday" -> HabitRoutineRepository.isHabitScheduled(habit, LocalDate.now().minusDays(1))
                "Upcoming" -> (1L..366L).any { offset ->
                    HabitRoutineRepository.isHabitScheduled(habit, LocalDate.now().plusDays(offset))
                }
                "Completed" -> isScheduledForDay && isCompleted
                else -> true
            }
        }
    }

    val completedCountToday = remember(habits, selectedDate) {
        habits.count { habit ->
            val completion = HabitRoutineRepository.getCompletion(context, habit.id, selectedDate.toString())
            completion?.status == "completed"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Habits",
                        color = TextWhite,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Build better you",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showSearch = !showSearch },
                        modifier = Modifier
                            .size(38.dp)
                            .background(CardInner, CircleShape)
                    ) {
                        Icon(
                            if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Create Habit Button (+)
                    Button(
                        onClick = onCreateHabitClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Habit", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Search Bar (Expandable)
        if (showSearch) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search your habits...", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = PurpleBrand,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = CardInner,
                        unfocusedContainerColor = CardInner
                    ),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) }
                )
            }
        }

        // Week Date Strip (Mon 12, Tue 13, Wed 14...)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekDays.forEach { day ->
                    val isSelected = day == selectedDate
                    val isCurrentDay = day == today
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) PurpleBrand else if (isCurrentDay) CardInner else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) PurpleBrand else if (isCurrentDay) CardBorder else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedDate = day }
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            day.dayOfMonth.toString(),
                            color = if (isSelected) Color.White else TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Filter Pills: All | Today | Completed
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Today" to habits.count { HabitRoutineRepository.isHabitScheduled(it, LocalDate.now()) },
                    "Yesterday" to habits.count { HabitRoutineRepository.isHabitScheduled(it, LocalDate.now().minusDays(1)) },
                    "Upcoming" to habits.count { habit -> (1L..30L).any { HabitRoutineRepository.isHabitScheduled(habit, LocalDate.now().plusDays(it)) } },
                    "All" to habits.size,
                    "Completed" to completedCountToday
                ).forEach { (filter, count) ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) PurpleBrand else CardInner)
                            .clickable {
                                selectedFilter = filter
                                when (filter) {
                                    "Today" -> selectedDate = LocalDate.now()
                                    "Yesterday" -> selectedDate = LocalDate.now().minusDays(1)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            if (filter == "Completed") "$filter $count" else filter,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Empty State (Strictly shown when user has not created habits)
        if (habits.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(PurpleBrand.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Spa,
                                contentDescription = null,
                                tint = PurpleBrand,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            "No habits created yet",
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Start building your daily routines with custom measurable targets, custom schedules, and reminders.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onCreateHabitClick,
                                colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Create Habit", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onOpenTemplates,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Text("Browse Templates", color = TextWhite, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        } else if (filteredHabits.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No habits match your selected filter.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            // Habit Cards List
            items(filteredHabits, key = { it.id }) { habit ->
                HabitCardItem(
                    habit = habit,
                    date = selectedDate,
                    onClick = { onHabitClick(habit) },
                    onStatusToggled = {
                        val completion = HabitRoutineRepository.getCompletion(context, habit.id, selectedDate.toString())
                        val willComplete = completion?.status != "completed"
                        if (willComplete) {
                            HabitRoutineRepository.markHabitComplete(context, habit.id, selectedDate.toString())
                        } else {
                            HabitRoutineRepository.undoHabitCompletion(context, habit.id, selectedDate.toString())
                        }
                        onHabitCompletedCallback(habit.name, willComplete)
                        onRefresh()
                    }
                )
            }
        }
    }
}

// ==========================================
// 4. HABIT CARD ITEM COMPONENT
// ==========================================

@Composable
private fun HabitCardItem(
    habit: Habit,
    date: LocalDate,
    onClick: () -> Unit,
    onStatusToggled: () -> Unit
) {
    val context = LocalContext.current
    val completion = remember(habit.id, date) {
        HabitRoutineRepository.getCompletion(context, habit.id, date.toString())
    }
    val isCompleted = completion?.status == "completed"
    val isSkipped = completion?.status == "skipped"
    val habitColor = HabitRoutineIconHelper.parseColor(habit.colorHex)
    val habitIcon = HabitRoutineIconHelper.getIcon(habit.iconName)

    val currentProgress = completion?.progressValue ?: 0.0
    val targetAmount = habit.targetAmount.coerceAtLeast(1.0)
    val progressFraction = (currentProgress / targetAmount).toFloat().coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Habit Icon Box
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(habitColor.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    habitIcon,
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Habit Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    habit.name,
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(3.dp))

                val subtitle = when (habit.targetType) {
                    "Quantity" -> "${habit.targetAmount} ${habit.unit} · ${habit.frequencyType}"
                    "Duration" -> "${habit.targetAmount.toInt()} min · ${habit.frequencyType}"
                    else -> habit.frequencyType
                }

                Text(
                    subtitle,
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            // Interactive Progress / Checkmark Indicator
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onStatusToggled),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(CyanBrand, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (habit.targetType == "Quantity" || habit.targetType == "Duration") {
                    // Circular progress ring showing fractional completion
                    Canvas(modifier = Modifier.size(38.dp)) {
                        drawCircle(
                            color = CardInner,
                            radius = size.minDimension / 2,
                            style = Stroke(width = 3.5.dp.toPx())
                        )
                        drawArc(
                            color = CyanBrand,
                            startAngle = -90f,
                            sweepAngle = 360f * progressFraction,
                            useCenter = false,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        if (currentProgress > 0) "${currentProgress.toInt()}/${targetAmount.toInt()}" else "",
                        color = CyanBrand,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    // Simple ring checkbox
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .border(2.dp, CardBorder, CircleShape)
                    )
                }
            }
        }
    }
}

// ==========================================
// 5. CREATE OR EDIT HABIT VIEW (SCREEN 2)
// ==========================================

@Composable
private fun CreateOrEditHabitView(
    initialHabit: Habit?,
    onSave: (Habit) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isEdit = initialHabit != null && initialHabit.createdAtMs > 0 && initialHabit.id.isNotBlank()

    var name by remember { mutableStateOf(initialHabit?.name ?: "") }
    var description by remember { mutableStateOf(initialHabit?.description ?: "") }
    var selectedIcon by remember { mutableStateOf(initialHabit?.iconName ?: "check") }
    var selectedColorHex by remember { mutableStateOf(initialHabit?.colorHex ?: "#A855F7") }
    var category by remember { mutableStateOf(initialHabit?.category ?: "") }
    var targetType by remember { mutableStateOf(initialHabit?.targetType ?: "Quantity") }
    var targetAmountInput by remember { mutableStateOf(initialHabit?.targetAmount?.toInt()?.toString() ?: "20") }
    var unit by remember { mutableStateOf(initialHabit?.unit ?: "pages") }
    var frequencyType by remember { mutableStateOf(initialHabit?.frequencyType ?: "Daily") }
    var selectedWeekdays by remember { mutableStateOf(initialHabit?.weekdays ?: setOf(1, 2, 3, 4, 5)) }
    var startDate by remember { mutableStateOf(initialHabit?.startDate ?: LocalDate.now().toString()) }
    var endDate by remember { mutableStateOf(initialHabit?.endDate.orEmpty()) }
    var intervalDaysInput by remember { mutableStateOf((initialHabit?.intervalDays ?: 2).toString()) }
    var weeklyTargetInput by remember { mutableStateOf((initialHabit?.weeklyTarget ?: 3).toString()) }

    // Reminders state
    var reminderEnabled by remember { mutableStateOf(initialHabit?.reminders?.any { it.enabled } ?: true) }
    var reminderTime by remember { mutableStateOf(initialHabit?.reminders?.firstOrNull()?.time ?: "20:00") }
    var reminderMessage by remember { mutableStateOf(initialHabit?.reminders?.firstOrNull()?.notificationMessage ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val userCategories = remember { HabitRoutineRepository.getCategories(context).map { it.name } }
    var showCategoryMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
    ) {
        // Top Action Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text(
                        if (isEdit) "Edit Habit" else "Create Habit",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        if (name.isBlank()) {
                            errorMessage = "Please enter a habit name."
                            return@Button
                        }
                        val parsedStart = runCatching { LocalDate.parse(startDate) }.getOrNull()
                        val parsedEnd = if (endDate.isBlank()) null else runCatching { LocalDate.parse(endDate) }.getOrNull()
                        val parsedReminderTime = runCatching { java.time.LocalTime.parse(reminderTime) }.getOrNull()
                        if (parsedStart == null) {
                            errorMessage = "Start date must use YYYY-MM-DD."
                            return@Button
                        }
                        if (endDate.isNotBlank() && (parsedEnd == null || parsedEnd.isBefore(parsedStart))) {
                            errorMessage = "End date must be valid and on or after the start date."
                            return@Button
                        }
                        if (frequencyType == "Selected weekdays" && selectedWeekdays.isEmpty()) {
                            errorMessage = "Select at least one weekday."
                            return@Button
                        }
                        if (frequencyType == "Interval" && (intervalDaysInput.toIntOrNull() ?: 0) < 1) {
                            errorMessage = "Interval must be at least 1 day."
                            return@Button
                        }
                        if (frequencyType == "Weekly frequency" && (weeklyTargetInput.toIntOrNull() ?: 0) !in 1..7) {
                            errorMessage = "Weekly target must be between 1 and 7."
                            return@Button
                        }
                        if (reminderEnabled && parsedReminderTime == null) {
                            errorMessage = "Reminder time must use HH:mm (24-hour format)."
                            return@Button
                        }
                        val amount = targetAmountInput.toDoubleOrNull() ?: 1.0
                        if (targetType != "Simple" && amount <= 0.0) {
                            errorMessage = "Target amount must be greater than zero."
                            return@Button
                        }
                        val reminder = if (reminderEnabled) {
                            listOf(
                                HabitReminder(
                                    time = reminderTime,
                                    enabled = true,
                                    notificationMessage = reminderMessage
                                )
                            )
                        } else emptyList()

                        val habit = (initialHabit ?: Habit(name = name)).copy(
                            name = name.trim(),
                            description = description.trim(),
                            iconName = selectedIcon,
                            colorHex = selectedColorHex,
                            category = category.trim(),
                            targetType = targetType,
                            targetAmount = amount,
                            unit = unit.trim(),
                            frequencyType = frequencyType,
                            weekdays = if (frequencyType == "Selected weekdays") selectedWeekdays else emptySet(),
                            weeklyTarget = if (frequencyType == "Weekly frequency") weeklyTargetInput.toInt() else initialHabit?.weeklyTarget ?: 3,
                            intervalDays = if (frequencyType == "Interval") intervalDaysInput.toInt() else initialHabit?.intervalDays ?: 1,
                            startDate = parsedStart.toString(),
                            endDate = parsedEnd?.toString(),
                            reminders = reminder
                        )
                        onSave(habit)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Icon Picker Row
        item {
            Text("CHOOSE ICON", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(HabitRoutineIconHelper.availableIcons) { item ->
                    val isSelected = selectedIcon.equals(item.id, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) PurpleBrand else CardInner)
                            .border(
                                1.5.dp,
                                if (isSelected) Color.White else CardBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { selectedIcon = item.id },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) Color.White else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Name & Description Fields
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("HABIT DETAILS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Habit Name (e.g. Read Book)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = PurpleBrand,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = CardInner,
                        unfocusedContainerColor = CardInner
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = PurpleBrand,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = CardInner,
                        unfocusedContainerColor = CardInner
                    ),
                    maxLines = 2
                )
            }
        }

        // Category & Color Selection
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category Input / Dropdown
                Column(modifier = Modifier.weight(1f)) {
                    Text("CATEGORY", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        placeholder = { Text("Personal, Health...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = PurpleBrand,
                            unfocusedBorderColor = CardBorder,
                            focusedContainerColor = CardInner,
                            unfocusedContainerColor = CardInner
                        ),
                        singleLine = true
                    )
                }

                // Color Palette
                Column(modifier = Modifier.weight(1f)) {
                    Text("COLOR", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .background(CardInner, RoundedCornerShape(14.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(HabitRoutineIconHelper.availableColors) { color ->
                            val hex = HabitRoutineIconHelper.colorToHex(color)
                            val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        if (isSelected) 2.5.dp else 0.dp,
                                        Color.White,
                                        CircleShape
                                    )
                                    .clickable { selectedColorHex = hex }
                            )
                        }
                    }
                }
            }
        }

        // Target Type & Target Amount
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TARGET & GOAL", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Simple", "Quantity", "Duration").forEach { type ->
                        val isSelected = targetType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) PurpleBrand else CardInner)
                                .clickable { targetType = type }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                type,
                                color = if (isSelected) Color.White else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (targetType != "Simple") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = targetAmountInput,
                            onValueChange = { targetAmountInput = it.filter(Char::isDigit) },
                            label = { Text("Target Amount") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = PurpleBrand,
                                unfocusedBorderColor = CardBorder,
                                focusedContainerColor = CardInner,
                                unfocusedContainerColor = CardInner
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("Unit (pages, L, min)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = PurpleBrand,
                                unfocusedBorderColor = CardBorder,
                                focusedContainerColor = CardInner,
                                unfocusedContainerColor = CardInner
                            ),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // Repeat Schedule
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("REPEAT SCHEDULE", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Daily", "Selected weekdays", "Weekly frequency", "Interval").forEach { rep ->
                        val isSelected = frequencyType == rep
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) PurpleBrand else CardInner)
                                .clickable { frequencyType = rep }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when (rep) { "Selected weekdays" -> "Weekdays"; "Weekly frequency" -> "Weekly"; else -> rep },
                                color = if (isSelected) Color.White else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (frequencyType == "Weekly frequency") {
                    OutlinedTextField(
                        value = weeklyTargetInput,
                        onValueChange = { weeklyTargetInput = it.filter(Char::isDigit).take(1) },
                        label = { Text("Times per week (1–7)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (frequencyType == "Interval") {
                    OutlinedTextField(
                        value = intervalDaysInput,
                        onValueChange = { intervalDaysInput = it.filter(Char::isDigit).take(3) },
                        label = { Text("Repeat every N days") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it.take(10); errorMessage = null },
                        label = { Text("Start date (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it.take(10); errorMessage = null },
                        label = { Text("End date (optional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                if (frequencyType == "Selected weekdays") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(
                            1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S"
                        ).forEach { (dayIso, label) ->
                            val isSelected = selectedWeekdays.contains(dayIso)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) CyanBrand else CardInner)
                                    .clickable {
                                        selectedWeekdays = if (isSelected) {
                                            selectedWeekdays - dayIso
                                        } else {
                                            selectedWeekdays + dayIso
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) Color.Black else TextWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reminder Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Notifications, null, tint = PurpleBrand)
                            Text("Enable Reminder", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { reminderEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PurpleBrand
                            )
                        )
                    }

                    if (reminderEnabled) {
                        OutlinedTextField(
                            value = reminderTime,
                            onValueChange = { reminderTime = it.take(5) },
                            label = { Text("Reminder Time (HH:mm, 24-hr)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = PurpleBrand,
                                unfocusedBorderColor = CardBorder,
                                focusedContainerColor = CardInner,
                                unfocusedContainerColor = CardInner
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = reminderMessage,
                            onValueChange = { reminderMessage = it },
                            label = { Text("Notification Message (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = PurpleBrand,
                                unfocusedBorderColor = CardBorder,
                                focusedContainerColor = CardInner,
                                unfocusedContainerColor = CardInner
                            ),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // Error message if any
        if (errorMessage != null) {
            item {
                Text(
                    errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ==========================================
// 6. HABIT DETAILS VIEW (SCREEN 3)
// ==========================================

@Composable
private fun HabitDetailsView(
    habit: Habit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var selectedTab by remember { mutableStateOf("Overview") } // "Overview", "Calendar", "Statistics"
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val completion = remember(habit.id, today) {
        HabitRoutineRepository.getCompletion(context, habit.id, today.toString())
    }
    val currentProgress = completion?.progressValue ?: 0.0
    val targetAmount = habit.targetAmount.coerceAtLeast(1.0)
    val isCompleted = completion?.status == "completed"

    val (currentStreak, longestStreak) = remember(habit.id) {
        HabitRoutineRepository.calculateHabitStreak(context, habit.id, today)
    }

    val habitColor = HabitRoutineIconHelper.parseColor(habit.colorHex)
    val habitIcon = HabitRoutineIconHelper.getIcon(habit.iconName)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
    ) {
        // Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }

                Text(
                    "Habit Details",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextWhite)
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Habit") },
                            onClick = {
                                showMoreMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                showMoreMenu = false
                                HabitRoutineRepository.duplicateHabit(context, habit.id)
                                onRefresh()
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (habit.isPaused) "Resume Habit" else "Pause Habit") },
                            onClick = {
                                showMoreMenu = false
                                HabitRoutineRepository.togglePauseHabit(context, habit.id)
                                onRefresh()
                            },
                            leadingIcon = { Icon(Icons.Default.Pause, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Habit", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMoreMenu = false
                                showDeleteConfirm = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }

        // Header Card: Icon, Name, Category · Frequency
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(habitColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            habitIcon,
                            contentDescription = null,
                            tint = habitColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column {
                        Text(
                            habit.name,
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${habit.category.ifBlank { "Habit" }} · ${habit.frequencyType}",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Sub-tabs: Overview | Calendar | Statistics
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Overview", "Calendar", "Statistics").forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PurpleBrand else CardInner)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tab,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        when (selectedTab) {
            "Overview" -> {
                // Today's Progress Card with Stepper controls
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Today's Progress", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Progress Circle
                                val pct = ((currentProgress / targetAmount) * 100).toInt().coerceIn(0, 100)
                                Box(
                                    modifier = Modifier.size(90.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(
                                            color = CardInner,
                                            radius = size.minDimension / 2,
                                            style = Stroke(width = 8.dp.toPx())
                                        )
                                        drawArc(
                                            color = CyanBrand,
                                            startAngle = -90f,
                                            sweepAngle = 360f * (pct / 100f),
                                            useCenter = false,
                                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                    Text("$pct%", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }

                                // Numeric Stepper: - [value] +
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "${currentProgress.toInt()} / ${targetAmount.toInt()} ${habit.unit}",
                                        color = TextWhite,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val remaining = (targetAmount - currentProgress).coerceAtLeast(0.0).toInt()
                                    Text(
                                        "$remaining ${habit.unit} remaining",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Minus Button
                                        IconButton(
                                            onClick = {
                                                val nextVal = (currentProgress - 1.0).coerceAtLeast(0.0)
                                                HabitRoutineRepository.recordProgress(
                                                    context,
                                                    habit.id,
                                                    today.toString(),
                                                    nextVal,
                                                    targetAmount
                                                )
                                                onRefresh()
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(CardInner, RoundedCornerShape(10.dp))
                                        ) {
                                            Text("-", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Current Value Badge
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp)
                                                .background(CardInner, RoundedCornerShape(10.dp))
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                currentProgress.toInt().toString(),
                                                color = CyanBrand,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Plus Button
                                        IconButton(
                                            onClick = {
                                                val nextVal = currentProgress + 1.0
                                                HabitRoutineRepository.recordProgress(
                                                    context,
                                                    habit.id,
                                                    today.toString(),
                                                    nextVal,
                                                    targetAmount
                                                )
                                                onRefresh()
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(PurpleBrand, RoundedCornerShape(10.dp))
                                        ) {
                                            Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Quick Action Buttons: Mark Complete | Skip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isCompleted) {
                                            HabitRoutineRepository.undoHabitCompletion(context, habit.id, today.toString())
                                        } else {
                                            HabitRoutineRepository.markHabitComplete(context, habit.id, today.toString())
                                        }
                                        onRefresh()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isCompleted) GreenBrand else PurpleBrand),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isCompleted) "Completed" else "Mark Complete", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        HabitRoutineRepository.markHabitSkipped(context, habit.id, today.toString())
                                        onRefresh()
                                    },
                                    modifier = Modifier.weight(0.7f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CardBorder)
                                ) {
                                    Text("Skip", color = TextMuted)
                                }
                            }
                        }
                    }
                }

                // Streaks Card
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFFF97316).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFF97316), modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Current Streak", color = TextMuted, fontSize = 10.sp)
                                    Text("$currentStreak days", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFFEAB308).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFEAB308), modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Longest Streak", color = TextMuted, fontSize = 10.sp)
                                    Text("$longestStreak days", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Action Menu Rows (Edit, Duplicate, Pause, Delete)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column {
                            ActionRowItem(Icons.Default.Edit, "Edit Habit", onClick = onEdit)
                            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                            ActionRowItem(Icons.Default.ContentCopy, "Duplicate", onClick = {
                                HabitRoutineRepository.duplicateHabit(context, habit.id)
                                onRefresh()
                            })
                            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                            ActionRowItem(
                                if (habit.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                if (habit.isPaused) "Resume Habit" else "Pause Habit",
                                onClick = {
                                    HabitRoutineRepository.togglePauseHabit(context, habit.id)
                                    onRefresh()
                                }
                            )
                            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                            ActionRowItem(
                                Icons.Default.Delete,
                                "Delete Habit",
                                textColor = MaterialTheme.colorScheme.error,
                                onClick = { showDeleteConfirm = true }
                            )
                        }
                    }
                }
            }
            "Calendar" -> {
                item {
                    HabitMonthlyHeatmapCard(habit = habit)
                }
            }
            "Statistics" -> {
                item {
                    HabitStatsCard(habit = habit)
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Habit?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete '${habit.name}'? All scheduled reminders will be cancelled. Past historical records will be preserved.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        HabitRoutineRepository.deleteHabit(context, habit.id)
                        showDeleteConfirm = false
                        onDeleted()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBackground
        )
    }
}

@Composable
private fun ActionRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    textColor: Color = TextWhite,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = textColor, modifier = Modifier.size(20.dp))
        Text(title, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}

// ==========================================
// 7. HABIT MONTHLY HEATMAP CARD (SCREEN 8 ADAPTED)
// ==========================================

@Composable
private fun HabitMonthlyHeatmapCard(habit: Habit) {
    val context = LocalContext.current
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    val daysInMonth = currentYearMonth.lengthOfMonth()
    val firstDayOfWeek = currentYearMonth.atDay(1).dayOfWeek.value // 1=Mon .. 7=Sun

    val completions = remember(habit.id, currentYearMonth) {
        HabitRoutineRepository.getCompletions(context).filter { it.habitId == habit.id }
            .associateBy { it.date }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Month Navigation Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentYearMonth = currentYearMonth.minusMonths(1) }) {
                    Icon(Icons.Default.ChevronLeft, null, tint = TextWhite)
                }
                Text(
                    currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { currentYearMonth = currentYearMonth.plusMonths(1) }) {
                    Icon(Icons.Default.ChevronRight, null, tint = TextWhite)
                }
            }

            // Days of week header (Sun, Mon, Tue...)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Days grid (up to 6 rows)
            val totalCells = ((firstDayOfWeek - 1) + daysInMonth + 6) / 7 * 7
            val rows = totalCells / 7

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        for (c in 0..6) {
                            val cellIndex = r * 7 + c
                            val dayNum = cellIndex - (firstDayOfWeek - 1) + 1
                            if (dayNum in 1..daysInMonth) {
                                val date = currentYearMonth.atDay(dayNum)
                                val comp = completions[date.toString()]
                                val isCompleted = comp?.status == "completed"
                                val isSkipped = comp?.status == "skipped"
                                val isScheduled = HabitRoutineRepository.isHabitScheduled(habit, date)

                                val bgColor = when {
                                    isCompleted -> CyanBrand
                                    isSkipped -> TextMuted.copy(alpha = 0.4f)
                                    isScheduled && date.isBefore(LocalDate.now()) -> Color(0xFFEF4444).copy(alpha = 0.25f)
                                    else -> CardInner
                                }

                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(bgColor, CircleShape)
                                        .border(
                                            1.dp,
                                            if (date == LocalDate.now()) PurpleBrand else Color.Transparent,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dayNum.toString(),
                                        color = if (isCompleted) Color.Black else TextWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.size(34.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. HABIT STATS CARD (SCREEN 9 ADAPTED)
// ==========================================

@Composable
private fun HabitStatsCard(habit: Habit) {
    val context = LocalContext.current
    var selectedRange by remember { mutableStateOf("7D") } // "7D", "30D", "3M", "1Y"
    val today = remember { LocalDate.now() }

    val startDate = remember(selectedRange, today) {
        when (selectedRange) {
            "7D" -> today.minusDays(6)
            "30D" -> today.minusDays(29)
            "3M" -> today.minusMonths(3)
            "1Y" -> today.minusYears(1)
            else -> today.minusDays(6)
        }
    }

    val completionRate = remember(habit.id, startDate, today) {
        HabitRoutineRepository.calculateHabitCompletionRate(context, habit.id, startDate, today)
    }

    val completions = remember(habit.id) {
        HabitRoutineRepository.getCompletions(context).filter { it.habitId == habit.id }
            .associateBy { it.date }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Timeframe Pills: 7D | 30D | 3M | 1Y
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("7D", "30D", "3M", "1Y").forEach { range ->
                    val isSelected = selectedRange == range
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PurpleBrand else CardInner)
                            .clickable { selectedRange = range }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            range,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Completion Rate Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Completion Rate", color = TextMuted, fontSize = 12.sp)
                    Text(
                        "${completionRate.toInt()}%",
                        color = CyanBrand,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .background(GreenBrand.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ArrowUpward, null, tint = GreenBrand, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Consistency Trend", color = GreenBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 7-day Bar Chart Visualizer
            val last7Days = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(CardInner, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                last7Days.forEach { day ->
                    val isDone = completions[day.toString()]?.status == "completed"
                    val isScheduled = HabitRoutineRepository.isHabitScheduled(habit, day)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(if (isDone) 60.dp else if (isScheduled) 20.dp else 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDone) CyanBrand else if (isScheduled) CardBorder else CardBorder.copy(alpha = 0.3f))
                        )
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. OPTIONAL TEMPLATES VIEW (SCREEN 11)
// ==========================================

@Composable
private fun HabitTemplatesView(
    onBack: () -> Unit,
    onSelectTemplate: (HabitRoutineTemplates.HabitTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    val templates = remember { HabitRoutineTemplates.getAvailableHabitTemplates() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Column {
                    Text("Habit Templates", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Select a template to preview and customize before saving", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        items(templates) { template ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelectTemplate(template) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                HabitRoutineIconHelper.parseColor(template.colorHex).copy(alpha = 0.2f),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            HabitRoutineIconHelper.getIcon(template.iconName),
                            contentDescription = null,
                            tint = HabitRoutineIconHelper.parseColor(template.colorHex),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(template.name, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(template.description, color = TextMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${template.targetAmount.toInt()} ${template.unit} · ${template.frequencyType}",
                            color = CyanBrand,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                }
            }
        }
    }
}
