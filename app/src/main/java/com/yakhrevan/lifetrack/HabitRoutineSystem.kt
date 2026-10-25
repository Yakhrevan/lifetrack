package com.yakhrevan.lifetrack

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val HABIT_PREFS = "lifetrack_habit_routines"

@Composable
internal fun HabitRoutineSystem(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(HABIT_PREFS, Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("Daily") }
    var weekdays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var weeklyTarget by remember { mutableStateOf("3") }
    var reminderTime by remember { mutableStateOf("09:00") }
    var reminderEnabled by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val habits = remember(refresh) { HabitRoutineEngine.decodeHabits(prefs.getString("habits", "[]")) }
    val history = remember(refresh) { decodeHabitHistory(prefs.getString("history", "{}")) }
    val today = LocalDate.now()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) scheduleHabitNotification(context, 0) }

    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("HABITS & ROUTINES", color = LifeTrackColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Schedules, streaks, history and completion rate", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                }
                TextButton(onClick = { addOpen = true; name = ""; error = null }) { Icon(Icons.Default.Add, null); Text("Add") }
            }
            if (habits.isEmpty()) Text("Add a habit to start tracking your routine.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
            habits.forEach { habit ->
                val completedDates = history.filterValues { it[habit.id] == "completed" }.keys
                val current = HabitRoutineEngine.streak(habit, completedDates, today)
                val longest = HabitRoutineEngine.longestStreak(habit, completedDates)
                val historyStart = today.minusDays(27)
                val rate = HabitRoutineEngine.completionRate(habit, history.mapValues { it.value[habit.id] ?: "" }, historyStart, today)
                val status = history[today]?.get(habit.id) ?: ""
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(habit.name, color = LifeTrackColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("${habit.frequency} · current streak $current · longest $longest", color = LifeTrackColors.TextSecondary, fontSize = 10.sp)
                        }
                        Text("${rate.toInt()}%", color = LifeTrackColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("completed", "skipped", "missed").forEach { state ->
                            FilterChip(selected = status == state, onClick = {
                                history.getOrPut(today) { mutableMapOf() }[habit.id] = state
                                saveHabitHistory(context, history); refresh++
                            }, label = { Text(state.replaceFirstChar { it.uppercase() }, fontSize = 10.sp) })
                        }
                    }
                    Text("28-day completion heatmap · tap a date to cycle status", color = LifeTrackColors.TextSecondary, fontSize = 10.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items((27 downTo 0).map { today.minusDays(it.toLong()) }) { day ->
                            val dayStatus = history[day]?.get(habit.id) ?: ""
                            val scheduled = HabitRoutineEngine.isScheduled(habit, day)
                            val color = when (dayStatus) {
                                "completed" -> LifeTrackColors.Primary
                                "skipped" -> Color(0xFF8993A7)
                                "missed" -> LifeTrackColors.Orange
                                else -> if (scheduled && day.isBefore(today)) LifeTrackColors.Orange.copy(alpha = .28f) else LifeTrackColors.TextSecondary.copy(alpha = .10f)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(Modifier.size(18.dp).background(color, RoundedCornerShape(4.dp)).clickable {
                                    val next = when (dayStatus) { "completed" -> "skipped"; "skipped" -> "missed"; "missed" -> ""; else -> "completed" }
                                    if (next.isBlank()) history[day]?.remove(habit.id) else history.getOrPut(day) { mutableMapOf() }[habit.id] = next
                                    saveHabitHistory(context, history); refresh++
                                })
                                Text(day.dayOfMonth.toString(), color = LifeTrackColors.TextSecondary, fontSize = 8.sp)
                            }
                        }
                    }
                    LinearProgressIndicator(progress = { (rate / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = LifeTrackColors.Primary, trackColor = LifeTrackColors.Background)
                    TextButton(onClick = {
                        val savedRows = runCatching { JSONArray(prefs.getString("habits", "[]")) }.getOrDefault(JSONArray())
                        val rows = JSONArray()
                        for (index in 0 until savedRows.length()) {
                            val row = savedRows.optJSONObject(index) ?: continue
                            if (row.optString("id") != habit.id) rows.put(row)
                        }
                        prefs.edit().putString("habits", rows.toString()).apply(); refresh++
                    }) { Text("Remove habit") }
                }
                HorizontalDivider(color = LifeTrackColors.Background)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Alarm, null, tint = LifeTrackColors.Blue)
                Text("Reminder schedules use local notifications. Snooze moves a reminder 10 minutes later.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else scheduleHabitNotification(context, 0)
                }) { Text("Enable / test") }
                TextButton(onClick = { scheduleHabitNotification(context, 10) }) { Text("Snooze 10 min") }
            }
        }
    }
    if (addOpen) AlertDialog(
        onDismissRequest = { addOpen = false },
        title = { Text("Add habit or routine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("Habit name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Daily", "Selected weekdays", "Weekly frequency").forEach { item ->
                        FilterChip(selected = frequency == item, onClick = { frequency = item }, label = { Text(item, fontSize = 9.sp) })
                    }
                }
                if (frequency == "Selected weekdays") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) { day ->
                            FilterChip(selected = day.value in weekdays, onClick = { weekdays = if (day.value in weekdays) weekdays - day.value else weekdays + day.value }, label = { Text(day.name.take(3), fontSize = 9.sp) })
                        }
                    }
                }
                if (frequency == "Weekly frequency") OutlinedTextField(value = weeklyTarget, onValueChange = { weeklyTarget = it.filter(Char::isDigit).take(1) }, label = { Text("Times per week (1–7)") }, singleLine = true)
                OutlinedTextField(value = reminderTime, onValueChange = { reminderTime = it.take(5) }, label = { Text("Reminder time · HH:mm") }, singleLine = true)
                FilterChip(selected = reminderEnabled, onClick = { reminderEnabled = !reminderEnabled }, label = { Text(if (reminderEnabled) "Reminder enabled" else "Enable reminder") })
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) { error = "Enter a habit name."; return@Button }
                if (frequency == "Selected weekdays" && weekdays.isEmpty()) { error = "Choose at least one weekday."; return@Button }
                val target = weeklyTarget.toIntOrNull() ?: 3
                if (frequency == "Weekly frequency" && target !in 1..7) { error = "Weekly target must be 1 to 7."; return@Button }
                val parsedTime = runCatching { LocalTime.parse(reminderTime) }.getOrNull()
                if (reminderEnabled && parsedTime == null) { error = "Enter reminder time as HH:mm."; return@Button }
                val row = HabitRoutineEngine.Habit(UUID.randomUUID().toString(), name.trim(), frequency, weekdays, target)
                val rows = JSONArray(prefs.getString("habits", "[]")).put(HabitRoutineEngine.encodeHabit(row, reminderEnabled, parsedTime?.toString() ?: "09:00"))
                prefs.edit().putString("habits", rows.toString()).apply()
                if (reminderEnabled) scheduleHabitAtTime(context, parsedTime!!)
                addOpen = false; refresh++
            }) { Text("Save habit") }
        },
        dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Cancel") } }
    )
}

private fun decodeHabitHistory(raw: String?): MutableMap<LocalDate, MutableMap<String, String>> = runCatching {
    val root = JSONObject(raw ?: "{}")
    val output = mutableMapOf<LocalDate, MutableMap<String, String>>()
    val keys = root.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
        val row = root.optJSONObject(key) ?: continue
        val values = mutableMapOf<String, String>()
        val ids = row.keys()
        while (ids.hasNext()) { val id = ids.next(); values[id] = row.optString(id) }
        output[date] = values
    }
    output
}.getOrDefault(mutableMapOf())

private fun saveHabitHistory(context: Context, history: Map<LocalDate, Map<String, String>>) {
    val root = JSONObject()
    history.forEach { (date, values) -> val row = JSONObject(); values.forEach { (id, status) -> row.put(id, status) }; root.put(date.toString(), row) }
    context.getSharedPreferences(HABIT_PREFS, Context.MODE_PRIVATE).edit().putString("history", root.toString()).apply()
}

private fun scheduleHabitNotification(context: Context, minutes: Long) {
    val request = OneTimeWorkRequestBuilder<HabitReminderWorker>().setInitialDelay(minutes, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniqueWork("lifetrack-habit-reminder", ExistingWorkPolicy.REPLACE, request)
}

private fun scheduleHabitAtTime(context: Context, time: LocalTime) {
    val now = java.time.LocalDateTime.now()
    var next = now.toLocalDate().atTime(time)
    if (!next.isAfter(now)) next = next.plusDays(1)
    val delay = java.time.Duration.between(now, next).toMillis().coerceAtLeast(1000L)
    val request = OneTimeWorkRequestBuilder<HabitReminderWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork("lifetrack-habit-reminder", ExistingWorkPolicy.REPLACE, request)
}
