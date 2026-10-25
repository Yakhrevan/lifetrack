package com.yakhrevan.lifetrack

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
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
// 2. ROUTINES SCREEN MAIN COMPOSABLE
// ==========================================

@Composable
fun RoutinesScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Screen views: "list", "builder", "run", "templates"
    var currentView by remember { mutableStateOf("list") }
    var selectedRoutineToEdit by remember { mutableStateOf<Routine?>(null) }
    var runningRoutine by remember { mutableStateOf<Routine?>(null) }

    val routines = remember(refreshTrigger) {
        HabitRoutineRepository.getRoutines(context, includeArchived = false)
    }

    when (currentView) {
        "list" -> {
            RoutinesMainListView(
                routines = routines,
                onRefresh = { refreshTrigger++ },
                onCreateRoutine = {
                    selectedRoutineToEdit = null
                    currentView = "builder"
                },
                onEditRoutine = { routine ->
                    selectedRoutineToEdit = routine
                    currentView = "builder"
                },
                onRunRoutine = { routine ->
                    runningRoutine = routine
                    currentView = "run"
                },
                onOpenTemplates = {
                    currentView = "templates"
                },
                modifier = modifier
            )
        }
        "builder" -> {
            RoutineBuilderView(
                initialRoutine = selectedRoutineToEdit,
                onSave = { routine ->
                    HabitRoutineRepository.saveRoutine(context, routine)
                    refreshTrigger++
                    currentView = "list"
                },
                onCancel = { currentView = "list" },
                modifier = modifier
            )
        }
        "run" -> {
            val routine = runningRoutine
            if (routine == null) {
                currentView = "list"
            } else {
                RunRoutineView(
                    routine = routine,
                    onFinished = { session ->
                        HabitRoutineRepository.recordRoutineSession(context, session)
                        refreshTrigger++
                        currentView = "list"
                    },
                    onExit = { currentView = "list" },
                    modifier = modifier
                )
            }
        }
        "templates" -> {
            RoutineTemplatesView(
                onBack = { currentView = "list" },
                onSelectTemplate = { template ->
                    val newRoutineId = UUID.randomUUID().toString()
                    val steps = template.steps.mapIndexed { index, stepTmpl ->
                        RoutineStep(
                            routineId = newRoutineId,
                            name = stepTmpl.name,
                            description = stepTmpl.description,
                            iconName = stepTmpl.iconName,
                            durationSeconds = stepTmpl.durationSeconds,
                            orderIndex = index
                        )
                    }
                    selectedRoutineToEdit = Routine(
                        id = newRoutineId,
                        name = template.name,
                        description = template.description,
                        iconName = template.iconName,
                        colorHex = template.colorHex,
                        category = template.category,
                        startTime = template.startTime,
                        steps = steps
                    )
                    currentView = "builder"
                },
                modifier = modifier
            )
        }
    }
}

// ==========================================
// 3. ROUTINES MAIN LIST VIEW (SCREEN 4)
// ==========================================

@Composable
private fun RoutinesMainListView(
    routines: List<Routine>,
    onRefresh: () -> Unit,
    onCreateRoutine: () -> Unit,
    onEditRoutine: (Routine) -> Unit,
    onRunRoutine: (Routine) -> Unit,
    onOpenTemplates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("Today") } // Today, Yesterday, Upcoming, All
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var selectedScheduleDate by remember { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()

    val filteredRoutines = remember(routines, selectedFilter, searchQuery, selectedScheduleDate, today) {
        routines.filter { r ->
            val matchesSearch = searchQuery.isBlank() ||
                    r.name.contains(searchQuery, ignoreCase = true) ||
                    r.category.contains(searchQuery, ignoreCase = true)
            if (!matchesSearch) return@filter false

            when (selectedFilter) {
                "Today" -> HabitRoutineRepository.isRoutineScheduled(r, today)
                "Yesterday" -> HabitRoutineRepository.isRoutineScheduled(r, today.minusDays(1))
                "Upcoming" -> HabitRoutineRepository.getNextRoutineDate(r, today.plusDays(1)) != null
                else -> true
            }
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
                        "Routines",
                        color = TextWhite,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Organize your day",
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

                    Button(
                        onClick = onCreateRoutine,
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Routine", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Expandable Search Bar
        if (showSearch) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search routines...", color = TextMuted, fontSize = 13.sp) },
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

        // Filter Pills: All | Today | Upcoming
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Today", "Yesterday", "Upcoming", "All").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) PurpleBrand else CardInner)
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            filter,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Empty State (Strictly shown when user has no routines created)
        if (routines.isEmpty()) {
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
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = PurpleBrand,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            "No routines created yet",
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Create step-by-step routines for morning, study, workout, or night with guided timers and duration tracking.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onCreateRoutine,
                                colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Create Routine", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
        } else if (filteredRoutines.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No routines found in this filter.", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            // User-created Routine Cards
            items(filteredRoutines, key = { it.id }) { routine ->
                RoutineCardItem(
                    routine = routine,
                    onEdit = { onEditRoutine(routine) },
                    onRun = { onRunRoutine(routine) },
                    onTogglePause = {
                        HabitRoutineRepository.togglePauseRoutine(context, routine.id)
                        onRefresh()
                    },
                    onDelete = {
                        HabitRoutineRepository.deleteRoutine(context, routine.id)
                        onRefresh()
                    }
                )
            }
        }
    }
}

// ==========================================
// 4. ROUTINE CARD ITEM COMPONENT
// ==========================================

@Composable
private fun RoutineCardItem(
    routine: Routine,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit
) {
    val routineColor = HabitRoutineIconHelper.parseColor(routine.colorHex)
    val routineIcon = HabitRoutineIconHelper.getIcon(routine.iconName)
    val totalSeconds = routine.steps.sumOf { it.durationSeconds }
    val totalMinutes = totalSeconds / 60
    val context = LocalContext.current
    val completedToday = remember(routine.id) {
        HabitRoutineRepository.getSessionsForDate(context, LocalDate.now().toString())
            .any { it.routineId == routine.id && it.isCompleted }
    }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Routine Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(routineColor.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        routineIcon,
                        contentDescription = null,
                        tint = routineColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Title and Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        routine.name,
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${routine.startTime} · ${routine.repeatType}",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                // Play / Run Button
                IconButton(
                    onClick = onRun,
                    modifier = Modifier
                        .size(44.dp)
                        .background(PurpleBrand, CircleShape)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Run Routine",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Step Progress and Estimated Duration Bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${routine.steps.size} steps",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (totalMinutes > 0) "$totalMinutes min" else "$totalSeconds sec",
                        color = CyanBrand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Progress Bar preview
                LinearProgressIndicator(
                    progress = { if (routine.steps.isEmpty() || !completedToday) 0f else 1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyanBrand,
                    trackColor = CardInner
                )
            }
        }
    }
}

// ==========================================
// 5. ROUTINE BUILDER VIEW (SCREEN 5)
// ==========================================

@Composable
private fun RoutineBuilderView(
    initialRoutine: Routine?,
    onSave: (Routine) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEdit = initialRoutine != null && initialRoutine.createdAtMs > 0 && initialRoutine.id.isNotBlank()

    var name by remember { mutableStateOf(initialRoutine?.name ?: "") }
    var description by remember { mutableStateOf(initialRoutine?.description ?: "") }
    var selectedIcon by remember { mutableStateOf(initialRoutine?.iconName ?: "sun") }
    var selectedColorHex by remember { mutableStateOf(initialRoutine?.colorHex ?: "#EC4899") }
    var repeatType by remember { mutableStateOf(initialRoutine?.repeatType ?: "Daily") }
    var startDate by remember { mutableStateOf(initialRoutine?.startDate ?: LocalDate.now().toString()) }
    var endDate by remember { mutableStateOf(initialRoutine?.endDate.orEmpty()) }
    var intervalDays by remember { mutableStateOf((initialRoutine?.intervalDays ?: 2).toString()) }
    var selectedWeekdays by remember { mutableStateOf(initialRoutine?.weekdays ?: setOf(1, 3, 5)) }
    var startTime by remember { mutableStateOf(initialRoutine?.startTime ?: "06:30") }
    var reminderEnabled by remember { mutableStateOf(initialRoutine?.reminderEnabled ?: false) }
    var reminderTime by remember { mutableStateOf(initialRoutine?.reminderTime ?: "06:20") }

    // Dynamic steps: starts with 0 steps when creating a new routine (per requirement 8)
    var steps by remember { mutableStateOf(initialRoutine?.steps?.toMutableList() ?: mutableListOf<RoutineStep>()) }

    var stepToEdit by remember { mutableStateOf<RoutineStep?>(null) }
    var showStepEditDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                        if (isEdit) "Edit Routine" else "Routine Builder",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        if (name.isBlank()) {
                            errorMessage = "Please enter a routine name."
                            return@Button
                        }
                        if (steps.isEmpty()) {
                            errorMessage = "Add at least one step before saving this routine."
                            return@Button
                        }
                        if (runCatching { LocalTime.parse(startTime) }.getOrNull() == null) {
                            errorMessage = "Start time must use HH:mm (24-hour format)."
                            return@Button
                        }
                        val parsedStartDate = runCatching { LocalDate.parse(startDate) }.getOrNull()
                        val parsedEndDate = if (endDate.isBlank()) null else runCatching { LocalDate.parse(endDate) }.getOrNull()
                        if (parsedStartDate == null || (endDate.isNotBlank() && (parsedEndDate == null || parsedEndDate.isBefore(parsedStartDate)))) {
                            errorMessage = "Enter valid dates; end date must be on or after start date."
                            return@Button
                        }
                        if (repeatType == "Selected weekdays" && selectedWeekdays.isEmpty()) {
                            errorMessage = "Select at least one weekday."
                            return@Button
                        }
                        if (repeatType == "Interval" && (intervalDays.toIntOrNull() ?: 0) < 1) {
                            errorMessage = "Repeat interval must be at least one day."
                            return@Button
                        }
                        if (reminderEnabled && runCatching { LocalTime.parse(reminderTime) }.getOrNull() == null) {
                            errorMessage = "Reminder time must use HH:mm (24-hour format)."
                            return@Button
                        }
                        val routineId = initialRoutine?.id ?: UUID.randomUUID().toString()
                        val orderedSteps = steps.mapIndexed { index, step ->
                            step.copy(routineId = routineId, orderIndex = index)
                        }
                        val routine = (initialRoutine ?: Routine(id = routineId, name = name)).copy(
                            name = name.trim(),
                            description = description.trim(),
                            iconName = selectedIcon,
                            colorHex = selectedColorHex,
                            repeatType = repeatType,
                            weekdays = if (repeatType == "Selected weekdays") selectedWeekdays else emptySet(),
                            intervalDays = if (repeatType == "Interval") intervalDays.toInt() else initialRoutine?.intervalDays ?: 1,
                            startDate = parsedStartDate.toString(),
                            endDate = parsedEndDate?.toString(),
                            startTime = startTime,
                            reminderEnabled = reminderEnabled,
                            reminderTime = reminderTime,
                            steps = orderedSteps
                        )
                        onSave(routine)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Routine Name & Icon Picker
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("ROUTINE DETAILS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        label = { Text("Routine Name (e.g. Morning Routine)") },
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

                    // Color palette
                    Text("COLOR", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(HabitRoutineIconHelper.availableColors) { color ->
                            val hex = HabitRoutineIconHelper.colorToHex(color)
                            val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(color, CircleShape)
                                    .border(if (isSelected) 3.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { selectedColorHex = hex }
                            )
                        }
                    }
                }
            }
        }

        // Schedule & Start Time
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it.take(5) },
                    label = { Text("Start Time (HH:mm)") },
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

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Repeat schedule", color = TextMuted, fontSize = 11.sp)
                    listOf("Daily", "Selected weekdays", "Interval").forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (repeatType == option) PurpleBrand.copy(alpha = 0.22f) else CardInner)
                                .clickable { repeatType = option }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = repeatType == option, onClick = { repeatType = option }, colors = RadioButtonDefaults.colors(selectedColor = PurpleBrand))
                            Text(if (option == "Selected weekdays") "Custom weekdays" else if (option == "Interval") "Every N days" else option, color = TextWhite, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().background(CardBackground, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Active date range", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = startDate, onValueChange = { startDate = it.take(10) },
                    label = { Text("Start date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endDate, onValueChange = { endDate = it.take(10) },
                    label = { Text("End date (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (repeatType == "Selected weekdays") {
                    Text("Repeat on", color = TextMuted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("M" to 1, "T" to 2, "W" to 3, "T" to 4, "F" to 5, "S" to 6, "S" to 7).forEach { (label, day) ->
                            val selected = day in selectedWeekdays
                            Box(
                                modifier = Modifier.weight(1f).clip(CircleShape)
                                    .background(if (selected) PurpleBrand else CardInner)
                                    .clickable { selectedWeekdays = if (selected) selectedWeekdays - day else selectedWeekdays + day }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(label, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (repeatType == "Interval") {
                    OutlinedTextField(
                        value = intervalDays, onValueChange = { intervalDays = it.filter(Char::isDigit).take(3) },
                        label = { Text("Repeat every N days") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Reminder Toggle Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Alarm, null, tint = PurpleBrand)
                        Column {
                            Text("Routine Reminder", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Scheduled before start ($reminderTime)", color = TextMuted, fontSize = 11.sp)
                        }
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
            }
        }

        // Routine Steps Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "ROUTINE STEPS",
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${steps.size} steps configured",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = {
                        stepToEdit = null
                        showStepEditDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = PurpleBrand, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Step", color = PurpleBrand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Empty Steps Notice (When 0 steps exist)
        if (steps.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardInner)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Zero steps in this routine",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Press 'Add Step' above to create each ordered task with its duration.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Ordered Steps List
            itemsIndexed(steps) { index, step ->
                RoutineStepItemRow(
                    index = index + 1,
                    step = step,
                    onMoveUp = {
                        if (index > 0) {
                            val mutable = steps.toMutableList()
                            val item = mutable.removeAt(index)
                            mutable.add(index - 1, item)
                            steps = mutable
                        }
                    },
                    onMoveDown = {
                        if (index < steps.size - 1) {
                            val mutable = steps.toMutableList()
                            val item = mutable.removeAt(index)
                            mutable.add(index + 1, item)
                            steps = mutable
                        }
                    },
                    onEdit = {
                        stepToEdit = step
                        showStepEditDialog = true
                    },
                    onDuplicate = {
                        val duplicated = step.copy(
                            id = UUID.randomUUID().toString(),
                            name = "${step.name} (Copy)"
                        )
                        val mutable = steps.toMutableList()
                        mutable.add(index + 1, duplicated)
                        steps = mutable
                    },
                    onDelete = {
                        val mutable = steps.toMutableList()
                        mutable.removeAt(index)
                        steps = mutable
                    }
                )
            }
        }

        // Error message if any
        if (errorMessage != null) {
            item {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }

    // Step Add / Edit Dialog (Screen 7)
    if (showStepEditDialog) {
        RoutineStepEditDialog(
            initialStep = stepToEdit,
            onDismiss = { showStepEditDialog = false },
            onSaveStep = { newOrEditedStep ->
                val mutable = steps.toMutableList()
                val existingIndex = mutable.indexOfFirst { it.id == newOrEditedStep.id }
                if (existingIndex >= 0) {
                    mutable[existingIndex] = newOrEditedStep
                } else {
                    mutable.add(newOrEditedStep)
                }
                steps = mutable
                showStepEditDialog = false
            }
        )
    }
}

// ==========================================
// 6. ROUTINE STEP ROW IN BUILDER
// ==========================================

@Composable
private fun RoutineStepItemRow(
    index: Int,
    step: RoutineStep,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val stepIcon = HabitRoutineIconHelper.getIcon(step.iconName)
    val durationMin = step.durationSeconds / 60
    val durationSec = step.durationSeconds % 60

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Step Number Badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(CardInner, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(index.toString(), color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Step Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(CyanBrand.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(stepIcon, null, tint = CyanBrand, modifier = Modifier.size(18.dp))
            }

            // Step Name & Duration
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    step.name,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (durationMin > 0) "$durationMin min" else "$durationSec sec",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            // Move Up & Move Down Accessible Controls
            IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ArrowUpward, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ArrowDownward, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }

            // Options Menu
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }

                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit Step") },
                        onClick = { showMenu = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = { showMenu = false; onDuplicate() },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Step", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

// ==========================================
// 7. ROUTINE STEP EDIT DIALOG (SCREEN 7)
// ==========================================

@Composable
private fun RoutineStepEditDialog(
    initialStep: RoutineStep?,
    onDismiss: () -> Unit,
    onSaveStep: (RoutineStep) -> Unit
) {
    var name by remember { mutableStateOf(initialStep?.name ?: "") }
    var description by remember { mutableStateOf(initialStep?.description ?: "") }
    var selectedIcon by remember { mutableStateOf(initialStep?.iconName ?: "check") }
    var durationMinutesInput by remember {
        mutableStateOf(((initialStep?.durationSeconds ?: 300) / 60).toString())
    }
    var durationUnit by remember { mutableStateOf("Minutes") } // "Minutes" or "Seconds"
    var reminderEnabled by remember { mutableStateOf(initialStep?.reminderEnabled ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialStep == null) "Add Step" else "Edit Step",
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Step Name (e.g. Wake up, Drink Water)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = PurpleBrand,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = CardInner,
                        unfocusedContainerColor = CardInner
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = PurpleBrand,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = CardInner,
                        unfocusedContainerColor = CardInner
                    )
                )

                // Duration Input
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = durationMinutesInput,
                        onValueChange = { durationMinutesInput = it.filter(Char::isDigit) },
                        label = { Text("Duration") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = PurpleBrand,
                            unfocusedBorderColor = CardBorder,
                            focusedContainerColor = CardInner,
                            unfocusedContainerColor = CardInner
                        )
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .background(CardInner, RoundedCornerShape(12.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                durationUnit = if (durationUnit == "Minutes") "Seconds" else "Minutes"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(durationUnit, color = CyanBrand, fontWeight = FontWeight.Bold)
                    }
                }

                // Icon Picker
                Text("Icon", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HabitRoutineIconHelper.availableIcons) { item ->
                        val isSelected = selectedIcon.equals(item.id, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (isSelected) PurpleBrand else CardInner, RoundedCornerShape(10.dp))
                                .clickable { selectedIcon = item.id },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, null, tint = if (isSelected) Color.White else TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Enter a step name."
                        return@Button
                    }
                    val num = durationMinutesInput.toIntOrNull() ?: 5
                    val seconds = if (durationUnit == "Minutes") num * 60 else num
                    val step = (initialStep ?: RoutineStep(name = name)).copy(
                        name = name.trim(),
                        description = description.trim(),
                        iconName = selectedIcon,
                        durationSeconds = seconds,
                        reminderEnabled = reminderEnabled
                    )
                    onSaveStep(step)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
        containerColor = CardBackground
    )
}

// ==========================================
// 8. RUN ROUTINE GUIDED EXECUTION (SCREEN 6)
// ==========================================

@Composable
private fun RunRoutineView(
    routine: Routine,
    onFinished: (RoutineSession) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = routine.steps
    if (steps.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This routine has no steps.", color = TextWhite, fontSize = 16.sp)
                Button(onClick = onExit) { Text("Back") }
            }
        }
        return
    }

    var currentStepIndex by remember { mutableIntStateOf(0) }
    val activeStep = steps.getOrNull(currentStepIndex) ?: steps.last()
    var isPaused by remember { mutableStateOf(false) }
    var secondsRemaining by remember(currentStepIndex) { mutableIntStateOf(activeStep.durationSeconds.coerceAtLeast(1)) }
    var sessionStartTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val completedStepIds = remember { mutableStateListOf<String>() }
    val skippedStepIds = remember { mutableStateListOf<String>() }
    var showExitConfirm by remember { mutableStateOf(false) }
    var isCelebration by remember { mutableStateOf(false) }

    // Countdown Timer Loop
    LaunchedEffect(currentStepIndex, isPaused, isCelebration) {
        while (!isPaused && !isCelebration && secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        }
    }

    if (isCelebration) {
        // Routine Completion Celebration Screen
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(GreenBrand.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = GreenBrand, modifier = Modifier.size(36.dp))
                    }

                    Text(if (completedStepIds.size == steps.size && skippedStepIds.isEmpty()) "Routine Complete!" else "Routine Session Saved", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "You finished ${completedStepIds.size} of ${steps.size} steps in '${routine.name}'.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            val session = RoutineSession(
                                routineId = routine.id,
                                routineName = routine.name,
                                date = LocalDate.now().toString(),
                                startedAtMs = sessionStartTimeMs,
                                completedAtMs = System.currentTimeMillis(),
                                durationSeconds = ((System.currentTimeMillis() - sessionStartTimeMs) / 1000).toInt(),
                                completedStepIds = completedStepIds.toList(),
                                skippedStepIds = skippedStepIds.toList(),
                                totalStepsCount = steps.size,
                                isCompleted = steps.isNotEmpty() && completedStepIds.size == steps.size && skippedStepIds.isEmpty()
                            )
                            onFinished(session)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Finish & Save Session", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // Active Execution Screen
    val totalProgress = ((currentStepIndex.toFloat() / steps.size.toFloat()) * 100).toInt()
    val formattedTimer = String.format("%02d:%02d", secondsRemaining / 60, secondsRemaining % 60)
    val activeIcon = HabitRoutineIconHelper.getIcon(activeStep.iconName)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header: Routine Name, Step X of Y, Progress Bar
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showExitConfirm = true }) {
                    Icon(Icons.Default.Close, null, tint = TextWhite)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(routine.name, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Step ${currentStepIndex + 1} of ${steps.size}", color = TextMuted, fontSize = 12.sp)
                }

                Text("$totalProgress%", color = CyanBrand, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            LinearProgressIndicator(
                progress = { (currentStepIndex + 1).toFloat() / steps.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CyanBrand,
                trackColor = CardInner
            )
        }

        // Active Step Center Card with Giant Timer
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Step Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(CyanBrand.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(activeIcon, null, tint = CyanBrand, modifier = Modifier.size(32.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        activeStep.name,
                        color = TextWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (activeStep.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(activeStep.description, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }

                // Giant Countdown Timer Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardInner, RoundedCornerShape(20.dp))
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        formattedTimer,
                        color = if (secondsRemaining == 0) GreenBrand else TextWhite,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        // Execution Control Buttons: Exit | Play/Pause | Next Step | Skip Step
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel / Exit
            IconButton(
                onClick = { showExitConfirm = true },
                modifier = Modifier
                    .size(52.dp)
                    .background(CardInner, CircleShape)
            ) {
                Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(24.dp))
            }

            // Play / Pause Large Center Button
            IconButton(
                onClick = { isPaused = !isPaused },
                modifier = Modifier
                    .size(68.dp)
                    .background(PurpleBrand, CircleShape)
            ) {
                Icon(
                    if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Next Step / Complete Step
            IconButton(
                onClick = {
                    completedStepIds.add(activeStep.id)
                    if (currentStepIndex < steps.size - 1) {
                        currentStepIndex++
                    } else {
                        isCelebration = true
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(CyanBrand, CircleShape)
            ) {
                Icon(Icons.Default.SkipNext, null, tint = Color.Black, modifier = Modifier.size(28.dp))
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Exit Routine?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to exit? Your progress in this session will not be completed.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirm = false
                        onExit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Exit", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Resume", color = TextMuted) }
            },
            containerColor = CardBackground
        )
    }
}

// ==========================================
// 9. ROUTINE TEMPLATES PREVIEW (SCREEN 11)
// ==========================================

@Composable
private fun RoutineTemplatesView(
    onBack: () -> Unit,
    onSelectTemplate: (HabitRoutineTemplates.RoutineTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    val templates = remember { HabitRoutineTemplates.getAvailableRoutineTemplates() }

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
                    Text("Routine Templates", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Select a template to preview, customize steps, and save", color = TextMuted, fontSize = 11.sp)
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                        }

                        Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                    }

                    // Steps preview
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardInner, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        template.steps.forEachIndexed { i, s ->
                            Text(
                                "${i + 1}. ${s.name} (${s.durationSeconds / 60}m)",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
