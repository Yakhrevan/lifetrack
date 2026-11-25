package com.yakhrevan.lifetrack

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
private fun V12Header(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back to previous screen" }) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
        }
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun V12EntryCard(
    title: String, subtitle: String, completed: Boolean,
    onClick: () -> Unit, action: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open $title", onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null) action()
            else if (completed) Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun TodayPlannerScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpen: (String, String) -> Unit,
    onQuickAdd: () -> Unit
) {
    val context = LocalContext.current
    var day by remember { mutableStateOf(LocalDate.now()) }
    var refresh by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("All") }
    val entries = remember(day, refresh) {
        ProductivityEngine.entries(day,
            TaskRepository.getTasks(context), CalendarStorage.load(context),
            HabitRoutineRepository.getHabits(context), HabitRoutineRepository.getCompletions(context),
            HabitRoutineRepository.getRoutines(context), HabitRoutineRepository.getRoutineSessions(context),
            StudyRepository.loadSessions(context), StudyRepository.loadTopics(context))
    }
    val shown = entries.filter { filter == "All" || (filter == "Pending" && !it.completed) || (filter == "Done" && it.completed) }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 90.dp)) {
        item { V12Header("Today Planner", "Tasks, events, habits, routines and study in one place", onBack) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { day = day.minusDays(1) }) { Text("‹") }
                Text(day.format(DateTimeFormatter.ofPattern("EEE, dd MMM")), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { day = day.plusDays(1) }) { Text("›") }
                TextButton(onClick = { day = LocalDate.now(); refresh++ }) { Text("Today") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "Pending", "Done").forEach { choice ->
                    FilterChip(selected = filter == choice, onClick = { filter = choice }, label = { Text(choice) })
                }
            }
        }
        item {
            Text("${entries.count { it.completed }} of ${entries.size} completed", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { if (entries.isEmpty()) 0f else entries.count { it.completed }.toFloat() / entries.size },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (shown.isEmpty()) item {
            Text(if (entries.isEmpty()) "Nothing planned for this day. Add your first item." else "No items in this filter.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onQuickAdd) { Text("Quick Add") }
        }
        items(shown, key = { it.kind + it.itemId }) { entry ->
            V12EntryCard(entry.title,
                listOf(entry.kind, entry.time.ifBlank { "Any time" }, if (entry.completed) "Done" else "Pending").joinToString(" · "),
                entry.completed, onClick = { onOpen(entry.kind, entry.itemId) },
                action = if (entry.kind == "Tasks") {{
                    Checkbox(checked = entry.completed, onCheckedChange = {
                        TaskRepository.toggleTaskCompletion(context, entry.itemId)
                        refresh++
                        LifeTrackWidgetUpdater.updateAll(context)
                    }, modifier = Modifier.semantics { contentDescription = "Mark ${entry.title} ${if (entry.completed) "pending" else "complete"}" })
                }} else null)
        }
    }
}

@Composable
fun NotificationInboxScreen(
    modifier: Modifier = Modifier, onBack: () -> Unit, onOpen: (String, String) -> Unit
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("All") }
    val history = remember(refresh) { NotificationHistoryRepository.read(context) }
    val queued = remember(refresh) { ReminderQueueProjector.project(
        java.time.LocalDateTime.now(), TaskRepository.getTasks(context), CalendarStorage.load(context),
        HabitRoutineRepository.getHabits(context), HabitRoutineRepository.getRoutines(context),
        HabitRoutineRepository.getCompletions(context), HabitRoutineRepository.getRoutineSessions(context)
    ) }
    val records = (queued + history).distinctBy { "${it.module}:${it.itemId}:${it.state}" }
        .filter { filter == "All" || it.state == filter.uppercase() }
        .sortedByDescending { it.timestampMs }.take(250)

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 90.dp)) {
        item { V12Header("Notification Center", "Upcoming, missed, delivered and snoozed reminders", onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("All", "Missed", "Snoozed", "Upcoming").forEach { option ->
                    FilterChip(selected = option == filter, onClick = { filter = option },
                        label = { Text(option, fontSize = 11.sp) })
                }
            }
        }
        item { OutlinedButton(onClick = { refresh++ }) { Text("Refresh") } }
        if (records.isEmpty()) item {
            Text("No reminders in this view. Upcoming scheduled tasks will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(records, key = { "${it.module}:${it.itemId}:${it.state}:${it.timestampMs}" }) { record ->
            V12EntryCard(record.title, "${record.module} · ${record.state.lowercase().replaceFirstChar { it.uppercase() }}",
                record.state == "DONE", onClick = { onOpen(record.module, record.itemId) })
        }
    }
}

@Composable
fun ProductivityInsightsScreen(modifier: Modifier = Modifier, onBack: () -> Unit, onOpenPlanner: () -> Unit) {
    val context = LocalContext.current
    val summary = remember {
        ProductivityEngine.weeklySummary(LocalDate.now(), TaskRepository.getTasks(context),
            HabitRoutineRepository.getHabits(context), HabitRoutineRepository.getCompletions(context),
            StudyRepository.loadSessions(context), NotificationHistoryRepository.read(context))
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 90.dp)) {
        item { V12Header("Productivity Insights", "Actual activity from the last 7 days", onBack) }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Completion rate", style = MaterialTheme.typography.titleMedium)
                    Text(if (summary.planned == 0) "No planned items yet" else "${summary.completionPercent}%",
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { summary.completionPercent / 100f }, Modifier.fillMaxWidth())
                    Text("${summary.completed} / ${summary.planned} planned task + habit occurrences", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { V12EntryCard("Study focus", "${summary.studyMinutes} minutes in completed sessions", true, onClick = onOpenPlanner) }
        item { V12EntryCard("Overdue tasks", "${summary.overdue} incomplete tasks", summary.overdue == 0, onClick = onOpenPlanner) }
        item { V12EntryCard("Missed reminder entries", "${summary.missedAlerts} logged in last 7 days", summary.missedAlerts == 0, onClick = onOpenPlanner) }
        item { Text("Scores use saved tasks, habits and sessions; no fabricated data or AI predictions.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Button(onClick = onOpenPlanner, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Today, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open Today Planner")
        } }
    }
}
