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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
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
private val OrangeBrand = Color(0xFFF97316)
private val RedPriority = Color(0xFFEF4444)
private val YellowPriority = Color(0xFFEAB308)
private val BluePriority = Color(0xFF38BDF8)
private val TextWhite = Color(0xFFF1F5F9)
private val TextMuted = Color(0xFF94A3B8)

// ==========================================
// 2. MAIN TASKS SCREEN COMPOSABLE
// ==========================================

@Composable
fun TasksScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Screen views: "list", "create", "edit", "details", "calendar", "reminder_settings"
    var currentView by remember { mutableStateOf("list") }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var taskToEdit by remember { mutableStateOf<TaskItem?>(null) }

    val tasks = remember(refreshTrigger) {
        TaskRepository.getTasks(context, includeArchived = false)
    }

    when (currentView) {
        "list" -> {
            TasksMainListView(
                tasks = tasks,
                onRefresh = { refreshTrigger++ },
                onCreateTask = {
                    taskToEdit = null
                    currentView = "create"
                },
                onTaskClick = { task ->
                    selectedTaskId = task.id
                    currentView = "details"
                },
                onOpenCalendar = {
                    currentView = "calendar"
                },
                modifier = modifier
            )
        }
        "create", "edit" -> {
            CreateOrEditTaskView(
                initialTask = taskToEdit,
                onSave = { task ->
                    TaskRepository.saveTask(context, task)
                    refreshTrigger++
                    currentView = "list"
                },
                onCancel = {
                    currentView = if (selectedTaskId != null) "details" else "list"
                },
                modifier = modifier
            )
        }
        "details" -> {
            val task = remember(selectedTaskId, refreshTrigger) {
                selectedTaskId?.let { TaskRepository.getTaskById(context, it) }
            }
            if (task == null) {
                currentView = "list"
            } else {
                TaskDetailsView(
                    task = task,
                    onBack = { currentView = "list" },
                    onEdit = {
                        taskToEdit = task
                        currentView = "edit"
                    },
                    onOpenReminderSettings = {
                        taskToEdit = task
                        currentView = "reminder_settings"
                    },
                    onRefresh = { refreshTrigger++ },
                    onDeleted = {
                        selectedTaskId = null
                        refreshTrigger++
                        currentView = "list"
                    },
                    modifier = modifier
                )
            }
        }
        "calendar" -> {
            TaskCalendarView(
                tasks = tasks,
                onBack = { currentView = "list" },
                onTaskClick = { task ->
                    selectedTaskId = task.id
                    currentView = "details"
                },
                onRefresh = { refreshTrigger++ },
                modifier = modifier
            )
        }
        "reminder_settings" -> {
            val task = taskToEdit
            if (task == null) {
                currentView = "list"
            } else {
                TaskReminderSettingsView(
                    task = task,
                    onBack = { currentView = "details" },
                    onSave = { updated ->
                        TaskRepository.saveTask(context, updated)
                        refreshTrigger++
                        currentView = "details"
                    },
                    modifier = modifier
                )
            }
        }
    }
}

// ==========================================
// 3. TASKS MAIN LIST VIEW (EMPTY & LIST)
// ==========================================

@Composable
private fun TasksMainListView(
    tasks: List<TaskItem>,
    onRefresh: () -> Unit,
    onCreateTask: () -> Unit,
    onTaskClick: (TaskItem) -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Today", "Upcoming", "Overdue", "Completed"
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var isCompletedExpanded by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    val completedTasks = remember(tasks) {
        tasks.filter { it.isCompleted }
    }
    val todayTasks = remember(tasks, today) {
        tasks.filter { it.dueDate == today.toString() && !it.isCompleted }
    }
    val todayCompletedTasks = remember(tasks, today) {
        tasks.filter { it.dueDate == today.toString() && it.isCompleted }
    }
    val upcomingTasks = remember(tasks, today) {
        tasks.filter {
            val due = runCatching { LocalDate.parse(it.dueDate) }.getOrNull()
            due != null && due.isAfter(today) && !it.isCompleted
        }
    }
    val overdueTasks = remember(tasks, today) {
        tasks.filter {
            val due = runCatching { LocalDate.parse(it.dueDate) }.getOrNull()
            due != null && due.isBefore(today) && !it.isCompleted
        }
    }
    val otherPendingTasks = remember(tasks, today) {
        tasks.filter { t ->
            !t.isCompleted &&
            t.dueDate != today.toString() &&
            runCatching { LocalDate.parse(t.dueDate) }.getOrNull()?.isAfter(today) != true &&
            runCatching { LocalDate.parse(t.dueDate) }.getOrNull()?.isBefore(today) != true
        }
    }
    val pendingTasks = remember(tasks) {
        tasks.filter { !it.isCompleted }
    }

    val filteredTasks = remember(tasks, selectedFilter, searchQuery) {
        tasks.filter { t ->
            val matchesQuery = searchQuery.isBlank() ||
                    t.title.contains(searchQuery, ignoreCase = true) ||
                    t.category.contains(searchQuery, ignoreCase = true) ||
                    t.description.contains(searchQuery, ignoreCase = true)
            if (!matchesQuery) return@filter false

            val due = runCatching { LocalDate.parse(t.dueDate) }.getOrNull()
            when (selectedFilter) {
                "Today" -> t.dueDate == today.toString() && !t.isCompleted
                "Upcoming" -> due != null && due.isAfter(today) && !t.isCompleted
                "Overdue" -> due != null && due.isBefore(today) && !t.isCompleted
                "Completed" -> t.isCompleted
                else -> !t.isCompleted
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 100.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tasks", color = TextWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Stay organized and get things done", color = TextMuted, fontSize = 13.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenCalendar,
                        modifier = Modifier
                            .size(38.dp)
                            .background(CardInner, CircleShape)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = TextWhite, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { showSearch = !showSearch },
                        modifier = Modifier
                            .size(38.dp)
                            .background(CardInner, CircleShape)
                    ) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, null, tint = TextWhite, modifier = Modifier.size(18.dp))
                    }

                    Button(
                        onClick = onCreateTask,
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Task", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                    placeholder = { Text("Search your tasks...", color = TextMuted, fontSize = 13.sp) },
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

        // Filter Pills: All | Today | Upcoming | Overdue | Completed
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "All" to pendingTasks.size,
                    "Today" to todayTasks.size,
                    "Upcoming" to upcomingTasks.size,
                    "Overdue" to overdueTasks.size,
                    "Completed" to completedTasks.size
                )
                items(filters) { (filter, count) ->
                    val isSelected = selectedFilter == filter
                    val pillColor = if (filter == "Completed" && isSelected) GreenBrand else if (isSelected) PurpleBrand else CardInner
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(pillColor)
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "${if (filter == "Completed") "Finished" else filter} $count",
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // EMPTY STATE (Shown strictly when user has created 0 tasks)
        if (tasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .background(PurpleBrand.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = PurpleBrand, modifier = Modifier.size(38.dp))
                        }

                        Text("No tasks yet", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                        Text(
                            "Create your first task to plan, stay focused and be productive.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = onCreateTask,
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.6f)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create Task", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (selectedFilter == "All" && searchQuery.isBlank()) {
            // Completed tasks are kept in Finished; keep the active view calm when none remain.
            if (pendingTasks.isEmpty() && completedTasks.isNotEmpty()) {
                item {
                    EmptyFilteredCard("No active tasks. Completed tasks are in Finished.")
                }
            }

            // Grouped Sections: Overdue, Today, Upcoming, Other (Active pending tasks ONLY)
            if (overdueTasks.isNotEmpty()) {
                item {
                    SectionHeader("Overdue", overdueTasks.size, RedPriority)
                }
                items(overdueTasks, key = { it.id }) { task ->
                    TaskCardRow(task = task, onClick = { onTaskClick(task) }, onToggle = {
                        TaskRepository.toggleTaskCompletion(context, task.id)
                        onRefresh()
                    })
                }
            }

            if (todayTasks.isNotEmpty()) {
                item {
                    SectionHeader("Today", todayTasks.size, CyanBrand)
                }
                items(todayTasks, key = { it.id }) { task ->
                    TaskCardRow(task = task, onClick = { onTaskClick(task) }, onToggle = {
                        TaskRepository.toggleTaskCompletion(context, task.id)
                        onRefresh()
                    })
                }
            }

            if (upcomingTasks.isNotEmpty()) {
                item {
                    SectionHeader("Upcoming", upcomingTasks.size, PurpleBrand)
                }
                items(upcomingTasks, key = { it.id }) { task ->
                    TaskCardRow(task = task, onClick = { onTaskClick(task) }, onToggle = {
                        TaskRepository.toggleTaskCompletion(context, task.id)
                        onRefresh()
                    })
                }
            }

            if (otherPendingTasks.isNotEmpty()) {
                item {
                    SectionHeader("Other Tasks", otherPendingTasks.size, BluePriority)
                }
                items(otherPendingTasks, key = { it.id }) { task ->
                    TaskCardRow(task = task, onClick = { onTaskClick(task) }, onToggle = {
                        TaskRepository.toggleTaskCompletion(context, task.id)
                        onRefresh()
                    })
                }
            }
        } else if (selectedFilter == "Today" && searchQuery.isBlank()) {
            // Today View: Pending today tasks first
            if (todayTasks.isNotEmpty()) {
                items(todayTasks, key = { it.id }) { task ->
                    TaskCardRow(task = task, onClick = { onTaskClick(task) }, onToggle = {
                        TaskRepository.toggleTaskCompletion(context, task.id)
                        onRefresh()
                    })
                }
            } else {
                item {
                    EmptyFilteredCard("No tasks scheduled for today.")
                }
            }
        } else {
            // Filtered Flat List (Search results, Upcoming, Overdue, or Completed)
            if (filteredTasks.isEmpty()) {
                item {
                    val emptyMsg = when (selectedFilter) {
                        "Completed" -> "No finished tasks yet. Tap the circle on any task to finish it and it moves here."
                        "Upcoming" -> "No upcoming tasks."
                        "Overdue" -> "No overdue tasks. You're on track!"
                        else -> "No tasks found matching your filter."
                    }
                    EmptyFilteredCard(emptyMsg)
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    TaskCardRow(task = task, onClick = { onTaskClick(task) }, onToggle = {
                        TaskRepository.toggleTaskCompletion(context, task.id)
                        onRefresh()
                    })
                }
            }
        }
    }
}

@Composable
private fun CompletedHeader(
    count: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    title: String = "Completed"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(GreenBrand.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = GreenBrand, modifier = Modifier.size(14.dp))
            }
            Text(title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .background(GreenBrand.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(count.toString(), color = GreenBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                if (isExpanded) "Hide" else "Show",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Hide" else "Show",
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EmptyFilteredCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.AssignmentLate, null, tint = TextMuted, modifier = Modifier.size(34.dp))
            Text(
                message,
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .background(accentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(count.toString(), color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 4. TASK CARD ROW
// ==========================================

@Composable
private fun TaskCardRow(
    task: TaskItem,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val priorityColor = when (task.priority) {
        "High" -> RedPriority
        "Low" -> BluePriority
        else -> YellowPriority
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Interactive Priority Checkbox
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (task.isCompleted) GreenBrand else Color.Transparent)
                    .border(2.dp, if (task.isCompleted) GreenBrand else priorityColor, CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                }
            }

            // Task Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    color = if (task.isCompleted) TextMuted else TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Priority chip
                    Box(
                        modifier = Modifier
                            .background(priorityColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(task.priority, color = priorityColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Due text
                    val dueText = if (task.dueTime != null) "${task.dueDate}, ${task.dueTime}" else task.dueDate
                    Text(dueText, color = TextMuted, fontSize = 11.sp)
                }
            }

            if (task.subtasks.isNotEmpty()) {
                val done = task.subtasks.count { it.isCompleted }
                Text("$done/${task.subtasks.size}", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ==========================================
// 5. CREATE OR EDIT TASK VIEW
// ==========================================

@Composable
private fun CreateOrEditTaskView(
    initialTask: TaskItem?,
    onSave: (TaskItem) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEdit = initialTask != null && initialTask.title.isNotBlank()

    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var priority by remember { mutableStateOf(initialTask?.priority ?: "Medium") }
    var startDate by remember { mutableStateOf(initialTask?.startDate ?: "") }
    var dueDate by remember { mutableStateOf(initialTask?.dueDate ?: LocalDate.now().toString()) }
    var dueTime by remember { mutableStateOf(initialTask?.dueTime ?: "17:00") }
    var repeatType by remember { mutableStateOf(initialTask?.repeatType ?: "Does not repeat") }
    var category by remember { mutableStateOf(initialTask?.category ?: "") }
    var selectedIcon by remember { mutableStateOf(initialTask?.iconName ?: "clipboard") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
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
                        if (isEdit) "Edit Task" else "Create Task",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        if (title.isBlank()) {
                            errorMessage = "Please enter a task title."
                            return@Button
                        }
                        val task = (initialTask ?: TaskItem(title = title)).copy(
                            title = title.trim(),
                            description = description.trim(),
                            priority = priority,
                            startDate = startDate.ifBlank { null },
                            dueDate = dueDate.ifBlank { LocalDate.now().toString() },
                            dueTime = dueTime.ifBlank { null },
                            repeatType = repeatType,
                            category = category.trim(),
                            iconName = selectedIcon
                        )
                        onSave(task)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Suggested Templates (if creating a new task)
        if (!isEdit) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SUGGESTED TEMPLATES", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val templates = listOf(
                        Triple("Work Task", "Work", "High") to ("work" to "Prepare project deliverables"),
                        Triple("Study & Read", "Study", "Medium") to ("book" to "Review chapter notes"),
                        Triple("Fitness Session", "Fitness", "Medium") to ("fitness" to "Daily exercise routine"),
                        Triple("Grocery Shopping", "Shopping", "Low") to ("cart" to "Buy essential items"),
                        Triple("Important Reminder", "Personal", "High") to ("clipboard" to "Important reminder")
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(templates) { (info, extra) ->
                            val (tplTitle, tplCat, tplPri) = info
                            val (tplIcon, tplDesc) = extra
                            Surface(
                                color = CardInner,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.clickable {
                                    title = tplTitle
                                    category = tplCat
                                    priority = tplPri
                                    selectedIcon = tplIcon
                                    description = tplDesc
                                    errorMessage = null
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = PurpleBrand, modifier = Modifier.size(13.dp))
                                    Text(tplTitle, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Icon Selector
        item {
            Text("ICON", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(listOf(
                    "clipboard" to Icons.Default.CheckCircle,
                    "work" to Icons.Default.Work,
                    "book" to Icons.Default.MenuBook,
                    "cart" to Icons.Default.ShoppingCart,
                    "star" to Icons.Default.Star,
                    "fitness" to Icons.Default.FitnessCenter,
                    "food" to Icons.Default.Restaurant
                )) { (iconId, iconVec) ->
                    val isSelected = selectedIcon == iconId
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PurpleBrand else CardInner)
                            .border(1.dp, if (isSelected) Color.White else CardBorder, RoundedCornerShape(12.dp))
                            .clickable { selectedIcon = iconId },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconVec, null, tint = if (isSelected) Color.White else TextMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Title and Description Inputs
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TASK INFO", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorMessage = null },
                    label = { Text("Task Title *") },
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
                    maxLines = 3
                )
            }
        }

        // Priority Chips: Low | Medium | High
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PRIORITY", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        "Low" to BluePriority,
                        "Medium" to YellowPriority,
                        "High" to RedPriority
                    ).forEach { (p, col) ->
                        val isSelected = priority == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) col.copy(alpha = 0.25f) else CardInner)
                                .border(1.5.dp, if (isSelected) col else CardBorder, RoundedCornerShape(12.dp))
                                .clickable { priority = p }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(p, color = if (isSelected) col else TextMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Dates & Time
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DATES & TIME", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                // Quick Due Date Presets
                Text("QUICK DUE DATE", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                val today = LocalDate.now()
                val datePresets = listOf(
                    "Today" to today.toString(),
                    "Tomorrow" to today.plusDays(1).toString(),
                    "Weekend" to today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).toString(),
                    "Next Week" to today.plusWeeks(1).with(DayOfWeek.MONDAY).toString()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    datePresets.forEach { (label, dateVal) ->
                        val isChosen = dueDate == dateVal
                        Surface(
                            color = if (isChosen) PurpleBrand else CardInner,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isChosen) PurpleBrand else CardBorder),
                            modifier = Modifier.weight(1f).clickable { dueDate = dateVal }
                        ) {
                            Text(
                                label,
                                color = if (isChosen) Color.White else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due Date (YYYY-MM-DD)") },
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
                        value = dueTime,
                        onValueChange = { dueTime = it },
                        label = { Text("Time (HH:mm)") },
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

                // Quick Time Presets
                val timePresets = listOf("09:00", "12:00", "15:00", "17:00", "20:00")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    timePresets.forEach { t ->
                        val isChosen = dueTime == t
                        Surface(
                            color = if (isChosen) PurpleBrand else CardInner,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isChosen) PurpleBrand else CardBorder),
                            modifier = Modifier.weight(1f).clickable { dueTime = t }
                        ) {
                            Text(
                                t,
                                color = if (isChosen) Color.White else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Repeat & Category
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("REPEAT & CATEGORY", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = repeatType,
                    onValueChange = { repeatType = it },
                    label = { Text("Repeat (Does not repeat, Daily, Weekly)") },
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
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (Optional)") },
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
        }

        if (errorMessage != null) {
            item {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

// ==========================================
// 6. TASK DETAILS VIEW
// ==========================================

@Composable
private fun TaskDetailsView(
    task: TaskItem,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onRefresh: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newSubtaskText by remember { mutableStateOf("") }
    var showAddSubtaskInput by remember { mutableStateOf(false) }

    val priorityColor = when (task.priority) {
        "High" -> RedPriority
        "Low" -> BluePriority
        else -> YellowPriority
    }

    val completedSubtasks = task.subtasks.count { it.isCompleted }
    val subtasksTotal = task.subtasks.size
    val subtaskFraction = if (subtasksTotal > 0) completedSubtasks.toFloat() / subtasksTotal.toFloat() else 0f

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
                    Icon(Icons.Default.ArrowBack, null, tint = TextWhite)
                }

                Text("Task Details", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = TextWhite)
                    }

                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit Task") },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                showMenu = false
                                TaskRepository.duplicateTask(context, task.id)
                                onRefresh()
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Task", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; showDeleteConfirm = true },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }

        // Header Card: Checkbox + Title + Priority
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (task.isCompleted) GreenBrand else Color.Transparent)
                                .border(2.5.dp, if (task.isCompleted) GreenBrand else priorityColor, CircleShape)
                                .clickable {
                                    TaskRepository.toggleTaskCompletion(context, task.id)
                                    onRefresh()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.isCompleted) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        }

                        Text(
                            task.title,
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(priorityColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("${task.priority} Priority", color = priorityColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (task.dueTime != null) {
                            Text("${task.dueDate}, ${task.dueTime}", color = TextMuted, fontSize = 12.sp)
                        } else {
                            Text(task.dueDate, color = TextMuted, fontSize = 12.sp)
                        }
                    }

                    if (task.description.isNotBlank()) {
                        Text(task.description, color = TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }

        // Subtasks Card with Progress
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subtasks ($completedSubtasks/$subtasksTotal)", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("${(subtaskFraction * 100).toInt()}%", color = CyanBrand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    LinearProgressIndicator(
                        progress = { subtaskFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = CyanBrand,
                        trackColor = CardInner
                    )

                    // Subtask items
                    task.subtasks.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    TaskRepository.toggleSubtaskCompletion(context, task.id, sub.id)
                                    onRefresh()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                if (sub.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                null,
                                tint = if (sub.isCompleted) CyanBrand else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                sub.title,
                                color = if (sub.isCompleted) TextMuted else TextWhite,
                                fontSize = 14.sp,
                                textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            )
                        }
                    }

                    // Add Subtask row
                    if (showAddSubtaskInput) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newSubtaskText,
                                onValueChange = { newSubtaskText = it },
                                placeholder = { Text("Subtask title...", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (newSubtaskText.isNotBlank()) {
                                        TaskRepository.addSubtask(context, task.id, newSubtaskText)
                                        newSubtaskText = ""
                                        showAddSubtaskInput = false
                                        onRefresh()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand)
                            ) {
                                Text("Add")
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { showAddSubtaskInput = true },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, null, tint = PurpleBrand, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Subtask", color = PurpleBrand, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Action Rows: Edit Task, Duplicate, Reminder, Delete
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    TaskActionRow(Icons.Default.Edit, "Edit Task", onClick = onEdit)
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    TaskActionRow(Icons.Default.ContentCopy, "Duplicate Task", onClick = {
                        TaskRepository.duplicateTask(context, task.id)
                        onRefresh()
                    })
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    TaskActionRow(Icons.Default.Alarm, "Task Reminder Settings", onClick = onOpenReminderSettings)
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    TaskActionRow(
                        Icons.Default.Delete,
                        "Delete Task",
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = { showDeleteConfirm = true }
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Task?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${task.title}'? All scheduled reminders will be cancelled.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        TaskRepository.deleteTask(context, task.id)
                        showDeleteConfirm = false
                        onDeleted()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBackground
        )
    }
}

@Composable
private fun TaskActionRow(
    icon: ImageVector,
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
// 7. TASK CALENDAR VIEW (SCREEN 5)
// ==========================================

@Composable
private fun TaskCalendarView(
    tasks: List<TaskItem>,
    onBack: () -> Unit,
    onTaskClick: (TaskItem) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val daysInMonth = currentYearMonth.lengthOfMonth()
    val firstDayOfWeek = currentYearMonth.atDay(1).dayOfWeek.value

    val tasksOnSelectedDate = remember(tasks, selectedDate) {
        tasks.filter { it.dueDate == selectedDate.toString() }
    }

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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = TextWhite)
                }
                Text("Tasks Calendar", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Calendar Month Picker
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                    // Days grid
                    val totalCells = ((firstDayOfWeek - 1) + daysInMonth + 6) / 7 * 7
                    val rows = totalCells / 7

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                for (c in 0..6) {
                                    val cellIndex = r * 7 + c
                                    val dayNum = cellIndex - (firstDayOfWeek - 1) + 1
                                    if (dayNum in 1..daysInMonth) {
                                        val date = currentYearMonth.atDay(dayNum)
                                        val hasTasks = tasks.any { it.dueDate == date.toString() }
                                        val isSelected = date == selectedDate

                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) PurpleBrand else if (hasTasks) CardInner else Color.Transparent)
                                                .clickable { selectedDate = date },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                dayNum.toString(),
                                                color = if (isSelected) Color.White else TextWhite,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected || hasTasks) FontWeight.Bold else FontWeight.Normal
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

        // Scheduled Tasks on Selected Date Header
        item {
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (tasksOnSelectedDate.isEmpty()) {
            item {
                Text("No tasks scheduled for this date.", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            items(tasksOnSelectedDate, key = { it.id }) { task ->
                TaskCardRow(task = task, onClick = { onTaskClick(task) }, onToggle = {
                    TaskRepository.toggleTaskCompletion(context, task.id)
                    onRefresh()
                })
            }
        }
    }
}

// ==========================================
// 8. TASK REMINDER SETTINGS VIEW (SCREEN 6)
// ==========================================

@Composable
private fun TaskReminderSettingsView(
    task: TaskItem,
    onBack: () -> Unit,
    onSave: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var reminderEnabled by remember { mutableStateOf(task.reminderEnabled) }
    var reminderTime by remember { mutableStateOf(task.reminderTime ?: "16:30") }
    var notificationMessage by remember { mutableStateOf(task.notificationMessage) }
    var snoozeMinutes by remember { mutableIntStateOf(task.snoozeMinutes) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextWhite) }
                    Text("Reminder Settings", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val updated = task.copy(
                            reminderEnabled = reminderEnabled,
                            reminderTime = reminderTime,
                            notificationMessage = notificationMessage.trim(),
                            snoozeMinutes = snoozeMinutes
                        )
                        onSave(updated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Reminder", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                            label = { Text("Reminder Time (HH:mm)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = notificationMessage,
                            onValueChange = { notificationMessage = it },
                            label = { Text("Notification Message (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text("SNOOZE OPTIONS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15, 30).forEach { mins ->
                                val isSelected = snoozeMinutes == mins
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) PurpleBrand else CardInner)
                                        .clickable { snoozeMinutes = mins }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$mins min", color = if (isSelected) Color.White else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
