package com.yakhrevan.lifetrack

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import kotlinx.coroutines.delay

/**
 * Study (category "Study") and creative (category "Art") tracker.
 * Subjects/projects, timer modes and durations are entirely user-defined.
 * Timer state is stored as timestamps so it survives navigation and app restarts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudyTrackerScreen(
    category: String,
    logs: List<LogEntry>,
    today: String,
    onBack: () -> Unit,
    onSaveSession: (title: String, minutes: Int, note: String) -> Unit
) {
    val isStudy = category == "Study"
    if (isStudy) {
        StudyScreen(
            logs = logs,
            today = today,
            onBack = onBack,
            onSaveSession = onSaveSession
        )
        return
    }

    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("lifetrack_study", Context.MODE_PRIVATE) }
    val p = category.lowercase()
    val defaultTitle = if (isStudy) "Study session" else "Creative session"
    val noun = if (isStudy) "subject" else "project"

    var tab by remember { mutableStateOf("Timer") }
    var statsPeriod by remember { mutableStateOf("Monthly") }
    var projects by remember { mutableStateOf(ProjectLogic.decode(prefs.getString("${p}_projects", null))) }
    var selectedId by remember { mutableStateOf(prefs.getString("${p}_selected", "") ?: "") }
    var customPresets by remember { mutableStateOf(DurationLogic.decodePresets(prefs.getString("${p}_presets", null))) }
    var breakMin by remember { mutableIntStateOf(prefs.getInt("${p}_break", 5).coerceIn(1, 720)) }
    var focusMin by remember { mutableIntStateOf(prefs.getInt("${p}_focus", 25).coerceIn(1, 720)) }
    var timer by remember {
        mutableStateOf(
            TimerState(
                mode = runCatching { TimerMode.valueOf(prefs.getString("${p}_mode", "STOPWATCH") ?: "STOPWATCH") }.getOrDefault(TimerMode.STOPWATCH),
                targetMin = prefs.getInt("${p}_target", 25).coerceIn(1, 720),
                running = prefs.getBoolean("${p}_running", false),
                startedAt = prefs.getLong("${p}_started", 0L),
                accMs = prefs.getLong("${p}_acc", 0L),
                onBreak = prefs.getBoolean("${p}_onbreak", false),
                active = prefs.getBoolean("${p}_active", false)
            )
        )
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun saveProjects(list: List<Project>) { projects = list; prefs.edit().putString("${p}_projects", ProjectLogic.encode(list)).apply() }
    fun persist(t: TimerState) {
        timer = t
        prefs.edit().putString("${p}_mode", t.mode.name).putInt("${p}_target", t.targetMin)
            .putBoolean("${p}_running", t.running).putLong("${p}_started", t.startedAt).putLong("${p}_acc", t.accMs)
            .putBoolean("${p}_onbreak", t.onBreak).putBoolean("${p}_active", t.active).apply()
    }
    fun savePresets(list: List<Int>) { customPresets = list; prefs.edit().putString("${p}_presets", DurationLogic.encodePresets(list)).apply() }

    val activeProjects = projects.filter { !it.archived }
    val selected = activeProjects.firstOrNull { it.id == selectedId }

    // Tick while running; auto-pause when a countdown/focus block completes.
    LaunchedEffect(timer.running) {
        while (timer.running) { now = System.currentTimeMillis(); delay(500) }
        now = System.currentTimeMillis()
    }
    LaunchedEffect(now, timer.running) {
        if (timer.running && timer.isDone(now)) persist(timer.pause(now))
    }

    var showFinish by remember { mutableStateOf(false) }
    var breakAfterSave by remember { mutableStateOf(false) }
    var finishMinutes by remember { mutableStateOf("") }
    var finishNote by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Project?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Project?>(null) }
    var newName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var customMinutes by remember { mutableStateOf("") }
    var durationError by remember { mutableStateOf<String?>(null) }

    val todayMin = logs.filter { it.date == today }.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
    val studyGoal = Goals.studyTarget(context)

    if (showFinish) {
        val minutes = DurationLogic.parse(finishMinutes)
        AlertDialog(
            onDismissRequest = { showFinish = false },
            title = { Text("Save session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${selected?.name ?: defaultTitle}. Correct the minutes if needed.", fontSize = 12.sp)
                    OutlinedTextField(finishMinutes, { finishMinutes = it.filter(Char::isDigit) }, label = { Text("Minutes (1–720)") },
                        isError = minutes == null, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(finishNote, { finishNote = it.take(200) }, label = { Text("Note (optional)") })
                }
            },
            confirmButton = {
                TextButton(enabled = minutes != null, onClick = {
                    onSaveSession(selected?.name ?: defaultTitle, minutes!!, finishNote.trim())
                    if (breakAfterSave) persist(TimerState(TimerMode.POMODORO, breakMin, onBreak = true).start(System.currentTimeMillis()))
                    else persist(timer.reset().copy(targetMin = focusMin))
                    breakAfterSave = false; showFinish = false; finishNote = ""
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showFinish = false }) { Text("Cancel") } }
        )
    }
    renaming?.let { r ->
        val ok = ProjectLogic.rename(projects, r.id, renameText) != null
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename $noun") },
            text = { OutlinedTextField(renameText, { renameText = it.take(ProjectLogic.MAX_NAME) }, singleLine = true, isError = !ok,
                supportingText = { if (!ok) Text("Name is empty or already used") }) },
            confirmButton = { TextButton(enabled = ok, onClick = { saveProjects(ProjectLogic.rename(projects, r.id, renameText)!!); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${d.name}”?") },
            text = { Text("Only this $noun is removed from your list. Sessions already saved under this name stay in your history. To hide it without deleting, archive it instead.") },
            confirmButton = { TextButton(onClick = {
                saveProjects(ProjectLogic.delete(projects, d.id))
                if (selectedId == d.id) { selectedId = ""; prefs.edit().putString("${p}_selected", "").apply() }
                deleting = null
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = LifeTrackColors.Primary) }
                Column(Modifier.weight(1f)) {
                    Text(if (isStudy) "Study & Focus" else "Art & Creative", color = LifeTrackColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (isStudy) "Today ${DurationLogic.format(todayMin)} of ${Goals.hours(studyGoal)} goal" else "Today ${DurationLogic.format(todayMin)}",
                        color = LifeTrackColors.TextSecondary, fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Timer", if (isStudy) "Subjects" else "Projects", "History", "Stats").forEach { x ->
                    FilterChip(selected = tab == x, onClick = { tab = x }, label = { Text(x, fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                }
            }
        }

        if (tab == "Timer") {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isStudy) "Subject" else "Project", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                        if (activeProjects.isEmpty()) {
                            Text("No $noun yet — you can still run a timer, or add your own in the ${if (isStudy) "Subjects" else "Projects"} tab.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                activeProjects.forEach { pr ->
                                    FilterChip(selected = pr.id == selectedId, onClick = {
                                        selectedId = if (pr.id == selectedId) "" else pr.id
                                        prefs.edit().putString("${p}_selected", selectedId).apply()
                                    }, label = { Text(pr.name) })
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Mode", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(TimerMode.STOPWATCH to "Stopwatch", TimerMode.COUNTDOWN to "Countdown", TimerMode.POMODORO to "Pomodoro").forEach { (m, label) ->
                                FilterChip(selected = timer.mode == m, enabled = !timer.active, onClick = { persist(timer.copy(mode = m, targetMin = focusMin)) }, label = { Text(label, fontSize = 12.sp) })
                            }
                        }
                        if (timer.mode != TimerMode.STOPWATCH) {
                            Text(if (timer.mode == TimerMode.POMODORO) "Focus length" else "Length", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (DurationLogic.BUILT_IN + customPresets).sorted().forEach { m ->
                                    val isCustom = m in customPresets
                                    FilterChip(
                                        selected = focusMin == m, enabled = !timer.active,
                                        onClick = { focusMin = m; prefs.edit().putInt("${p}_focus", m).apply(); persist(timer.copy(targetMin = m)) },
                                        label = { Text(DurationLogic.format(m)) },
                                        trailingIcon = if (isCustom && !timer.active) {
                                            { IconButton(onClick = { savePresets(customPresets - m) }, modifier = Modifier.size(18.dp)) { Icon(Icons.Default.Close, "Remove $m minutes", modifier = Modifier.size(14.dp)) } }
                                        } else null
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(customMinutes, { customMinutes = it.filter(Char::isDigit).take(3); durationError = null }, label = { Text("Your own minutes (e.g. 37)") },
                                    singleLine = true, enabled = !timer.active, isError = durationError != null, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                Button(enabled = !timer.active, onClick = {
                                    val m = DurationLogic.parse(customMinutes)
                                    if (m == null) durationError = "Enter 1 to 720 minutes"
                                    else {
                                        savePresets(DurationLogic.addPreset(customPresets, m))
                                        focusMin = m; prefs.edit().putInt("${p}_focus", m).apply(); persist(timer.copy(targetMin = m))
                                        customMinutes = ""; durationError = null
                                    }
                                }) { Text("Use") }
                            }
                            if (durationError != null) Text(durationError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            if (timer.mode == TimerMode.POMODORO) {
                                var breakText by remember(breakMin) { mutableStateOf(breakMin.toString()) }
                                OutlinedTextField(breakText, {
                                    breakText = it.filter(Char::isDigit).take(3)
                                    DurationLogic.parse(breakText)?.let { v -> breakMin = v; prefs.edit().putInt("${p}_break", v).apply() }
                                }, label = { Text("Break length (minutes)") }, singleLine = true, enabled = !timer.active,
                                    isError = DurationLogic.parse(breakText) == null, modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                        }
                    }
                }
            }
            item {
                val done = timer.isDone(now)
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.SurfaceRaised), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            when {
                                timer.onBreak -> "BREAK"
                                done -> "TIME'S UP"
                                timer.running -> "FOCUSING"
                                timer.active -> "PAUSED"
                                else -> "READY"
                            }, color = LifeTrackColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                        )
                        val shown = if (!timer.active && timer.mode != TimerMode.STOPWATCH) focusMin * 60_000L
                        else if (timer.mode == TimerMode.STOPWATCH && !timer.onBreak) timer.elapsedMs(now) else timer.remainingMs(now)
                        Text(TimerState.clock(shown), color = LifeTrackColors.TextPrimary, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        Text(selected?.name ?: defaultTitle, color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!timer.active) {
                                Button(onClick = { persist(timer.copy(targetMin = focusMin, onBreak = false).start(System.currentTimeMillis())) }) {
                                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Start")
                                }
                            } else if (timer.onBreak) {
                                if (timer.running && !done) OutlinedButton(onClick = { persist(timer.pause(System.currentTimeMillis())) }) { Text("Pause") }
                                else if (!done) Button(onClick = { persist(timer.resume(System.currentTimeMillis())) }) { Text("Resume") }
                                OutlinedButton(onClick = { persist(TimerState(timer.mode, focusMin)) }) { Text(if (done) "Back to focus" else "Skip break") }
                            } else {
                                if (timer.running) OutlinedButton(onClick = { persist(timer.pause(System.currentTimeMillis())) }) { Icon(Icons.Default.Pause, null); Spacer(Modifier.width(4.dp)); Text("Pause") }
                                else if (!done) Button(onClick = { persist(timer.resume(System.currentTimeMillis())) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Resume") }
                                Button(onClick = {
                                    val t = System.currentTimeMillis()
                                    val paused = timer.pause(t)
                                    persist(paused)
                                    breakAfterSave = paused.mode == TimerMode.POMODORO && paused.isDone(t)
                                    val m = TimerState.minutesToLog(paused.countedMs(t))
                                    finishMinutes = if (m > 0) m.toString() else ""
                                    showFinish = true
                                }) { Text("Finish & save") }
                                OutlinedButton(onClick = { persist(timer.reset().copy(targetMin = focusMin)) }) { Text("Discard") }
                            }
                        }
                        if (done && !timer.onBreak) Text(if (timer.mode == TimerMode.POMODORO) "Tap Finish & save to log this focus block; your $breakMin min break starts after saving." else "Tap Finish & save to log this session.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                    }
                }
            }
            item {
                Text("The timer keeps counting if you leave this screen. It does not send a notification when time is up; you will see it finished when you come back.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
            }
        }

        if (tab == "Stats") {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val endCalendar = java.util.Calendar.getInstance().apply { time = formatter.parse(today) ?: java.util.Date() }
            val periodDates = when (statsPeriod) {
                "Weekly" -> {
                    val start = (endCalendar.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -6) }
                    (0..6).map { offset ->
                        val date = formatter.format(start.time)
                        start.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        date
                    }
                }
                "Monthly" -> {
                    val start = (endCalendar.clone() as java.util.Calendar).apply { set(java.util.Calendar.DAY_OF_MONTH, 1) }
                    (0 until start.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)).map {
                        val date = formatter.format(start.time)
                        start.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        date
                    }
                }
                "All time" -> emptyList()
                else -> listOf(today)
            }
            val statsLogs = if (statsPeriod == "All time") logs else logs.filter { row -> periodDates.any { it == row.date } }
            val monthPrefix = today.take(7)
            val daysInMonth = endCalendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            val monthlyGoalMinutes = (Goals.studyTarget(context).coerceAtLeast(1) * daysInMonth)
            val monthlyActualMinutes = logs.filter { it.date.startsWith(monthPrefix) }
                .sumOf { it.amount.trim().toIntOrNull() ?: 0 }
            val totalMinutes = statsLogs.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
            val activeDates = statsLogs.map { it.date }.distinct().size
            val averageMinutes = if (activeDates > 0) totalMinutes / activeDates else 0
            val subjectTotals = statsLogs.groupBy { it.title.ifBlank { defaultTitle } }
                .mapValues { (_, rows) -> rows.sumOf { it.amount.trim().toIntOrNull() ?: 0 } }
                .toList().sortedByDescending { it.second }
            val activityDates = logs.filter { (it.amount.trim().toIntOrNull() ?: 0) > 0 }.map { it.date }.toSet()
            var currentStreak = 0
            val streakCursor = java.util.Calendar.getInstance().apply { time = formatter.parse(today) ?: java.util.Date() }
            if (formatter.format(streakCursor.time) !in activityDates) streakCursor.add(java.util.Calendar.DAY_OF_YEAR, -1)
            while (formatter.format(streakCursor.time) in activityDates && currentStreak < 3660) {
                currentStreak++
                streakCursor.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            var bestStreak = 0
            var runningStreak = 0
            activityDates.minOrNull()?.let { earliest ->
                val scan = java.util.Calendar.getInstance().apply { time = formatter.parse(earliest) ?: java.util.Date() }
                val end = java.util.Calendar.getInstance().apply { time = formatter.parse(today) ?: java.util.Date() }
                while (scan.time <= end.time && runningStreak < 3660) {
                    if (formatter.format(scan.time) in activityDates) {
                        runningStreak++
                        bestStreak = maxOf(bestStreak, runningStreak)
                    } else runningStreak = 0
                    scan.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }
            val trendDates = if (statsPeriod == "All time") {
                val start = (endCalendar.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -6) }
                (0..6).map {
                    val date = formatter.format(start.time)
                    start.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    date
                }
            } else periodDates
            val trendBuckets: List<Pair<String, Int>> = if (statsPeriod == "Monthly") {
                trendDates.chunked(7).mapIndexed { index, dates ->
                    "Week ${index + 1}" to logs.filter { it.date in dates }.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
                }
            } else {
                trendDates.map { date ->
                    date.takeLast(5) to logs.filter { it.date == date }.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
                }
            }
            val trendValues = trendBuckets.map { it.second }
            val maxTrend = (trendValues.maxOrNull() ?: 0).coerceAtLeast(1)
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isStudy) "STUDY INSIGHTS" else "CREATIVE INSIGHTS", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Statistics use saved sessions only.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("Daily", "Weekly", "Monthly", "All time").forEach { period ->
                                FilterChip(selected = statsPeriod == period, onClick = { statsPeriod = period }, label = { Text(period, fontSize = 10.sp) })
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Total time", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                            Text(DurationLogic.format(totalMinutes), color = LifeTrackColors.Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Active days", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                            Text(activeDates.toString(), color = LifeTrackColors.Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Monthly goal vs actual", Modifier.weight(1f), color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("${(monthlyActualMinutes * 100L / monthlyGoalMinutes.coerceAtLeast(1)).coerceAtMost(999)}%", color = LifeTrackColors.Primary, fontWeight = FontWeight.Bold)
                        }
                        Text("${DurationLogic.format(monthlyActualMinutes)} / ${DurationLogic.format(monthlyGoalMinutes)} target", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                        LinearProgressIndicator(
                            progress = { (monthlyActualMinutes.toFloat() / monthlyGoalMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = LifeTrackColors.Primary,
                            trackColor = LifeTrackColors.SurfaceRaised
                        )
                        Text("Based on your saved daily study target and this month's logged sessions.", color = LifeTrackColors.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("$currentStreak days", color = LifeTrackColors.Primary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("Current streak", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("$bestStreak days", color = LifeTrackColors.Primary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("Best streak", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("TIME TREND", color = LifeTrackColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(if (statsPeriod == "All time") "Last 7 days · all-time total above" else "$statsPeriod activity", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        if (trendValues.none { it > 0 }) {
                            Text("No sessions in this period yet.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                        } else {
                            trendBuckets.forEachIndexed { index, bucket ->
                                val value = trendValues[index]
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(bucket.first, color = LifeTrackColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.width(52.dp))
                                    LinearProgressIndicator(
                                        progress = { value.toFloat() / maxTrend.toFloat() },
                                        modifier = Modifier.weight(1f).height(7.dp),
                                        color = LifeTrackColors.Primary,
                                        trackColor = LifeTrackColors.SurfaceRaised
                                    )
                                    Text(DurationLogic.format(value), color = LifeTrackColors.TextPrimary, fontSize = 10.sp, modifier = Modifier.width(52.dp))
                                }
                            }
                        }
                        HorizontalDivider(color = LifeTrackColors.TextSecondary.copy(alpha = .18f))
                        Text("Daily average: ${DurationLogic.format(averageMinutes)} on active days", color = LifeTrackColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isStudy) "SUBJECT-WISE TIME" else "PROJECT-WISE TIME", color = LifeTrackColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        if (subjectTotals.isEmpty()) {
                            Text("Add a session to see your breakdown.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                        } else {
                            subjectTotals.take(10).forEach { (name, minutes) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(name, color = LifeTrackColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Text(DurationLogic.format(minutes), color = LifeTrackColors.Primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (tab != "Timer" && tab != "History" && tab != "Stats") {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add your own $noun", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(newName, { newName = it.take(ProjectLogic.MAX_NAME); nameError = null }, label = { Text("Name") }, singleLine = true, isError = nameError != null, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                val updated = ProjectLogic.add(projects, newName)
                                if (updated == null) nameError = "Enter a new name (up to ${ProjectLogic.MAX_NAME} characters)"
                                else { saveProjects(updated); newName = ""; nameError = null }
                            }) { Text("Add") }
                        }
                        if (nameError != null) Text(nameError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
            if (projects.isEmpty()) item { Text("Nothing here yet. Add whatever you actually work on; nothing is pre-filled.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp) }
            items(projects, key = { it.id }) { pr ->
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pr.name, color = if (pr.archived) LifeTrackColors.TextSecondary else LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            val total = logs.filter { it.title == pr.name }.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
                            Text(if (pr.archived) "Archived · ${DurationLogic.format(total)} total" else "${DurationLogic.format(total)} total", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                        }
                        IconButton(onClick = { renaming = pr; renameText = pr.name }) { Icon(Icons.Default.Edit, "Rename") }
                        IconButton(onClick = { saveProjects(ProjectLogic.setArchived(projects, pr.id, !pr.archived)) }) {
                            Icon(if (pr.archived) Icons.Default.Unarchive else Icons.Default.Archive, if (pr.archived) "Restore" else "Archive")
                        }
                        IconButton(onClick = { deleting = pr }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            }
        }

        if (tab == "History") {
            val recent = logs.filter { it.category == category }.sortedWith(compareByDescending<LogEntry> { it.date }.thenByDescending { it.id }).take(30)
            val studyDates = logs.filter { it.category == category && (it.amount.trim().toIntOrNull() ?: 0) > 0 }.map { it.date }.toSet()
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val todayCalendar = java.util.Calendar.getInstance().apply { time = dateFormat.parse(today) ?: java.util.Date() }
            var currentStreak = 0
            val cursor = java.util.Calendar.getInstance().apply { time = todayCalendar.time }
            if (dateFormat.format(cursor.time) !in studyDates) cursor.add(java.util.Calendar.DAY_OF_YEAR, -1)
            while (dateFormat.format(cursor.time) in studyDates && currentStreak < 3660) {
                currentStreak++
                cursor.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            var bestStreak = 0
            var runningStreak = 0
            val earliest = studyDates.minOrNull()
            if (earliest != null) {
                val scan = java.util.Calendar.getInstance().apply { time = dateFormat.parse(earliest) ?: todayCalendar.time }
                while (scan.time <= todayCalendar.time && runningStreak < 3660) {
                    if (dateFormat.format(scan.time) in studyDates) {
                        runningStreak++
                        bestStreak = maxOf(bestStreak, runningStreak)
                    } else runningStreak = 0
                    scan.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }
            val weekStart = java.util.Calendar.getInstance().apply { time = todayCalendar.time; add(java.util.Calendar.DAY_OF_YEAR, -6) }
            val weekMinutes = logs.filter { it.category == category && it.date >= dateFormat.format(weekStart.time) && it.date <= today }
                .sumOf { it.amount.trim().toIntOrNull() ?: 0 }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isStudy) "STUDY CONSISTENCY" else "CREATIVE CONSISTENCY", color = LifeTrackColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("$currentStreak days", color = LifeTrackColors.Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("Current streak", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("$bestStreak days", color = LifeTrackColors.Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("Best streak", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(DurationLogic.format(weekMinutes), color = LifeTrackColors.Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("Last 7 days", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                            }
                        }
                        Text("Counts only days with saved sessions. Missing days are not counted as activity.", color = LifeTrackColors.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
            if (recent.isEmpty()) item { Text("No sessions saved yet. Finish a timer or add a session to see it here.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp) }
            items(recent, key = { it.id }) { e ->
                Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title.ifBlank { defaultTitle }, color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(e.date + if (e.note.isNotBlank()) " · ${e.note}" else "", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                        }
                        Text(DurationLogic.format(e.amount.trim().toIntOrNull() ?: 0), color = LifeTrackColors.Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Text("To edit or delete a session, use the History page.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp) }
        }
    }
}