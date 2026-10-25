package com.yakhrevan.lifetrack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
private fun CalendarMetric(title: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.08f)), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = LifeTrackColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = LifeTrackColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun ConsistencyCalendarScreen(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
    onEditEntry: (LogEntry) -> Unit = {},
    onDeleteEntry: (LogEntry) -> Unit = {},
    onAddEntry: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val habitCompletions = remember(logs) { HabitRoutineRepository.getCompletions(context) }
    val routineSessions = remember(logs) { HabitRoutineRepository.getRoutineSessions(context) }
    val completedHabitRecords = habitCompletions.filter { it.status == "completed" }
    val completedRoutineRecords = routineSessions.filter { it.isCompleted }
    val habitNames = remember(logs) { HabitRoutineRepository.getHabits(context, includeArchived = true).associateBy { it.id } }
    val routineNames = remember(logs) { HabitRoutineRepository.getRoutines(context, includeArchived = true).associateBy { it.id } }
    var displayedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var displayedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    val monthCalendar = Calendar.getInstance().apply { set(Calendar.YEAR, displayedYear); set(Calendar.MONTH, displayedMonth); set(Calendar.DAY_OF_MONTH, 1) }
    val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstWeekday = (monthCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(monthCalendar.time)
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val activityByDate = remember(logs, habitCompletions, routineSessions) { logs.groupBy { it.date } }
    val selectedLogs = activityByDate[selectedDate].orEmpty()
    val selectedHabits = completedHabitRecords.filter { it.date == selectedDate }
    val selectedRoutines = completedRoutineRecords.filter { it.date == selectedDate }
    val monthPrefix = String.format(Locale.US, "%04d-%02d", displayedYear, displayedMonth + 1)
    val monthLogs = remember(logs, monthPrefix) { logs.filter { it.date.startsWith(monthPrefix) } }
    val monthHabitRecords = completedHabitRecords.filter { it.date.startsWith(monthPrefix) }
    val monthRoutineRecords = completedRoutineRecords.filter { it.date.startsWith(monthPrefix) }
    val activeDays = (monthLogs.map { it.date } + monthHabitRecords.map { it.date } + monthRoutineRecords.map { it.date }).distinct().size
    val monthStudyMinutes = monthLogs.filter { it.category == "Study" }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val monthWaterMl = monthLogs.filter { it.category == "Water" }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val selectedStudyMinutes = selectedLogs.filter { it.category == "Study" }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val selectedWaterMl = selectedLogs.filter { it.category == "Water" }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val selectedExpenses = selectedLogs.filter { it.category == "Expense" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val selectedIncome = selectedLogs.filter { it.category == "Income" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Consistency Calendar", color = LifeTrackColors.TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Your real activity, day by day.", color = LifeTrackColors.TextSecondary, fontSize = 13.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val c = Calendar.getInstance().apply { set(Calendar.YEAR, displayedYear); set(Calendar.MONTH, displayedMonth); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -1) }
                            displayedYear = c.get(Calendar.YEAR); displayedMonth = c.get(Calendar.MONTH)
                            selectedDate = dateFormat.format(c.time)
                        }) { Icon(Icons.Default.ChevronLeft, "Previous month", tint = LifeTrackColors.Primary) }
                        Text(monthLabel, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = LifeTrackColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            val c = Calendar.getInstance().apply { set(Calendar.YEAR, displayedYear); set(Calendar.MONTH, displayedMonth); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, 1) }
                            displayedYear = c.get(Calendar.YEAR); displayedMonth = c.get(Calendar.MONTH)
                            selectedDate = dateFormat.format(c.time)
                        }) { Icon(Icons.Default.ChevronRight, "Next month", tint = LifeTrackColors.Primary) }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Text(it, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = LifeTrackColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    val cellCount = ((firstWeekday + daysInMonth + 6) / 7) * 7
                    (0 until cellCount).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            week.forEach { index ->
                                val day = index - firstWeekday + 1
                                if (day in 1..daysInMonth) {
                                    val date = dateFormat.format(Calendar.getInstance().apply { set(Calendar.YEAR, displayedYear); set(Calendar.MONTH, displayedMonth); set(Calendar.DAY_OF_MONTH, day) }.time)
                                    val dayLogs = activityByDate[date].orEmpty()
                                    val dayHabitCount = completedHabitRecords.count { it.date == date }
                                    val dayRoutineCount = completedRoutineRecords.count { it.date == date }
                                    val activityCount = dayLogs.size + dayHabitCount + dayRoutineCount
                                    val isSelected = date == selectedDate
                                    val intensity = when { activityCount == 0 -> 0; activityCount >= 5 -> 3; activityCount >= 3 -> 2; else -> 1 }
                                    val fill = when (intensity) { 0 -> Color(0xFFF0F3FA); 1 -> Color(0xFFDCE6FF); 2 -> Color(0xFF9FB8FF); else -> Color(0xFF315CFF) }
                                    Column(
                                        Modifier.weight(1f).aspectRatio(0.82f).background(if (isSelected) Color(0xFF315CFF) else fill, RoundedCornerShape(12.dp)).clickable { selectedDate = date }.padding(vertical = 5.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(day.toString(), color = if (isSelected) Color.White else LifeTrackColors.TextPrimary, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                                        if (activityCount > 0) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            repeat(minOf(activityCount, 3)) { dot ->
                                                Box(Modifier.size(4.dp).background(if (isSelected) Color.White else listOf(Color(0xFF315CFF), Color(0xFF20B982), Color(0xFFF05B87))[dot], CircleShape))
                                            }
                                        } else Spacer(Modifier.height(4.dp))
                                    }
                                } else Spacer(Modifier.weight(1f).aspectRatio(0.82f))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(Color(0xFFF0F3FA), Color(0xFFDCE6FF), Color(0xFF9FB8FF), Color(0xFF315CFF)).forEachIndexed { i, c ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(9.dp).background(c, RoundedCornerShape(3.dp)))
                                Text(listOf("No logs", "1–2", "3–4", "5+")[i], color = LifeTrackColors.TextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.SurfaceRaised), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MONTH SUMMARY", color = LifeTrackColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("$activeDays active days", color = LifeTrackColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CalendarMetric("Study", if (monthStudyMinutes >= 60) "${monthStudyMinutes / 60}h ${monthStudyMinutes % 60}m" else "$monthStudyMinutes min", Color(0xFF315CFF), Modifier.weight(1f))
                        CalendarMetric("Water", if (monthWaterMl >= 1000) String.format(Locale.US, "%.1f L", monthWaterMl / 1000f) else "$monthWaterMl ml", Color(0xFF2498F3), Modifier.weight(1f))
                    }
                    Text("${monthLogs.size} saved entries · ${monthHabitRecords.size} habit completions · ${monthRoutineRecords.size} completed routines in $monthLabel", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(dateFormat.parse(selectedDate) ?: Date()), color = LifeTrackColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CalendarMetric("Study", if (selectedStudyMinutes >= 60) "${selectedStudyMinutes / 60}h ${selectedStudyMinutes % 60}m" else "$selectedStudyMinutes min", Color(0xFF315CFF), Modifier.weight(1f))
                        CalendarMetric("Water", if (selectedWaterMl >= 1000) String.format(Locale.US, "%.1f L", selectedWaterMl / 1000f) else "$selectedWaterMl ml", Color(0xFF2498F3), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CalendarMetric("Income", "₹" + String.format(Locale.US, "%.0f", selectedIncome), Color(0xFF20B982), Modifier.weight(1f))
                        CalendarMetric("Expenses", "₹" + String.format(Locale.US, "%.0f", selectedExpenses), Color(0xFFFF9B43), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CalendarMetric("Habits", "${selectedHabits.size} done", Color(0xFF20B982), Modifier.weight(1f))
                        CalendarMetric("Routines", "${selectedRoutines.size} done", Color(0xFFA855F7), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${selectedLogs.size} entries · ${selectedHabits.size} habits · ${selectedRoutines.size} routines", Modifier.weight(1f), color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                        OutlinedButton(onClick = { onAddEntry(selectedDate) }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Add entry")
                        }
                    }
                    if (selectedLogs.isEmpty() && selectedHabits.isEmpty() && selectedRoutines.isEmpty()) Text("No saved activity, habit completion, or routine completion on this date.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                    selectedHabits.forEach { record ->
                        Text("✓ ${habitNames[record.habitId]?.name ?: "Habit"} · completed", color = Color(0xFF20B982), fontSize = 12.sp)
                    }
                    selectedRoutines.forEach { record ->
                        Text("✓ ${record.routineName.ifBlank { routineNames[record.routineId]?.name ?: "Routine" }} · completed", color = Color(0xFFA855F7), fontSize = 12.sp)
                    }
                    selectedLogs.forEach { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(34.dp).background(Color(0xFFEAF0FF), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text(when (entry.category) { "Water" -> "💧"; "Expense", "Income" -> "₹"; "Study" -> "📘"; "Care", "Face routine" -> "✓"; else -> "•" }, color = LifeTrackColors.Primary, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(entry.category, color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                            }
                            Text(when (entry.category) { "Expense" -> "−₹${entry.amount}"; "Income" -> "+₹${entry.amount}"; "Water" -> "${entry.amount} ml"; "Study", "Art" -> "${entry.amount} min"; else -> entry.amount }, color = LifeTrackColors.Primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            IconButton(onClick = { onEditEntry(entry) }) { Icon(Icons.Default.Edit, contentDescription = "Edit activity", tint = LifeTrackColors.Primary, modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { onDeleteEntry(entry) }) { Icon(Icons.Default.Delete, contentDescription = "Delete activity", tint = Color(0xFFF05B87), modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
    }
}
