package com.yakhrevan.lifetrack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Navy get() = LifeTrackColors.Background
private val Panel get() = LifeTrackColors.Surface
private val Panel2 get() = LifeTrackColors.SurfaceRaised
private val Mint get() = LifeTrackColors.Primary
private val Blue get() = LifeTrackColors.Blue
private val Purple get() = LifeTrackColors.Purple
private val Pink get() = LifeTrackColors.Pink
private val Orange get() = LifeTrackColors.Orange
private val Muted get() = LifeTrackColors.TextSecondary
private val WhiteText get() = LifeTrackColors.TextPrimary

internal fun isValidIsoDate(value: String): Boolean = runCatching { java.time.LocalDate.parse(value); true }.getOrDefault(false)

internal fun isValidIsoDateRange(start: String, end: String): Boolean = runCatching {
    val startDate = java.time.LocalDate.parse(start)
    val endDate = java.time.LocalDate.parse(end)
    !endDate.isBefore(startDate)
}.getOrDefault(false)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(248,250,255)
        window.navigationBarColor = android.graphics.Color.rgb(248,250,255)
        val savedSettings = UserProfileRepository.getSettings(this)
        val resolvedSettings = if (savedSettings.themeMode == "System") {
            val systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            savedSettings.copy(themeMode = if (systemDark) "Dark" else "Light")
        } else savedSettings
        LifeTrackColors.applySettings(resolvedSettings)
        setContent {
            val dark = LifeTrackColors.darkMode.value
            val baseDensity = LocalDensity.current
            val layoutScale = if (LifeTrackColors.dashboardStyle.value == "Compact") 0.94f else 1f
            val scaledDensity = Density(baseDensity.density * layoutScale, baseDensity.fontScale * LifeTrackColors.textSizeMultiplier.floatValue)
            val scheme = if (dark) darkColorScheme(
                primary = LifeTrackColors.Primary,
                secondary = LifeTrackColors.Blue,
                background = LifeTrackColors.Background,
                surface = LifeTrackColors.Surface,
                surfaceVariant = LifeTrackColors.SurfaceRaised,
                onBackground = LifeTrackColors.TextPrimary,
                onSurface = LifeTrackColors.TextPrimary,
                onSurfaceVariant = LifeTrackColors.TextSecondary
            ) else lightColorScheme(
                primary = LifeTrackColors.Primary,
                secondary = LifeTrackColors.Blue,
                background = LifeTrackColors.Background,
                surface = LifeTrackColors.Surface,
                surfaceVariant = LifeTrackColors.SurfaceRaised,
                onBackground = LifeTrackColors.TextPrimary,
                onSurface = LifeTrackColors.TextPrimary,
                onSurfaceVariant = LifeTrackColors.TextSecondary
            )
            SideEffect {
                val bar = if (dark) android.graphics.Color.rgb(16, 21, 34) else android.graphics.Color.rgb(248, 250, 255)
                window.statusBarColor = bar
                window.navigationBarColor = bar
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    var flags = if (dark) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !dark) {
                        flags = flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                    window.decorView.systemUiVisibility = flags
                }
            }
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                MaterialTheme(colorScheme = scheme) { LifeTrack() }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LifeTrack(){
    val context=androidx.compose.ui.platform.LocalContext.current
    val repository = remember { LifeTrackRepository(context) }
    val profilePrefs = remember { context.getSharedPreferences("lifetrack_profile",Context.MODE_PRIVATE) }
    // Real-data-first onboarding: never create synthetic tracker entries automatically.
    var logs by remember { mutableStateOf(repository.loadEntries()) }
    var appRefreshToken by remember { mutableIntStateOf(0) }
    var showUsageAccessDialog by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<LifeTrackUpdate?>(null) }
    var updateCheckFinished by remember { mutableStateOf(false) }
    var dismissedUpdateVersion by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // Demo seeding is disabled for release builds.

    // Release cleanup: remove previously seeded synthetic records once and never seed samples again.
    // User-created records are preserved unless they carry the explicit DEMO DATA marker.
    LaunchedEffect(context) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val cleanupPrefs = context.getSharedPreferences("lifetrack_demo_seed_v2", Context.MODE_PRIVATE)
            if (!cleanupPrefs.getBoolean("release_cleanup_v2", false)) {
                HabitRoutineRepository.removeLegacyDemoRecords(context)
                val savedEntries = repository.loadEntries()
                val personalEntries = savedEntries.filterNot { it.note.contains("DEMO DATA", ignoreCase = true) }
                if (personalEntries.size != savedEntries.size) repository.saveEntries(personalEntries)

                val waterEntries = WaterRepository.loadEntries(context, personalEntries)
                val personalWaterEntries = waterEntries.filterNot { it.note.contains("DEMO DATA", ignoreCase = true) }
                if (personalWaterEntries.size != waterEntries.size) WaterRepository.saveEntries(context, personalWaterEntries)

                NoteRepository.getNotes(context, includeArchived = true)
                    .filter { it.content.contains("DEMO DATA", ignoreCase = true) }
                    .forEach { NoteRepository.deleteNote(context, it.id) }
                TaskRepository.getTasks(context, includeArchived = true)
                    .filter { it.description.contains("DEMO DATA", ignoreCase = true) }
                    .forEach { TaskRepository.deleteTask(context, it.id) }
                StudyRepository.loadSessions(context)
                    .filter { it.notes.contains("DEMO DATA", ignoreCase = true) }
                    .forEach { StudyRepository.deleteSession(context, it.id) }
                cleanupPrefs.edit().putBoolean("release_cleanup_v2", true).apply()
            }
            profilePrefs.edit().putBoolean("demo_mode", false).putBoolean("demo_seeded", true).apply()
        }
        // Refresh the in-memory dashboard state after persisted sample cleanup completes.
        logs = repository.loadEntries()
        appRefreshToken++
    }
    LaunchedEffect(context) {
        MoneyCategory.load(context)
        // Optional update checks must never prevent normal app startup.
        val currentVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.1.0"
        }.getOrDefault("1.1.0")
        availableUpdate = UpdateChecker.check(currentVersion)
        updateCheckFinished = true
    }

    // Ask for the app's relevant runtime permissions on startup. Android special
    // access (Usage Access) is handled separately because it cannot use a runtime dialog.
    val startupPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        logs = repository.loadEntries()
        appRefreshToken++
        showUsageAccessDialog = !ScreenTimeRepository.hasUsageAccessPermission(context)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                logs = repository.loadEntries()
                appRefreshToken++
                if (ScreenTimeRepository.hasUsageAccessPermission(context)) {
                    showUsageAccessDialog = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        val requiredPermissions = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
        }
        if (requiredPermissions.isNotEmpty()) startupPermissionLauncher.launch(requiredPermissions.toTypedArray())
        else {
            logs = repository.loadEntries()
            appRefreshToken++
            showUsageAccessDialog = !ScreenTimeRepository.hasUsageAccessPermission(context)
        }
    }

    val updateToShow = availableUpdate?.takeIf { it.versionName != dismissedUpdateVersion }
    if (updateCheckFinished && updateToShow != null) {
        AlertDialog(
            onDismissRequest = { dismissedUpdateVersion = updateToShow.versionName },
            title = { Text("LifeTrack update available") },
            text = { Text("Version " + updateToShow.versionName + " is available. Open the GitHub release page to review release notes and download the update.") },
            confirmButton = {
                TextButton(onClick = {
                    dismissedUpdateVersion = updateToShow.versionName
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateToShow.releaseUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { dismissedUpdateVersion = updateToShow.versionName }) { Text("Later") }
            }
        )
    }

    if (showUsageAccessDialog) {
        AlertDialog(
            onDismissRequest = { showUsageAccessDialog = false },
            title = { Text("Enable Screen Time access") },
            text = { Text("LifeTrack needs Android Usage Access to read device screen-time statistics. This is a separate system setting and cannot be granted by the normal permission popup. You can enable it now or continue without Screen Time data.") },
            confirmButton = {
                TextButton(onClick = {
                    showUsageAccessDialog = false
                    ScreenTimeRepository.openUsageAccessSettings(context)
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showUsageAccessDialog = false }) { Text("Not now") }
            }
        )
    }

    LaunchedEffect(appRefreshToken) {
        // Re-read persisted tracker data after permission flow and refresh startup-dependent data.
        logs = repository.loadEntries()
        MoneyCategory.load(context)
        val city = profilePrefs.getString("weather_city", "Coimbatore") ?: "Coimbatore"
        val savedLat = profilePrefs.getString("weather_lat", null)?.toDoubleOrNull()
        val savedLon = profilePrefs.getString("weather_lon", null)?.toDoubleOrNull()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (savedLat != null && savedLon != null &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fetchLifeTrackWeatherByCoords(savedLat, savedLon, city)
                } else {
                    fetchLifeTrackWeather(city)
                }
            }.onSuccess { weather ->
                profilePrefs.edit()
                    .putString("weather_city_label", weather)
                    .putLong("weather_city_checked_at", System.currentTimeMillis())
                    .apply()
            }
        }
    }

    LaunchedEffect(Unit) {
        val syncPrefs = CloudSyncRepository.getPreferences(context)
        if (syncPrefs.autoBackupEnabled) {
            BackupSyncScheduler.scheduleDailyBackup(context)
        }
    }
    val initialPage = if (profilePrefs.getBoolean("setup_complete", false)) "Home" else "Setup"
    var profileName by remember { mutableStateOf(profilePrefs.getString("profile_name", null) ?: profilePrefs.getString("name", "") ?: "") }
    var page by remember { mutableStateOf(initialPage) }
    val backstack = remember { mutableStateListOf(initialPage) }
    var profileReturnPage by remember { mutableStateOf("Home") }
    var settingsInitialSubScreen by remember { mutableStateOf("Main") }
    LaunchedEffect(page) { if (page != "Settings") settingsInitialSubScreen = "Main" }
    var showLauncher  by remember { mutableStateOf(false) }
    var showQuickAdd  by remember { mutableStateOf(false) }
    var moneyInitialQuickAddType by remember { mutableStateOf<String?>(null) }
    var add by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf("Study") }
    var moneyCategory by remember { mutableStateOf("Other") }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var entryDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var editingEntry by remember { mutableStateOf<LogEntry?>(null) }
    var deletingEntry by remember { mutableStateOf<LogEntry?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var historySearch by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf("All") }
    var insightPeriod by remember { mutableStateOf("Daily") }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown" }.getOrDefault("Unknown") }
    var selectedAppearance by remember { mutableStateOf(if (LifeTrackColors.darkMode.value) "Dark" else "Light") }
    var insightFrom by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-01", Locale.US).format(Date())) }
    var insightTo by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var remindersEnabled by remember { mutableStateOf(profilePrefs.getBoolean("water_reminders_enabled",false)) }
    var reminderInterval by remember { mutableIntStateOf(profilePrefs.getInt("reminder_interval",60).coerceIn(15,240)) }
    val waterNotificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        remindersEnabled = granted
        profilePrefs.edit().putBoolean("water_reminders_enabled", granted).putInt("reminder_interval", reminderInterval).apply()
        scheduleWaterReminders(context, reminderInterval, granted)
    }
    var waterGoal by remember { mutableIntStateOf(Goals.waterTarget(context)) }
    var studyGoal by remember { mutableIntStateOf(Goals.studyTarget(context)) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var waterGoalInput by remember { mutableStateOf("") }
    var studyGoalInput by remember { mutableStateOf("") }
    if (showGoalDialog) {
        val waterOk = Goals.parseWater(waterGoalInput) != null
        val studyOk = Goals.parseStudy(studyGoalInput) != null
        AlertDialog(
            onDismissRequest={showGoalDialog=false},
            title={Text("Daily goals")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value=waterGoalInput,onValueChange={waterGoalInput=it.filter(Char::isDigit)},label={Text("Water goal (ml, 500–10000)")},isError=!waterOk,singleLine=true)
                    OutlinedTextField(value=studyGoalInput,onValueChange={studyGoalInput=it.filter(Char::isDigit)},label={Text("Study goal (minutes, 15–1440)")},isError=!studyOk,singleLine=true)
                }
            },
            confirmButton={TextButton(enabled=waterOk&&studyOk,onClick={
                val w=Goals.parseWater(waterGoalInput)!!; val st=Goals.parseStudy(studyGoalInput)!!
                Goals.save(context,w,st); waterGoal=w; studyGoal=st; showGoalDialog=false
            }) { Text("Save") }},
            dismissButton={TextButton(onClick={showGoalDialog=false}) { Text("Cancel") }}
        )
    }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<LifeTrackBackupData?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: error("Unable to create the backup file.")
                output.bufferedWriter().use { it.write(LifeTrackBackup.encode(context, logs)) }
            }.onSuccess {
                backupMessage = "Full LifeTrack backup saved successfully."
            }.onFailure { error ->
                backupMessage = "Backup could not be saved: ${error.message ?: "storage error"}"
            }
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val result = StringBuilder()
                    val buffer = CharArray(8192)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        require(result.length + count <= 10 * 1024 * 1024) {
                            "Backup file is too large to import safely (maximum 10 MB)."
                        }
                        result.append(buffer, 0, count)
                    }
                    result.toString()
                } ?: error("Unable to read the selected backup file.")
                LifeTrackBackup.decode(raw)
            }.onSuccess { entries ->
                pendingRestore = entries
                backupMessage = null
            }.onFailure { error ->
                backupMessage = "Backup could not be read: ${error.message ?: "invalid JSON file"}"
            }
        }
    }
    val today=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
    val systemInDark = isSystemInDarkTheme()

    val navigateTo: (String) -> Unit = { dest ->
        showQuickAdd = false
        when (dest) {
            "More" -> {
                // Keep More as a floating launcher overlay instead of navigating to a page.
                showLauncher = true
            }
            "Food" -> {
                showLauncher = false
                cat = "Meal"
                moneyCategory = "Other"
                title = ""
                amount = "1"
                note = ""
                entryDate = today
                validationError = null
                add = true
            }
            "Mood" -> {
                if (page != "Habits & Routines") {
                    backstack.add("Habits & Routines")
                    page = "Habits & Routines"
                }
            }
            else -> {
                if (page != dest) {
                    backstack.add(dest)
                    page = dest
                }
            }
        }
    }

    val navigateBack: () -> Unit = {
        showLauncher = false
        showQuickAdd = false
        if (backstack.size > 1) {
            backstack.removeAt(backstack.lastIndex)
            page = backstack.last()
        } else if (page != "Home" && page != "Setup") {
            page = "Home"
            backstack.clear()
            backstack.add("Home")
        }
    }

    val canGoBack = showQuickAdd || showLauncher || add || editingEntry != null || deletingEntry != null || showGoalDialog || showAppearanceDialog || showAboutDialog || backstack.size > 1 || (page != "Home" && page != "Setup")

    BackHandler(enabled = canGoBack) {
        when {
            showQuickAdd -> showQuickAdd = false
            showLauncher -> showLauncher = false
            add -> add = false
            editingEntry != null -> editingEntry = null
            deletingEntry != null -> deletingEntry = null
            showGoalDialog -> showGoalDialog = false
            showAppearanceDialog -> showAppearanceDialog = false
            showAboutDialog -> showAboutDialog = false
            backstack.size > 1 -> {
                backstack.removeAt(backstack.lastIndex)
                page = backstack.last()
            }
            page != "Home" && page != "Setup" -> {
                page = "Home"
                backstack.clear()
                backstack.add("Home")
            }
        }
    }
    val daily=logs.filter { it.date==today }
    val weekStart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }.time)
    val weekLogs = logs.filter { it.date >= weekStart && it.date <= today }
    val water=daily.filter { it.category=="Water" }.sumOf { it.amount.toIntOrNull()?:0 }
    val study=daily.filter { it.category=="Study" }.sumOf { it.amount.toIntOrNull()?:0 }
    val spent=daily.filter { it.category=="Expense" }.sumOf { MoneyAmounts.value(it.amount) }
    val currentMonth = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    val insightBounds = remember(insightPeriod, today, insightFrom, insightTo) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val end = Calendar.getInstance().apply { time = fmt.parse(today) ?: Date() }
        val start = (end.clone() as Calendar)
        when (insightPeriod) {
            "Weekly" -> start.add(Calendar.DAY_OF_YEAR, -6)
            "Monthly" -> start.set(Calendar.DAY_OF_MONTH, 1)
            "Yearly" -> start.set(Calendar.DAY_OF_YEAR, 1)
            "Custom range" -> if (isValidIsoDateRange(insightFrom, insightTo)) {
                start.time = fmt.parse(insightFrom) ?: start.time
                end.time = fmt.parse(insightTo) ?: end.time
            }
            "All time" -> start.time = logs.minOfOrNull { runCatching { fmt.parse(it.date) ?: Date() }.getOrNull() ?: Date() } ?: end.time
        }
        fmt.format(start.time) to fmt.format(end.time)
    }
    val periodStartDate = insightBounds.first
    val periodEndDate = insightBounds.second
    val insightLogs = when (insightPeriod) {
        "All time" -> logs
        else -> logs.filter { it.date >= periodStartDate && it.date <= periodEndDate }
    }
    val previousBounds = remember(insightPeriod, insightBounds) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start = Calendar.getInstance().apply { time = fmt.parse(insightBounds.first) ?: Date() }
        val end = Calendar.getInstance().apply { time = fmt.parse(insightBounds.second) ?: Date() }
        val days = ((end.timeInMillis - start.timeInMillis) / 86_400_000L + 1).coerceAtLeast(1L).coerceAtMost(366L).toInt()
        when (insightPeriod) {
            "Daily" -> { start.add(Calendar.DAY_OF_YEAR, -1); end.add(Calendar.DAY_OF_YEAR, -1) }
            "Weekly" -> { start.add(Calendar.DAY_OF_YEAR, -7); end.add(Calendar.DAY_OF_YEAR, -7) }
            "Monthly" -> { start.add(Calendar.MONTH, -1); end.add(Calendar.MONTH, -1) }
            "Yearly" -> { start.add(Calendar.YEAR, -1); end.add(Calendar.YEAR, -1) }
            "Custom range" -> { start.add(Calendar.DAY_OF_YEAR, -days); end.add(Calendar.DAY_OF_YEAR, -days) }
            else -> { start.add(Calendar.DAY_OF_YEAR, -days); end.add(Calendar.DAY_OF_YEAR, -1) }
        }
        fmt.format(start.time) to fmt.format(end.time)
    }
    val previousInsightLogs = if (insightPeriod == "All time") emptyList() else logs.filter { it.date >= previousBounds.first && it.date <= previousBounds.second }
    fun persistEntries(entries: List<LogEntry>) {
        repository.saveEntries(entries)
    }
    fun openEdit(entry: LogEntry) {
        editingEntry = entry
        editTitle = entry.title
        editAmount = entry.amount
        editNote = entry.note
        editDate = entry.date
        moneyCategory = entry.moneyCategory
        validationError = null
    }
    fun save(){
        val raw = EntryValidator.normalizeAmount(amount)
        val error = EntryValidator.amountError(cat, raw)
        if (error != null) {
            validationError = error
            return
        }
        val row=LogEntry(
            category = cat,
            title = title.ifBlank { defaultTitle(cat) },
            amount = raw,
            date = entryDate,
            note = note,
            moneyCategory = if (cat == "Expense" || cat == "Income") moneyCategory else "Other"
        )
        logs=listOf(row)+logs
        persistEntries(logs)
        add=false; title=""; amount=""; note=""; moneyCategory="Other"; entryDate = today; validationError=null
    }
    Scaffold(
        containerColor=LifeTrackColors.Background,
        topBar={
            if (page != "Setup" && page != "Home" && page != "Tasks" && page != "Notes" && page != "Profile" && page != "Settings" && page != "Goals" && page != "Personal Goals" && page != "Money" && page != "Categories" && page != "Insights" && page != "Calendar" && page != "Digital Wellbeing" && page != "Screen Time" && page != "Study" && page != "Study & Focus" && page != "Water" && page != "Water Coach" && page != "Water Tracking" && page != "Backup & Sync" && page != "Cloud Backup" && page != "Voice Assistant" && page != "Voice Bot" && page != "Voice Logging") Row(Modifier.fillMaxWidth().background(Navy).statusBarsPadding().padding(horizontal=18.dp, vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center) {
                    Image(painterResource(R.drawable.app_logo), contentDescription = "LifeTrack Logo", modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(start=10.dp)) {
                    Text("LifeTrack",color=WhiteText,fontSize=18.sp,fontWeight=FontWeight.ExtraBold)
                    Text(when(page){"Calendar"->"Your consistency at a glance";"Insights"->"Your progress, visualized";"History"->"Your daily journal";"Settings"->"Make it yours";"Money"->"Income, expenses & budgets";else->"Track what matters"},color=Muted,fontSize=11.sp)
                }
            }
        },
        bottomBar={
            if (page != "Setup" && page != "Study" && page != "Study & Focus" && page != "Water" && page != "Water Coach" && page != "Water Tracking" && page != "Backup & Sync" && page != "Cloud Backup" && page != "Voice Assistant" && page != "Voice Bot" && page != "Voice Logging") {
                Surface(
                    color = LifeTrackColors.Surface,
                    border = BorderStroke(1.dp, LifeTrackColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Home
                        val isHome = page == "Home"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { navigateTo("Home") }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isHome) LifeTrackColors.Primary.copy(alpha = 0.14f) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = "Home",
                                    tint = if (isHome) LifeTrackColors.Primary else LifeTrackColors.TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                "Home",
                                color = if (isHome) LifeTrackColors.Primary else LifeTrackColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isHome) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        // 2. Tasks
                        val isTasks = page == "Tasks"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { navigateTo("Tasks") }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isTasks) LifeTrackColors.Primary.copy(alpha = 0.14f) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Tasks",
                                    tint = if (isTasks) LifeTrackColors.Primary else LifeTrackColors.TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                "Tasks",
                                color = if (isTasks) LifeTrackColors.Primary else LifeTrackColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isTasks) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        // 3. Center FAB: Gradient + / X Button with smooth rotation
                        val fabRotation by animateFloatAsState(
                            targetValue = if (showQuickAdd) 135f else 0f,
                            animationSpec = tween(durationMillis = if (LifeTrackColors.isMotionReduced) 0 else 300, easing = FastOutSlowInEasing),
                            label = "fab_rotation"
                        )
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .shadow(10.dp, CircleShape, ambientColor = Color(0xFF8B5CF6), spotColor = Color(0xFF6366F1))
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF38BDF8), Color(0xFF6366F1), Color(0xFFA855F7))
                                    )
                                )
                                .border(1.5.dp, Color.White.copy(alpha = 0.62f), CircleShape)
                                .clickable {
                                    if (showLauncher) showLauncher = false
                                    showQuickAdd = !showQuickAdd
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = if (showQuickAdd) "Close Menu" else "Quick Add",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(27.dp)
                                    .graphicsLayer { rotationZ = fabRotation }
                            )
                        }

                        // 4. Notes
                        val isNotes = page == "Notes"
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { navigateTo("Notes") }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isNotes) LifeTrackColors.Primary.copy(alpha = 0.14f) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Note,
                                    contentDescription = "Notes",
                                    tint = if (isNotes) LifeTrackColors.Primary else LifeTrackColors.TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                "Notes",
                                color = if (isNotes) LifeTrackColors.Primary else LifeTrackColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isNotes) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        // 5. Launcher / More (4-dot grid - Windows Start-menu style popup)
                        val isMore = showLauncher
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    if (showQuickAdd) showQuickAdd = false
                                    showLauncher = !showLauncher
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isMore) LifeTrackColors.Primary.copy(alpha = 0.14f) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.GridView,
                                    contentDescription = "More",
                                    tint = if (isMore) LifeTrackColors.Primary else LifeTrackColors.TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                "More",
                                color = if (isMore) LifeTrackColors.Primary else LifeTrackColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isMore) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        when(page) {
            "Setup" -> ProfileSetupScreen(
                initialName = profileName,
                initialAge = profilePrefs.getInt("profile_age", 0).takeIf { it > 0 },
                initialHeightCm = profilePrefs.getInt("profile_height_cm", 0).takeIf { it > 0 } ?: profilePrefs.getString("height", null)?.toIntOrNull(),
                initialWeightKg = profilePrefs.getInt("profile_weight_kg", 0).takeIf { it > 0 } ?: profilePrefs.getString("weight", null)?.toFloatOrNull()?.toInt(),
                initialWaterGoalMl = profilePrefs.getInt("water_target", 0).takeIf { it > 0 },
                initialStudyGoalMinutes = studyGoal,
                initialRemindersEnabled = remindersEnabled,
                initialReminderIntervalMinutes = reminderInterval,
                onComplete = { profile ->
                profilePrefs.edit()
                    .putBoolean("setup_complete", true)
                    .putString("profile_name", profile.name)
                    .putInt("profile_age", profile.age)
                    .putInt("profile_height_cm", profile.heightCm)
                    .putInt("profile_weight_kg", profile.weightKg)
                    .putString("name", profile.name)
                    .putString("height", profile.heightCm.toString())
                    .putString("weight", profile.weightKg.toString())
                    .putString("bottle", profilePrefs.getString("bottle", "750") ?: "750")
                    .putBoolean("profile_saved", true)
                    .putBoolean("water_reminders_enabled", profile.remindersEnabled)
                    .putInt("reminder_interval", profile.reminderIntervalMinutes)
                    .apply()
                Goals.save(context, profile.waterGoalMl, profile.studyGoalMinutes)
                profileName = profile.name
                waterGoal = profile.waterGoalMl
                studyGoal = profile.studyGoalMinutes
                remindersEnabled = profile.remindersEnabled
                reminderInterval = profile.reminderIntervalMinutes
                scheduleWaterReminders(context, reminderInterval, remindersEnabled)
                page = profileReturnPage
            },
                onSkip = {
                    if (!profilePrefs.getBoolean("setup_complete", false)) {
                        profilePrefs.edit().putBoolean("setup_complete", true).putBoolean("profile_saved", true).apply()
                        Goals.save(context, waterGoal, studyGoal)
                    }
                    page = profileReturnPage
                }
            )
            "Home" -> HomeScreen(
                logs = logs,
                studyGoal = studyGoal,
                waterGoal = waterGoal,
                profileName = profileName,
                profilePrefs = profilePrefs,
                onNavigate = { dest ->
                    navigateTo(dest)
                },
                onOpenAdd = { selected ->
                    when (selected) {
                        "Expense", "Income" -> {
                            moneyInitialQuickAddType = selected
                            navigateTo("Money")
                        }
                        "Habit" -> navigateTo("Habits")
                        "Webling" -> navigateTo("Digital Webling")
                        "Note", "Notes" -> navigateTo("Notes")
                        "Task", "Tasks" -> navigateTo("Tasks")
                        "Food" -> {
                            moneyInitialQuickAddType = "Expense"
                            navigateTo("Money")
                        }
                        "Mood" -> navigateTo("Habits & Routines")
                        else -> { cat = selected; entryDate = today; add = true }
                    }
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            "Tasks" -> TasksScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onNavigateBack = navigateBack
            )
            "Notes" -> NotesScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onNavigateBack = navigateBack
            )
            "Calendar" -> CalendarScreen(
                logs = logs,
                onAddEntry = { selectedCat ->
                    cat = selectedCat
                    entryDate = today
                    moneyCategory = "Other"
                    title = ""
                    amount = ""
                    note = ""
                    validationError = null
                    add = true
                },
                onEditEntry = { openEdit(it) },
                onDeleteEntry = { deletingEntry = it },
                onNavigate = { dest ->
                    navigateTo(dest)
                },
                onNavigateHome = {
                    navigateTo("Home")
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            "Goals", "Personal Goals" -> PersonalGoalsScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onNavigateBack = navigateBack
            )
            "Profile" -> ProfileScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onNavigateBack = navigateBack,
                onNavigateToGoals = { navigateTo("Personal Goals") },
                onNavigateToSettings = {
                    settingsInitialSubScreen = "Main"
                    navigateTo("Settings")
                },
                onNavigateToPreferences = {
                    settingsInitialSubScreen = "Personalisation"
                    navigateTo("Settings")
                },
                onNavigateToPersonalisation = {
                    settingsInitialSubScreen = "Personalisation"
                    navigateTo("Settings")
                },
                onNavigateToDataBackup = { navigateTo("Backup & Sync") },
                onNavigateToInsights = { navigateTo("Insights") }
            )
            "Settings" -> SettingsScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                initialSubScreen = settingsInitialSubScreen,
                onNavigateBack = {
                    settingsInitialSubScreen = "Main"
                    navigateBack()
                },
                onNavigateToProfile = { navigateTo("Profile") },
                onNavigateToCategories = { navigateTo("Categories") },
                onNavigateToCloudSync = { navigateTo("Backup & Sync") }
            )
            "Backup & Sync", "Cloud Backup" -> BackupSyncScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onNavigateBack = navigateBack,
                onImportBackupFile = { restoreBackupLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }
            )
            "More" -> {
                // More is a floating launcher, not a full-screen destination.
                LaunchedEffect(Unit) { navigateBack() }
            }
            "Digital Wellbeing", "Screen Time" -> DigitalWellbeingScreen(
                onNavigate = { dest ->
                    navigateTo(dest)
                },
                onNavigateHome = {
                    navigateTo("Home")
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            "Digital Webling" -> DigitalWeblingScreen(logs = logs, modifier = Modifier.fillMaxSize().padding(padding))
            "Habits & Routines" -> HabitsRoutinesScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onHabitCompleted = { name, completed ->
                    val existing = logs.any { it.category == "Care" && it.title == name && it.date == today && it.note == "Routine completed" }
                    logs = when {
                        completed && !existing -> listOf(LogEntry("Care", name, "1", today, "Routine completed")) + logs
                        !completed -> logs.filterNot { it.category == "Care" && it.title == name && it.date == today && it.note == "Routine completed" }
                        else -> logs
                    }
                    persistEntries(logs)
                }
            )
            "Habits" -> HabitsScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onNavigateBack = navigateBack,
                onHabitCompletedCallback = { name, completed ->
                    val existing = logs.any { it.category == "Care" && it.title == name && it.date == today && it.note == "Routine completed" }
                    logs = when {
                        completed && !existing -> listOf(LogEntry("Care", name, "1", today, "Routine completed")) + logs
                        !completed -> logs.filterNot { it.category == "Care" && it.title == name && it.date == today && it.note == "Routine completed" }
                        else -> logs
                    }
                    persistEntries(logs)
                }
            )
            "Routines" -> RoutinesScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onNavigateBack = navigateBack
            )
            "Study & Focus", "Study" -> StudyScreen(
                logs = logs,
                today = today,
                onBack = navigateBack,
                onNavigate = { dest ->
                    if (dest == "Goals" || dest == "Daily goals" || dest == "Edit goals") {
                        studyGoalInput = Goals.studyTarget(context).toString()
                        waterGoalInput = Goals.waterTarget(context).toString()
                        showGoalDialog = true
                    } else {
                        navigateTo(dest)
                    }
                },
                onNavigateHome = {
                    navigateTo("Home")
                },
                onSaveSession = { sessionTitle, minutes, sessionNote ->
                    val row = LogEntry("Study", sessionTitle, minutes.toString(), today, sessionNote)
                    logs = listOf(row) + logs
                    persistEntries(logs)
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            "Categories" -> MoneyScreen(
                logs = logs.filter { it.category == "Expense" || it.category == "Income" },
                onSaveTransaction = { entry ->
                    val existingIndex = logs.indexOfFirst { it.id == entry.id }
                    val updated = if (existingIndex != -1) {
                        logs.toMutableList().apply { set(existingIndex, entry) }
                    } else {
                        listOf(entry) + logs
                    }
                    logs = updated
                    persistEntries(updated)
                },
                onDeleteTransaction = { entry ->
                    val updated = logs.filterNot { it.id == entry.id }
                    logs = updated
                    persistEntries(updated)
                },
                onAdd = { selected -> cat = selected; entryDate = today; add = true },
                onEdit = { openEdit(it) },
                onDelete = { deletingEntry = it },
                onNavigateHome = { navigateTo("Home") },
                initialShowCategories = true,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            "Money" -> MoneyScreen(
                logs = logs.filter { it.category == "Expense" || it.category == "Income" },
                onSaveTransaction = { entry ->
                    val existingIndex = logs.indexOfFirst { it.id == entry.id }
                    val updated = if (existingIndex != -1) {
                        logs.toMutableList().apply { set(existingIndex, entry) }
                    } else {
                        listOf(entry) + logs
                    }
                    logs = updated
                    persistEntries(updated)
                },
                onDeleteTransaction = { entry ->
                    val updated = logs.filterNot { it.id == entry.id }
                    logs = updated
                    persistEntries(updated)
                },
                initialQuickAddType = moneyInitialQuickAddType,
                onResetQuickAddType = { moneyInitialQuickAddType = null },
                onAdd = { selected ->
                    moneyInitialQuickAddType = selected
                },
                onEdit = { openEdit(it) },
                onDelete = { deletingEntry = it },
                onNavigateHome = { navigateTo("Home") },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            "Voice Assistant", "Voice Bot", "Voice Logging" -> VoiceBotScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onNavigateBack = navigateBack,
                onNavigateToPage = { dest -> navigateTo(dest) }
            )
            "Water", "Water Coach", "Water Tracking" -> WaterScreen(
                logs = logs,
                today = today,
                onNavigate = { dest ->
                    navigateTo(dest)
                },
                onNavigateHome = {
                    navigateTo("Home")
                },
                onWaterLogged = { _, _, _ ->
                    logs = repository.loadEntries()
                    waterGoal = Goals.waterTarget(context)
                },
                onDeleteLogEntry = { _ ->
                    logs = repository.loadEntries()
                    waterGoal = Goals.waterTarget(context)
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            "Insights" -> InsightsScreen(
                logs = logs,
                onNavigate = { dest -> navigateTo(dest) },
                onNavigateHome = { navigateTo("Home") },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            "History" -> {
                val categories = listOf("All", "Money", "Study", "Art", "Water", "Meal", "Care", "Face routine", "Phone usage")
                val query = historySearch.trim().lowercase(Locale.ROOT)
                val visibleHistory = logs.filter { entry ->
                    val categoryMatches = when (historyFilter) {
                        "All" -> true
                        "Money" -> entry.category == "Income" || entry.category == "Expense"
                        else -> entry.category.equals(historyFilter, ignoreCase = true)
                    }
                    val queryMatches = query.isBlank() ||
                        entry.title.lowercase(Locale.ROOT).contains(query) ||
                        entry.category.lowercase(Locale.ROOT).contains(query) ||
                        entry.note.lowercase(Locale.ROOT).contains(query) ||
                        entry.date.contains(query)
                    categoryMatches && queryMatches
                }
                LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                    item { SectionHeader("History & logs","Search, filter, edit or delete saved entries") }
                    item {
                        OutlinedTextField(
                            value = historySearch,
                            onValueChange = { historySearch = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search activity, note or date") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                    }
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.forEach { category ->
                                FilterChip(
                                    selected = historyFilter == category,
                                    onClick = { historyFilter = category },
                                    label = { Text(category) }
                                )
                            }
                        }
                    }
                    item { Text("${visibleHistory.size} matching entries stored on this device",color=Muted,fontSize=12.sp) }
                    if (visibleHistory.isEmpty()) item { EmptyCard("No matching entries","Try another search or category.") }
                    items(visibleHistory, key = { it.id }) { entry ->
                        ActivityRow(entry, onEdit = { openEdit(entry) }, onDelete = { deletingEntry = entry })
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                item { SectionHeader("Preferences","Private by default") }
                item {
                    SettingRow(
                        Icons.Default.Person,
                        "Personal profile",
                        "${profileName.ifBlank { "No name set" }} · Age ${profilePrefs.getInt("profile_age", 0).takeIf { it > 0 } ?: "—"} · ${profilePrefs.getInt("profile_height_cm", 0).takeIf { it > 0 }?.let { "$it cm" } ?: "Height not set"} · ${profilePrefs.getInt("profile_weight_kg", 0).takeIf { it > 0 }?.let { "$it kg" } ?: "Weight not set"}",
                        onClick = { profileReturnPage = "Settings"; page = "Setup" }
                    )
                }
                item { SettingRow(Icons.Default.Storage,"Local storage","Tracker entries are stored locally on this phone.") }
                item {
                    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Default.Notifications,null,tint=Mint)
                                Column(Modifier.weight(1f).padding(start=12.dp)) {
                                    Text("Water reminders",color=WhiteText,fontWeight=FontWeight.SemiBold)
                                    Text("Optional notifications; Android delivery time is approximate.",color=Muted,fontSize=11.sp)
                                }
                                Switch(checked=remindersEnabled,onCheckedChange={enabled ->
                                    if (enabled && android.os.Build.VERSION.SDK_INT >= 33 &&
                                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        waterNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        remindersEnabled=enabled
                                        profilePrefs.edit()
                                            .putBoolean("water_reminders_enabled", enabled)
                                            .putInt("reminder_interval", reminderInterval)
                                            .apply()
                                        scheduleWaterReminders(context,reminderInterval,enabled)
                                    }
                                })
                            }
                            Text("Reminder interval",color=WhiteText,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                listOf(30,60,90,120).forEach { minutes ->
                                    FilterChip(selected=reminderInterval==minutes,onClick={
                                        reminderInterval=minutes
                                        profilePrefs.edit()
                                            .putBoolean("water_reminders_enabled", remindersEnabled)
                                            .putInt("reminder_interval", minutes)
                                            .apply()
                                        scheduleWaterReminders(context,minutes,remindersEnabled)
                                    },label={Text(if(minutes<60) "${minutes} min" else "${minutes/60} hr")})
                                }
                            }
                            Text("Minimum 15 minutes. Notifications require permission on Android 13+.",color=Muted,fontSize=10.sp)
                        }
                    }
                }
                item {
                    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("LOCAL DATA OVERVIEW",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                            Text("${logs.size} tracker entries saved",color=WhiteText,fontSize=18.sp,fontWeight=FontWeight.Bold)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) { Text("Income",color=Muted,fontSize=11.sp); Text(logs.count { it.category=="Income" }.toString(),color=Mint,fontWeight=FontWeight.Bold) }
                                Column(Modifier.weight(1f)) { Text("Expenses",color=Muted,fontSize=11.sp); Text(logs.count { it.category=="Expense" }.toString(),color=Orange,fontWeight=FontWeight.Bold) }
                                Column(Modifier.weight(1f)) { Text("Study / Art",color=Muted,fontSize=11.sp); Text(logs.count { it.category=="Study" || it.category=="Art" }.toString(),color=Blue,fontWeight=FontWeight.Bold) }
                                Column(Modifier.weight(1f)) { Text("Water",color=Muted,fontSize=11.sp); Text(logs.count { it.category=="Water" }.toString(),color=Purple,fontWeight=FontWeight.Bold) }
                            }
                            Text("Counts cover tracker entries only; routines and hydration totals are stored separately.",color=Muted,fontSize=10.sp)
                        }
                    }
                }
                item { SettingRow(Icons.Default.Palette,"Appearance",if (LifeTrackColors.darkMode.value) "Dark theme · low-light palette" else "Light theme · blue and pastel accents",onClick={selectedAppearance=if(LifeTrackColors.darkMode.value) "Dark" else "Light";showAppearanceDialog=true}) }
                item { SettingRow(Icons.Default.Security,"Privacy","Data stays on this device unless you explicitly share an export or backup. Voice recognition may use your device's speech provider.") }
                item { SettingRow(Icons.Default.Info,"About LifeTrack","Version $appVersion · Personal study, habits, hydration and money tracker",onClick={showAboutDialog=true}) }
                item { SettingRow(Icons.Default.FileDownload,"Export CSV","Share a spreadsheet-friendly copy of your tracker entries.",onClick={exportCsv(context,logs)}) }
                item { SettingRow(Icons.Default.Backup,"Export full backup","Save a restorable JSON file with entries, profile, goals, routines, focus settings, money categories and Webling preferences.",onClick={createBackupLauncher.launch("LifeTrack-backup.json")}) }
                item { SettingRow(Icons.Default.Restore,"Restore full backup","Choose a LifeTrack JSON backup. Existing entries and supported settings are replaced only after confirmation.",onClick={restoreBackupLauncher.launch(arrayOf("*/*"))}) }
                if (backupMessage != null) item { Text(backupMessage!!,color=Orange,fontSize=12.sp) }
                item { Button(onClick={confirmClear=true},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF6A263B)),modifier=Modifier.fillMaxWidth()) { Text("Clear tracker entries") } }
            }
        }
    }

    // Floating App Launcher panel (above bottom nav)
    if (page != "Setup") {
        AppLauncherPanel(
            visible = showLauncher,
            currentPage = page,
            onNavigate = { dest ->
                navigateTo(dest)
            },
            onDismiss = { showLauncher = false }
        )
        // Floating Quick Add menu (Rotating Radial / Fan Menu)
        QuickAddMenu(
            visible = showQuickAdd,
            onSelect = { action ->
                showQuickAdd = false
                when (action.categoryOrRoute) {
                    "Expense", "Income" -> {
                        moneyInitialQuickAddType = action.categoryOrRoute
                        navigateTo("Money")
                    }
                    "Habit" -> navigateTo("Habits")
                    "Webling" -> navigateTo("Digital Webling")
                    "Note", "Notes" -> navigateTo("Notes")
                    "Task", "Tasks" -> navigateTo("Tasks")
                    "Water" -> navigateTo("Water")
                    "Study", "Focus" -> navigateTo("Study")
                    "Calendar" -> navigateTo("Calendar")
                    "More" -> navigateTo("More")
                    "Food" -> {
                        moneyInitialQuickAddType = "Expense"
                        navigateTo("Money")
                    }
                    "Mood" -> navigateTo("Habits & Routines")
                    else -> { cat = action.categoryOrRoute; entryDate = today; add = true }
                }
            },
            onDismiss = { showQuickAdd = false }
        )
    }

    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore LifeTrack backup?") },
            text = { Text("This will replace the current ${logs.size} tracker entries with ${pendingRestore!!.entries.size} entries from the selected backup. A full backup also replaces supported profile, routine, focus, money-category and companion settings. Export your current data first if you may need it.") },
            confirmButton = {
                TextButton(onClick = {
                    val restored = pendingRestore ?: return@TextButton
                    val safeRestoredEntries = EntryCodec.newestFirst(EntryCodec.uniqueById(restored.entries))
                    repository.saveEntries(safeRestoredEntries)
                    logs = safeRestoredEntries
                    if (restored.preferences != null) RoutineReminderScheduler.cancelAllCurrent(context)
                    LifeTrackBackup.restorePreferences(context, restored.preferences)
                    if (restored.preferences != null) {
                        // Preferences alone do not recreate WorkManager jobs; reschedule enabled routines.
                        RoutineReminderScheduler.restoreEnabled(context)
                        MoneyCategory.load(context)
                        profileName = profilePrefs.getString("profile_name", null) ?: profilePrefs.getString("name", "") ?: ""
                        val restoredThemeMode = profilePrefs.getString("theme_mode", "System") ?: "System"
                        val dark = when (restoredThemeMode) {
                            "Light" -> false
                            "Dark" -> true
                            else -> systemInDark
                        }
                        LifeTrackColors.darkMode.value = dark
                        selectedAppearance = restoredThemeMode
                        waterGoal = Goals.waterTarget(context)
                        studyGoal = Goals.studyTarget(context)
                        remindersEnabled = profilePrefs.getBoolean("water_reminders_enabled", false)
                        reminderInterval = profilePrefs.getInt("reminder_interval", 60).coerceIn(15, 240)
                        scheduleWaterReminders(context, reminderInterval, remindersEnabled)
                    }
                    pendingRestore = null
                    backupMessage = "Restored ${safeRestoredEntries.size} entries and supported settings."
                }) { Text("Restore backup") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
        )
    }
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("LifeTrack") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version $appVersion", color = Mint, fontWeight = FontWeight.Bold)
                    Text("A personal dashboard for study sessions, habits, hydration, daily activity and money management.", color = WhiteText)
                    Text("Your tracker entries are stored locally on this device. Use Backup & Restore before moving devices or reinstalling.", color = Muted, fontSize = 12.sp)
                    Text("Weather information is fetched from Open-Meteo when internet access is available.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            }
        )
    }
    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text("Appearance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Light", "Dark").forEach { option ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RadioButton(selected = selectedAppearance == option, onClick = { selectedAppearance = option })
                            Column {
                                Text(option, color = WhiteText, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when (option) {
                                        "System" -> "Follow device light/dark mode setting"
                                        "Light" -> "Bright background and blue accents"
                                        else -> "Dark surfaces for low-light use"
                                    },
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dark = when (selectedAppearance) {
                        "Light" -> false
                        "Dark" -> true
                        else -> systemInDark
                    }
                    val updatedSettings = UserProfileRepository.getSettings(context).copy(themeMode = selectedAppearance)
                    val resolved = if (selectedAppearance == "System") {
                        updatedSettings.copy(themeMode = if (systemInDark) "Dark" else "Light")
                    } else updatedSettings
                    LifeTrackColors.applySettings(resolved)
                    profilePrefs.edit().putBoolean("dark_theme", dark).putString("theme_mode", selectedAppearance).apply()
                    UserProfileRepository.saveSettings(context, updatedSettings)
                    showAppearanceDialog = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showAppearanceDialog = false }) { Text("Cancel") } }
        )
    }
    if(confirmClear) {
        AlertDialog(
            onDismissRequest={confirmClear=false},
            title={Text("Delete tracker entries?")},
            text={Text("This permanently deletes tracker entries from History. Routine definitions, hydration totals and reminder preferences are stored separately and are not cleared by this action. Export a CSV first if you need a copy.")},
            confirmButton={Button(onClick={
                logs=emptyList()
                repository.clearEntries()
                confirmClear=false
            },colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF6A263B))) { Text("Delete entries") }},
            dismissButton={TextButton(onClick={confirmClear=false}) { Text("Cancel") }}
        )
    }
    if (editingEntry != null) {
        val entry = editingEntry!!
        AlertDialog(
            onDismissRequest = { editingEntry = null; validationError = null },
            title = { Text("Edit entry", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${entry.category} · $editDate", Modifier.weight(1f), color = Muted, fontSize = 12.sp)
                        TextButton(onClick = { showIsoDatePicker(context, editDate) { editDate = it } }) { Text("Change date") }
                    }
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Name / activity") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (entry.category == "Expense" || entry.category == "Income") {
                        var editCategoryExpanded by remember(entry.id) { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = editCategoryExpanded, onExpandedChange = { editCategoryExpanded = !editCategoryExpanded }) {
                            OutlinedTextField(value = moneyCategory, onValueChange = {}, readOnly = true, label = { Text("Transaction category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(editCategoryExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded = editCategoryExpanded, onDismissRequest = { editCategoryExpanded = false }) {
                                MoneyCategory.optionsFor(entry.category).forEach { option ->
                                    DropdownMenuItem(text = { Text(option) }, onClick = { moneyCategory = option; editCategoryExpanded = false })
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = editAmount, onValueChange = { editAmount = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text(when (entry.category) { "Income", "Expense" -> "Amount in ₹"; "Water" -> "Water in ml"; "Study", "Art", "Phone usage" -> "Duration in minutes"; else -> "Amount / count" }) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editNote, onValueChange = { editNote = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                    if (validationError != null) Text(validationError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val error = EntryValidator.amountError(entry.category, editAmount)
                    if (error != null) {
                        validationError = error
                    } else {
                        val updated = logs.map {
                            if (it.id == entry.id) {
                                val newTitle = editTitle.ifBlank { defaultTitle(entry.category) }
                                it.copy(
                                    title = newTitle,
                                    amount = editAmount.trim(),
                                    date = editDate,
                                    note = editNote.trim(),
                                    moneyCategory = if (entry.category == "Expense" || entry.category == "Income") moneyCategory else it.moneyCategory
                                )
                            } else it
                        }
                        logs = updated
                        persistEntries(updated)
                        editingEntry = null
                        editDate = today
                        validationError = null
                    }
                }) { Text("Save changes") }
            },
            dismissButton = { TextButton(onClick = { editingEntry = null; validationError = null }) { Text("Cancel") } }
        )
    }
    if (deletingEntry != null) {
        val entry = deletingEntry!!
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text("Delete this entry?") },
            text = { Text("${entry.title} will be removed from your saved history.") },
            confirmButton = {
                Button(onClick = {
                    val updated = logs.filterNot { it.id == entry.id }
                    logs = updated
                    persistEntries(updated)
                    deletingEntry = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2635))) { Text("Delete entry") }
            },
            dismissButton = { TextButton(onClick = { deletingEntry = null }) { Text("Cancel") } }
        )
    }
    if(add) {
        AlertDialog(
            onDismissRequest={add=false; validationError=null; entryDate=today},
            title={Text("Add ${cat.lowercase()} entry",fontWeight=FontWeight.Bold)},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("Date: $entryDate", Modifier.weight(1f), color=Muted, fontSize=12.sp)
                        TextButton(onClick={showIsoDatePicker(context,entryDate){entryDate=it}}) { Text("Change date") }
                    }
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=!expanded}) {
                        OutlinedTextField(value=cat,onValueChange={},readOnly=true,label={Text("Category")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                            trackerNames.forEach { DropdownMenuItem(text={Text(it)},onClick={cat=it;expanded=false;title=""}) }
                        }
                    }
                    OutlinedTextField(value=title,onValueChange={title=it},label={Text("Name / activity")},placeholder={Text(defaultTitle(cat))},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    val titleSuggestions = when (cat) {
                        "Expense" -> listOf("Groceries", "Transport", "Bills", "Food", "Shopping")
                        "Income" -> listOf("Salary", "Freelance", "Interest", "Other income")
                        "Study" -> listOf("Focused study", "Revision", "Practice questions", "Reading")
                        "Water" -> listOf("Water", "After workout", "Morning hydration")
                        "Meal" -> listOf("Breakfast", "Lunch", "Dinner", "Snack")
                        else -> listOf(defaultTitle(cat), "Daily progress", "Other")
                    }.distinct()
                    Text("Quick picks", color=Muted, fontSize=11.sp, fontWeight=FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        titleSuggestions.forEach { suggestion ->
                            FilterChip(
                                selected = title.equals(suggestion, ignoreCase = true),
                                onClick = { title = suggestion },
                                label = { Text(suggestion, maxLines=1) }
                            )
                        }
                    }
                    if (cat == "Expense" || cat == "Income") {
                        var categoryExpanded by remember { mutableStateOf(false) }
                        var showNewCategory by remember { mutableStateOf(false) }
                        var newCategoryName by remember { mutableStateOf("") }
                        var newCategoryError by remember { mutableStateOf<String?>(null) }
                        var newCategoryIcon by remember { mutableStateOf("category") }
                        if (showNewCategory) {
                            AlertDialog(
                                onDismissRequest={showNewCategory=false},
                                title={Text("New category")},
                                text={
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value=newCategoryName,
                                        onValueChange={newCategoryName=it; newCategoryError=null},
                                        label={Text("Category name")},
                                        isError=newCategoryError!=null,
                                        supportingText={newCategoryError?.let { Text(it) }},
                                        singleLine=true
                                    )
                                    Text("Choose an icon", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(MoneyCategory.iconChoices) { iconKey ->
                                            FilterChip(
                                                selected = newCategoryIcon == iconKey,
                                                onClick = { newCategoryIcon = iconKey },
                                                label = { Icon(moneyCategoryIconByKey(iconKey), contentDescription = iconKey, modifier = Modifier.size(18.dp)) }
                                            )
                                        }
                                    }
                                    Text("You can use different icons for each custom category.", color = Muted, fontSize = 10.sp)
                                    }
                                },
                                confirmButton={TextButton(onClick={
                                    val err=MoneyCategory.addCustom(context,cat,newCategoryName,newCategoryIcon)
                                    if(err==null){ moneyCategory=newCategoryName.trim(); showNewCategory=false } else newCategoryError=err
                                }) { Text("Add") }},
                                dismissButton={TextButton(onClick={showNewCategory=false}) { Text("Cancel") }}
                            )
                        }
                        LaunchedEffect(cat) { moneyCategory = MoneyCategory.optionsFor(cat).firstOrNull() ?: "Other" }
                        ExposedDropdownMenuBox(expanded=categoryExpanded,onExpandedChange={categoryExpanded=!categoryExpanded}) {
                            OutlinedTextField(
                                value=moneyCategory,
                                onValueChange={},
                                readOnly=true,
                                label={Text("Transaction category")},
                                trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded)},
                                modifier=Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded=categoryExpanded,onDismissRequest={categoryExpanded=false}) {
                                MoneyCategory.optionsFor(cat).forEach { option ->
                                    DropdownMenuItem(text={Text(option)},leadingIcon={Icon(moneyCategoryIcon(option),contentDescription=null,tint=Mint)},onClick={
                                        moneyCategory=option
                                        categoryExpanded=false
                                    })
                                }
                                DropdownMenuItem(text={Text("＋ New category…",color=Mint)},onClick={
                                    categoryExpanded=false
                                    newCategoryName=""; newCategoryError=null; newCategoryIcon="category"; showNewCategory=true
                                })
                            }
                        }

                    }
                    val amountSuggestions = when (cat) {
                        "Expense" -> listOf("100", "250", "500", "1000")
                        "Income" -> listOf("500", "1000", "5000", "10000")
                        "Water" -> listOf("250", "350", "500", "750")
                        "Study", "Art", "Phone usage" -> listOf("15", "25", "45", "60")
                        else -> listOf("1", "2", "3", "5")
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        amountSuggestions.forEach { preset ->
                            SuggestionChip(onClick = { amount = preset }, label = { Text(if (cat == "Expense" || cat == "Income") "₹$preset" else preset) })
                        }
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { value -> amount = value.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text(when (cat) { "Expense", "Income" -> "Amount in ₹"; "Water" -> "Water in ml"; "Study", "Art", "Phone usage" -> "Duration in minutes"; else -> "Count" }) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if(cat=="Expense" || cat=="Income") Text("Choose a category that best describes this transaction.",color=Muted,fontSize=12.sp)
                    if(cat=="Water") Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("150","250","500","750").forEach { OutlinedButton(onClick={amount=it},contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp)) { Text("${it}ml") } } }
                    if(cat=="Study" || cat=="Art" || cat=="Phone usage") Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("15","30","60","90","120").forEach { OutlinedButton(onClick={amount=it},contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp)) { Text("${it}m") } } }
                    if(validationError!=null) Text(validationError!!,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
                    OutlinedTextField(value=note,onValueChange={note=it},label={Text("Notes (optional)")},modifier=Modifier.fillMaxWidth())
                }
            },
            confirmButton={Button(onClick={save()}) { Text("Save entry") }},
            dismissButton={TextButton(onClick={add=false; validationError=null; entryDate=today}) { Text("Cancel") }}
        )
    }
}

internal fun moneyCategoryIcon(category:String) = moneyCategoryIconByKey(MoneyCategory.iconKeyFor(category))

internal fun moneyCategoryIconByKey(key: String) = when(key.lowercase(Locale.ROOT)) {
    "restaurant" -> Icons.Default.Restaurant
    "transport" -> Icons.Default.DirectionsBus
    "shopping", "clothing" -> Icons.Default.ShoppingBag
    "electricity", "water" -> Icons.Default.Bolt
    "health", "fitness" -> Icons.Default.Favorite
    "education" -> Icons.Default.School
    "entertainment", "sports" -> Icons.Default.Movie
    "subscriptions", "phone", "wifi" -> Icons.Default.PhoneAndroid
    "travel", "car" -> Icons.Default.DirectionsCar
    "home" -> Icons.Default.Home
    "salary" -> Icons.Default.Work
    "laptop" -> Icons.Default.Laptop
    "pets" -> Icons.Default.Pets
    "gift" -> Icons.Default.CardGiftcard
    "savings" -> Icons.Default.Savings
    "tools" -> Icons.Default.Build
    "child" -> Icons.Default.ChildCare
    "beauty" -> Icons.Default.Face
    "coffee" -> Icons.Default.Coffee
    "insurance" -> Icons.Default.VerifiedUser
    "charity" -> Icons.Default.VolunteerActivism
    "other" -> Icons.Default.Replay
    else -> Icons.Default.Category
}
@Composable private fun QuickPresetRow(items:List<String>,onClick:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
        items.chunked(2).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { row.forEach { label -> OutlinedButton(onClick={onClick(label)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(13.dp)) { Text(label,fontSize=12.sp) } }; if(row.size==1) Spacer(Modifier.weight(1f)) } }
    }
}
private fun categoryTitle(category:String)=when(category) {"Study"->"Study Tracker";"Expense"->"Expenses Tracker";"Art"->"Art & Creative";"Water"->"Water & Food";"Meal"->"Water & Food";"Care"->"Personal Care";"Face routine"->"Face Routine";else->category}
private fun formatMins(mins:Int)=if(mins>=60) "${mins/60}:${(mins%60).toString().padStart(2,'0')}:00" else "00:${mins.toString().padStart(2,'0')}:00"

private val trackerNames=listOf("Study","Expense","Income","Water","Meal","Care","Face routine")
private fun defaultTitle(category:String)=when(category) {
    "Study"->"Study session";"Expense"->"Expense";"Income"->"Income";"Water"->"Water";"Meal"->"Meal";"Care"->"Personal care";"Face routine"->"Routine step";else->"Activity"
}
@Composable private fun DateStrip(today:String) {
    val date=SimpleDateFormat("EEE, dd MMM yyyy",Locale.ENGLISH).format(Date())
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(date.uppercase(),color=Muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp); Text("Small steps, big progress.",color=WhiteText,fontSize=14.sp) }
        Surface(color=Panel2,shape=RoundedCornerShape(14.dp)) { Row(Modifier.padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.CalendarToday,null,tint=Mint,modifier=Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text("Today",fontSize=12.sp,color=WhiteText) } }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DailyFocusCard(study:Int,water:Int,spent:Double,studyGoal:Int,waterGoal:Int,onEditGoals:()->Unit) {
    Card(onClick=onEditGoals,colors=CardDefaults.cardColors(containerColor=LifeTrackColors.SurfaceRaised),shape=RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("DAILY OVERVIEW",color=Mint,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp); Spacer(Modifier.height(4.dp)); Text("Make today count.",color=WhiteText,fontSize=22.sp,fontWeight=FontWeight.Bold) }
                Box(Modifier.size(48.dp).background(Mint.copy(alpha=.12f),CircleShape),contentAlignment=Alignment.Center) { Icon(Icons.Default.AutoAwesome,null,tint=Mint,modifier=Modifier.size(27.dp)) }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { Text("STUDY FOCUS",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold); Text(if(study>=60)"${study/60}h ${study%60}m" else "${study}m",color=WhiteText,fontSize=24.sp,fontWeight=FontWeight.Bold); Text("Goal ${Goals.hours(studyGoal)} · tap to edit",color=Muted,fontSize=11.sp) }
                Column(Modifier.weight(1f)) { Text("WATER INTAKE",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold); Text("%.1f L".format(Locale.US,water/1000f),color=WhiteText,fontSize=24.sp,fontWeight=FontWeight.Bold); Text("Goal ${Goals.litres(waterGoal)}",color=Muted,fontSize=11.sp) }
            }
            Text("Study goal progress",color=Muted,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
            LinearProgressIndicator(progress={(study / studyGoal.toFloat().coerceAtLeast(1f)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(7.dp),color=Mint,trackColor=Color(0xFFE5EAF5))
            Text("Water goal progress",color=Muted,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
            LinearProgressIndicator(progress={(water / waterGoal.toFloat().coerceAtLeast(1f)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(7.dp),color=Blue,trackColor=Color(0xFFE5EAF5))
            Text("₹${"%.0f".format(Locale.US,spent)} spent today · keep your goals in sight",color=Muted,fontSize=12.sp)
        }
    }
}
@Composable private fun MetricCard(title:String,value:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,tint:Color,modifier:Modifier) {
    Card(modifier,colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) { Box(Modifier.size(32.dp).background(tint.copy(alpha=.14f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) { Icon(icon,null,tint=tint,modifier=Modifier.size(18.dp)) }; Text(title,color=Muted,fontSize=12.sp,fontWeight=FontWeight.SemiBold) }
            Text(value,color=WhiteText,fontSize=21.sp,fontWeight=FontWeight.Bold)
            Text(subtitle,color=Muted,fontSize=10.sp)
        }
    }
}

@Composable
private fun InsightMetric(title: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = .09f)), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = WhiteText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

private val chartPalette = listOf(Color(0xFF315CFF), Color(0xFF20B982), Color(0xFFFF9B43), Color(0xFF8B5CF6), Color(0xFFF05B87), Color(0xFF2498F3), Color(0xFF14B8A6), Color(0xFF64748B))

@Composable private fun YesterdayTodayComparison(today: String, todayLogs: List<LogEntry>, allLogs: List<LogEntry>) {
    val yesterday = remember(today) {
        val cal = Calendar.getInstance()
        cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(today) ?: Date()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
    val yesterdayLogs = allLogs.filter { it.date == yesterday }
    val context = androidx.compose.ui.platform.LocalContext.current
    val metrics = listOf(Triple("Study", "min", "Study"), Triple("Water", "ml", "Water"), Triple("Expenses", "₹", "Expense"), Triple("Income", "₹", "Income"))
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("DAY-TO-DAY COMPARISON", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("Yesterday vs today", color = WhiteText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Only saved entries are compared; missing logs stay unreported.", color = Muted, fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Metric", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1.1f))
                Text("Yesterday", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Text("Today", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            metrics.forEach { (_, _, category) ->
                val label = when(category) { "Expense" -> "Expenses"; "Income" -> "Income"; else -> category }
                val yesterdayRows = yesterdayLogs.filter { it.category == category }
                val todayRows = todayLogs.filter { it.category == category }
                val hasYesterday = yesterdayRows.isNotEmpty()
                val hasToday = todayRows.isNotEmpty()
                fun amount(rows: List<LogEntry>): Double = rows.sumOf { MoneyAmounts.value(it.amount) }
                fun display(rows: List<LogEntry>, exists: Boolean): String {
                    if (!exists) return "No data"
                    val value = amount(rows)
                    return when (category) {
                        "Expense", "Income" -> "₹" + String.format(Locale.US, "%.0f", value)
                        "Water" -> if (value >= 1000) String.format(Locale.US, "%.1f L", value / 1000.0) else "${value.toInt()} ml"
                        else -> if (value >= 60) "${value.toInt() / 60}h ${value.toInt() % 60}m" else "${value.toInt()} min"
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, color = WhiteText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.1f))
                    Text(display(yesterdayRows, hasYesterday), color = if (hasYesterday) WhiteText else Muted, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text(display(todayRows, hasToday), color = if (hasToday) Mint else Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                if (category == "Study" || category == "Water") {
                    val goal = if (category == "Study") Goals.studyTarget(context) else Goals.waterTarget(context)
                    LinearProgressIndicator(progress = { (amount(todayRows).toFloat() / goal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp), color = if (category == "Study") Blue else Mint, trackColor = Muted.copy(alpha = .12f))
                }
                if (category != "Income") HorizontalDivider(color = Muted.copy(alpha = .12f))
            }
        }
    }
}

@Composable private fun MoneyCategoryDonut(totals: List<Pair<String, Double>>) {
    val total = totals.sumOf { it.second }.coerceAtLeast(0.0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(136.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                if (total > 0.0) {
                    var start = -90f
                    totals.forEachIndexed { index, item ->
                        val sweep = (item.second / total * 360.0).toFloat()
                        drawArc(color = chartPalette[index % chartPalette.size], startAngle = start, sweepAngle = sweep, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * .19f))
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TOTAL", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("₹" + String.format(Locale.US, "%.0f", total), color = WhiteText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${totals.size} categories", color = Muted, fontSize = 11.sp)
            Text("Largest expense", color = Muted, fontSize = 10.sp)
            Text(totals.maxByOrNull { it.second }?.first ?: "—", color = WhiteText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(totals.maxByOrNull { it.second }?.second?.let { "₹" + String.format(Locale.US, "%.0f", it) } ?: "₹0", color = Orange, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun WeeklyDashboardCharts(logs: List<LogEntry>, onOpenInsights: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val dayLabelFormat = remember { SimpleDateFormat("EE", Locale.US) }
    val days = (6 downTo 0).map { offset ->
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }.time
    }
    val labels = days.map { dayLabelFormat.format(it) }
    val dates = days.map { dateFormat.format(it) }
    val studyValues = dates.map { date -> logs.filter { it.date == date && it.category == "Study" }.sumOf { it.amount.toIntOrNull() ?: 0 } }
    val expenseValues = dates.map { date -> logs.filter { it.date == date && it.category == "Expense" }.sumOf { MoneyAmounts.value(it.amount) }.coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt() }
    Card(onClick = onOpenInsights, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR WEEK IN GRAPHS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Text("Last 7 days", color = WhiteText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.OpenInFull, contentDescription = "Open weekly insights", tint = Muted, modifier = Modifier.size(18.dp))
            }
            if (logs.any { it.category == "Study" }) {
                DashboardBarChart(title = "Study time", values = studyValues, labels = labels, tint = Blue, currency = false)
            } else {
                Text("No study entries in the last 7 days yet.", color = Muted, fontSize = 12.sp)
            }
            HorizontalDivider(color = Muted.copy(alpha = .18f))
            if (logs.any { it.category == "Expense" }) {
                DashboardBarChart(title = "Daily spending", values = expenseValues, labels = labels, tint = Orange, currency = true)
            } else {
                Text("No expenses in the last 7 days yet.", color = Muted, fontSize = 12.sp)
            }
            Text("Charts use your saved entries. Tap to explore weekly insights.", color = Muted, fontSize = 11.sp)
        }
    }
}
@Composable
private fun HomeProgressVisual(study: Int, water: Int, spent: Double, studyGoal: Int, waterGoal: Int, todayLogs: List<LogEntry>, allLogs: List<LogEntry>, onGoalsClick: () -> Unit, onMoneyClick: () -> Unit) {
    val totalIncome = allLogs.filter { it.category == "Income" }.sumOf { MoneyAmounts.value(it.amount) }
    val totalExpense = allLogs.filter { it.category == "Expense" }.sumOf { MoneyAmounts.value(it.amount) }
    val todayIncome = todayLogs.filter { it.category == "Income" }.sumOf { MoneyAmounts.value(it.amount) }
    val todayExpense = todayLogs.filter { it.category == "Expense" }.sumOf { MoneyAmounts.value(it.amount) }
    val studyProgress = if (studyGoal > 0) (study.toFloat() / studyGoal).coerceIn(0f, 1f) else 0f
    val waterProgress = if (waterGoal > 0) (water.toFloat() / waterGoal).coerceIn(0f, 1f) else 0f
    val balance = totalIncome - totalExpense
    Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TODAY AT A GLANCE", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Your day, visualized", color = WhiteText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(94.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { studyProgress }, modifier = Modifier.fillMaxSize(), strokeWidth = 8.dp, color = Blue, trackColor = Blue.copy(alpha = .12f))
                    CircularProgressIndicator(progress = { waterProgress }, modifier = Modifier.fillMaxSize().padding(12.dp), strokeWidth = 7.dp, color = Color(0xFF2498F3), trackColor = Color(0xFF2498F3).copy(alpha = .12f))
                    Text("${(studyProgress * 100).toInt()}%", color = Blue, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Study: ${study} / ${studyGoal} min", color = WhiteText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { studyProgress }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Blue)
                    Text("Water: ${String.format(Locale.US, "%.1f", water / 1000f)} / ${String.format(Locale.US, "%.1f", waterGoal / 1000f)} L", color = WhiteText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { waterProgress }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Color(0xFF2498F3))
                }
            }
            Text("Still to go today: ${(studyGoal - study).coerceAtLeast(0)} study minutes · ${(waterGoal - water).coerceAtLeast(0)} ml water", color = Muted, fontSize = 11.sp)
            Divider(color = Blue.copy(alpha = .12f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MONEY SNAPSHOT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Balance: ₹${String.format(Locale.US, "%.0f", balance)}", color = if (balance >= 0) Mint else Orange, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Income ₹${String.format(Locale.US, "%.0f", totalIncome)}", color = Mint, fontSize = 11.sp)
                    Text("Expenses ₹${String.format(Locale.US, "%.0f", totalExpense)}", color = Orange, fontSize = 11.sp)
                    Text("Today: +₹${String.format(Locale.US, "%.0f", todayIncome)} / −₹${String.format(Locale.US, "%.0f", todayExpense)}", color = Muted, fontSize = 10.sp)
                }
                HomeMoneyDonut(totalIncome, totalExpense, Modifier.size(78.dp))
            }
            Button(onClick = onMoneyClick, modifier = Modifier.fillMaxWidth()) { Text("Open money dashboard") }
            TextButton(onClick = onGoalsClick, modifier = Modifier.align(Alignment.End)) { Text("Adjust daily goals") }
        }
    }
}

@Composable
private fun HomeMoneyDonut(income: Double, expenses: Double, modifier: Modifier = Modifier) {
    val total = (income + expenses).takeIf { it > 0.0 } ?: 1.0
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Butt)
            val incomeSweep = (income / total * 360.0).toFloat()
            drawArc(color = Mint, startAngle = -90f, sweepAngle = incomeSweep, useCenter = false, style = stroke)
            drawArc(color = Orange, startAngle = -90f + incomeSweep, sweepAngle = (expenses / total * 360.0).toFloat(), useCenter = false, style = stroke)
        }
        Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Income and expenses", tint = Blue, modifier = Modifier.size(25.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeWeatherCard(profilePrefs: android.content.SharedPreferences) {
    var city by remember { mutableStateOf(profilePrefs.getString("weather_city", "Coimbatore")?.ifBlank { "Coimbatore" } ?: "Coimbatore") }
    var weather by remember { mutableStateOf(profilePrefs.getString("weather_city_label", null) ?: "Weather not checked") }
    var checkedAt by remember { mutableLongStateOf(profilePrefs.getLong("weather_city_checked_at", 0L)) }
    var loading by remember { mutableStateOf(false) }
    var refreshRequest by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCityDialog by remember { mutableStateOf(false) }
    var cityInput by remember { mutableStateOf(city) }

    suspend fun refreshWeather(force: Boolean) {
        if (loading) return
        if (!force && checkedAt > 0L && System.currentTimeMillis() - checkedAt < 30 * 60 * 1000L) return
        loading = true
        error = null
        val result = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                fetchLifeTrackWeather(city)
            }
        }
        result.onSuccess { label ->
            weather = label
            checkedAt = System.currentTimeMillis()
            profilePrefs.edit().putString("weather_city", city).putString("weather_city_label", label)
                .putLong("weather_city_checked_at", checkedAt).apply()
        }.onFailure {
            error = "Weather unavailable. Check internet and city name."
            if (checkedAt == 0L) weather = "Weather unavailable"
        }
        loading = false
    }

    LaunchedEffect(city, refreshRequest) { refreshWeather(force = refreshRequest > 0) }

    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Cloud, null, tint = Blue, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("WEATHER", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { cityInput = city; showCityDialog = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Text(city, fontSize = 10.sp)
                    }
                }
                Text(if (loading) "Checking live weather…" else weather, color = WhiteText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    when {
                        error != null -> error!!
                        checkedAt > 0L -> "Updated ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(checkedAt))}"
                        else -> "Live forecast from Open-Meteo"
                    },
                    color = if (error != null) Orange else Muted,
                    fontSize = 10.sp
                )
                TextButton(onClick = { refreshRequest++ }, enabled = !loading, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                    Text(if (loading) "Refreshing…" else "Refresh weather", fontSize = 11.sp)
                }
            }
            Icon(Icons.Default.WbSunny, null, tint = Orange, modifier = Modifier.size(24.dp))
        }
    }

    if (showCityDialog) {
        AlertDialog(
            onDismissRequest = { showCityDialog = false },
            title = { Text("Weather location") },
            text = {
                OutlinedTextField(
                    value = cityInput,
                    onValueChange = { cityInput = it.take(80) },
                    label = { Text("City or town") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleaned = cityInput.trim()
                    if (cleaned.isNotEmpty()) {
                        city = cleaned
                        profilePrefs.edit().putString("weather_city", cleaned).apply()
                        checkedAt = 0L
                        showCityDialog = false
                    }
                }) { Text("Save and refresh") }
            },
            dismissButton = { TextButton(onClick = { showCityDialog = false }) { Text("Cancel") } }
        )
    }
}

private fun getWeatherJson(url: String, userAgent: String? = null): org.json.JSONObject {
    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    connection.connectTimeout = 8000
    connection.readTimeout = 8000
    connection.requestMethod = "GET"
    connection.setRequestProperty("Accept", "application/json")
    if (userAgent != null) {
        connection.setRequestProperty("User-Agent", userAgent)
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        check(code in 200..299) { "Weather service returned HTTP $code" }
        return org.json.JSONObject(body)
    } finally {
        connection.disconnect()
    }
}

internal fun fetchLifeTrackWeatherByCoords(
    latitude: Double,
    longitude: Double,
    displayName: String
): String {
    val forecast = getWeatherJson("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code&timezone=auto")
    val current = forecast.getJSONObject("current")
    val temp = current.optDouble("temperature_2m", Double.NaN)
    val feels = current.optDouble("apparent_temperature", Double.NaN)
    val humidity = current.optInt("relative_humidity_2m", -1)
    val code = current.optInt("weather_code", -1)
    require(temp.isFinite()) { "Weather data unavailable" }
    val condition = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Current conditions"
    }
    val placeLabel = displayName.ifBlank { "Current Location" }
    val feelsLabel = if (feels.isFinite()) " · feels ${feels.toInt()}°C" else ""
    val humidityLabel = if (humidity >= 0) " · humidity $humidity%" else ""
    return "$placeLabel · ${temp.toInt()}°C · $condition$feelsLabel$humidityLabel"
}

internal fun fetchLifeTrackWeather(city: String): String {
    val trimmedCity = city.trim()
    val searchCity = if (trimmedCity.contains(",")) trimmedCity.substringBefore(",").trim() else trimmedCity
    val encodedCity = java.net.URLEncoder.encode(searchCity, Charsets.UTF_8.name())

    // 1. Try Open-Meteo Geocoding
    var lat: Double? = null
    var lon: Double? = null
    var resolvedName = trimmedCity

    try {
        val geo = getWeatherJson("https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json")
        val places = geo.optJSONArray("results")
        if (places != null && places.length() > 0) {
            val place = places.getJSONObject(0)
            lat = place.getDouble("latitude")
            lon = place.getDouble("longitude")
            val name = place.optString("name", trimmedCity)
            val country = place.optString("country", "")
            resolvedName = if (country.isNotBlank()) "$name, $country" else name
        }
    } catch (_: Exception) {}

    // 2. If Open-Meteo didn't find small town/village (e.g. Samusigapuram), try OpenStreetMap Nominatim
    if (lat == null || lon == null) {
        try {
            val nominatimUrl = "https://nominatim.openstreetmap.org/search?q=$encodedCity&format=json&limit=1"
            val conn = java.net.URL(nominatimUrl).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "LifeTrackAndroidApp/1.0 (contact@lifetrack.app)")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = org.json.JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    lat = obj.getString("lat").toDoubleOrNull()
                    lon = obj.getString("lon").toDoubleOrNull()
                    val disp = obj.optString("display_name", trimmedCity)
                    resolvedName = disp.split(",").take(2).joinToString(",").trim()
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
    }

    // 3. If coordinates resolved, fetch weather via exact coordinates
    if (lat != null && lon != null) {
        return fetchLifeTrackWeatherByCoords(lat, lon, resolvedName)
    }

    // Fallback: Default to Tamil Nadu coordinates if network resolution fails
    return fetchLifeTrackWeatherByCoords(11.0168, 76.9558, trimmedCity)
}

@Composable private fun DashboardBarChart(
    title: String,
    values: List<Int>,
    labels: List<String>,
    tint: Color,
    currency: Boolean
) {
    val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val total = values.sum()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = WhiteText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(if (currency) "₹${total} total" else if (total >= 60) "${total / 60}h ${total % 60}m total" else "${total} min total", color = tint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().height(78.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { index, value ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (value > 0) Text(if (currency) "₹$value" else value.toString(), color = Muted, fontSize = 8.sp, maxLines = 1)
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 3.dp).height(if (value == 0) 3.dp else (8f + (value.toFloat() / maxValue.toFloat()) * 38f).dp)
                            .background(if (value == 0) Muted.copy(alpha = .18f) else tint.copy(alpha = if (index == values.lastIndex) 1f else .58f), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            labels.forEach { label -> Text(label, modifier = Modifier.weight(1f), color = Muted, fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}
@Composable private fun SectionHeader(title:String,action:String?=null,onAction:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=WhiteText,fontSize=17.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
        if(action!=null) TextButton(onClick={onAction?.invoke()},contentPadding=PaddingValues(0.dp)) { Text(action,color=Mint,fontSize=12.sp) }
    }
}
@Composable private fun QuickGrid(onClick:(String)->Unit) {
    val items=listOf(
        Triple("Study",Icons.Default.MenuBook,Color(0xFF315CFF)),
        Triple("Water",Icons.Default.WaterDrop,Color(0xFF2498F3)),
        Triple("Expense",Icons.Default.AccountBalanceWallet,Color(0xFFF05B87)),
        Triple("Income",Icons.Default.Savings,Color(0xFF20B982)),
        Triple("Habit",Icons.Default.Favorite,Color(0xFF20B982)),
        Triple("Webling",Icons.Default.Pets,Color(0xFF8B5CF6))
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { (name,icon,tint) ->
                    Card(onClick={onClick(name)},modifier=Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(vertical=12.dp,horizontal=2.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.size(36.dp).background(tint.copy(alpha=.12f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) { Icon(icon,null,tint=tint,modifier=Modifier.size(20.dp)) }
                            Text(name,color=WhiteText,fontSize=11.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                        }
                    }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
@Composable private fun ActivityRow(
    e: LogEntry,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var menuExpanded by remember(e.id) { mutableStateOf(false) }
    val tint = when (e.category) {
        "Expense" -> Orange
        "Income" -> Blue
        "Water" -> Blue
        "Study" -> Blue
        "Art" -> Purple
        "Face routine" -> Pink
        else -> Orange
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(40.dp).background(tint.copy(alpha = .13f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(when (e.category) {
                    "Expense", "Income" -> Icons.Default.AccountBalanceWallet
                    "Water" -> Icons.Default.WaterDrop
                    "Meal" -> Icons.Default.Restaurant
                    "Art" -> Icons.Default.Brush
                    "Phone usage" -> Icons.Default.PhoneAndroid
                    "Care" -> Icons.Default.Spa
                    "Face routine" -> Icons.Default.Face
                    else -> Icons.Default.MenuBook
                }, null, tint = tint)
            }
            Column(Modifier.weight(1f)) {
                Text(e.title, color = WhiteText, fontWeight = FontWeight.SemiBold)
                Text("${e.category} · ${e.date}", color = Muted, fontSize = 10.sp)
                if (e.note.isNotBlank()) Text(e.note, color = Muted, fontSize = 11.sp, maxLines = 1)
            }
            Text(when (e.category) {
                "Expense" -> "−₹${e.amount}"
                "Income" -> "+₹${e.amount}"
                "Water" -> "${e.amount} ml"
                "Study", "Art", "Phone usage" -> "${e.amount} min"
                else -> e.amount
            }, color = tint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            if (onEdit != null || onDelete != null) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Entry actions", tint = Muted) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (onEdit != null) DropdownMenuItem(text = { Text("Edit entry") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; onEdit() })
                        if (onDelete != null) DropdownMenuItem(text = { Text("Delete entry") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuExpanded = false; onDelete() })
                    }
                }
            }
        }
    }
}
@Composable private fun EmptyCard(title:String,body:String) {
    Card(colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) { Text(title,color=WhiteText,fontWeight=FontWeight.Bold); Text(body,color=Muted,fontSize=12.sp) } }
}
@Composable private fun SettingRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,body:String,onClick:(()->Unit)?=null) {
    Card(onClick={onClick?.invoke()},enabled=onClick!=null,colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,tint=Mint)
            Column(Modifier.weight(1f)) { Text(title,color=WhiteText,fontWeight=FontWeight.SemiBold); Text(body,color=Muted,fontSize=11.sp) }
            if(onClick!=null) Icon(Icons.Default.ChevronRight,null,tint=Muted)
        }
    }
}
private fun exportCsv(context:Context,logs:List<LogEntry>) {
    val csv=buildString { appendLine("date,type,transaction_category,title,amount,note"); logs.forEach { e -> appendLine(listOf(e.date,e.category,e.moneyCategory,e.title,e.amount,e.note).joinToString(",") { cell ->
                val first = cell.firstOrNull()
                val safe = if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') "'" + cell else cell
                "\"" + safe.replace("\"", "\"\"") + "\""
            }) } }
    val i=Intent(Intent.ACTION_SEND).apply { type="text/csv"; putExtra(Intent.EXTRA_SUBJECT,"LifeTrack export"); putExtra(Intent.EXTRA_TEXT,csv) }
    context.startActivity(Intent.createChooser(i,"Export LifeTrack CSV"))
}
// End of LifeTrack CSV export helper.


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryManagementScreen(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var transactionType by remember { mutableStateOf("Expense") }
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newIcon by remember { mutableStateOf("category") }
    var iconForCategory by remember { mutableStateOf<String?>(null) }
    var selectedIcon by remember { mutableStateOf("category") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val custom = if (transactionType == "Income") MoneyCategory.customIncomes else MoneyCategory.customExpenses
    val builtIn = if (transactionType == "Income") MoneyCategory.incomes else MoneyCategory.expenses

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("Categories & icons", "Personalize your money tracker")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Expense", "Income").forEachIndexed { index, type ->
                SegmentedButton(
                    selected = transactionType == type,
                    onClick = { transactionType = type },
                    shape = SegmentedButtonDefaults.itemShape(index, 2)
                ) { Text(type) }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Built-in categories", color = WhiteText, fontWeight = FontWeight.Bold)
                Text("Default categories are protected. Create custom categories below and choose an icon for each.", color = Muted, fontSize = 12.sp)
                builtIn.forEach { name ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(Color(0xFFEAF0FF), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                            Icon(moneyCategoryIcon(name), contentDescription = null, tint = Mint)
                        }
                        Text(name, Modifier.weight(1f).padding(start = 10.dp), color = WhiteText, fontSize = 13.sp)
                        Text("Default", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your categories", color = WhiteText, fontWeight = FontWeight.Bold)
                Text("${custom.size} custom categories", color = Muted, fontSize = 11.sp)
            }
            Button(onClick = { newName = ""; newIcon = "category"; error = null; showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Add")
            }
        }
        if (custom.isEmpty()) {
            EmptyCard("No custom categories yet", "Add a category such as Rent, Pet care, or Side projects.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(custom.toList(), key = { "${transactionType}_$it" }) { name ->
                    val inUse = logs.any { it.moneyCategory.equals(name, ignoreCase = true) }
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(40.dp).background(Color(0xFFEAF0FF), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(moneyCategoryIconByKey(MoneyCategory.iconKeyFor(name)), contentDescription = name, tint = Mint)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(name, color = WhiteText, fontWeight = FontWeight.SemiBold)
                                Text(if (inUse) "Used by saved transactions" else "Custom category", color = Muted, fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                iconForCategory = name
                                selectedIcon = MoneyCategory.iconKeyFor(name)
                            }) { Text("Icon") }
                            IconButton(onClick = { pendingDelete = name }, enabled = !inUse) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete $name", tint = if (inUse) Muted else Color(0xFFD64B5D))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add custom category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it; error = null }, label = { Text("Category name") }, singleLine = true, isError = error != null)
                    Text("Choose an icon", color = Muted, fontSize = 12.sp)
                    LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(MoneyCategory.iconChoices.chunked(5)) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { key ->
                                    FilledTonalIconButton(
                                        onClick = { newIcon = key },
                                        modifier = Modifier.weight(1f),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = if (newIcon == key) Mint.copy(alpha = 0.22f) else Panel2)
                                    ) { Icon(moneyCategoryIconByKey(key), contentDescription = key, tint = Mint) }
                                }
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = MoneyCategory.addCustom(context, transactionType, newName, newIcon)
                    if (result == null) showAdd = false else error = result
                }) { Text("Save category") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    if (iconForCategory != null) {
        AlertDialog(
            onDismissRequest = { iconForCategory = null },
            title = { Text("Choose icon") },
            text = {
                LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(MoneyCategory.iconChoices.chunked(5)) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { key ->
                                FilledTonalIconButton(
                                    onClick = { selectedIcon = key },
                                    modifier = Modifier.weight(1f),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = if (selectedIcon == key) Mint.copy(alpha = 0.22f) else Panel2)
                                ) { Icon(moneyCategoryIconByKey(key), contentDescription = key, tint = Mint) }
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = iconForCategory
                    if (name != null) {
                        MoneyCategory.customIconKeys[name] = selectedIcon
                        context.getSharedPreferences("lifetrack_money", Context.MODE_PRIVATE).edit()
                            .putString("category_icon_$name", selectedIcon).apply()
                    }
                    iconForCategory = null
                }) { Text("Save icon") }
            },
            dismissButton = { TextButton(onClick = { iconForCategory = null }) { Text("Cancel") } }
        )
    }

    if (pendingDelete != null) {
        val name = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete category?") },
            text = { Text("Delete the custom category '$name'? Existing transactions are preserved, but the category will no longer appear in the picker.") },
            confirmButton = {
                TextButton(onClick = {
                    val prefs = context.getSharedPreferences("lifetrack_money", Context.MODE_PRIVATE)
                    val key = if (transactionType == "Income") "custom_income_categories" else "custom_expense_categories"
                    val list = if (transactionType == "Income") MoneyCategory.customIncomes else MoneyCategory.customExpenses
                    list.remove(name)
                    prefs.edit()
                        .putString(key, org.json.JSONArray(list.toList()).toString())
                        .remove("category_icon_$name")
                        .apply()
                    MoneyCategory.customIconKeys.remove(name)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun IncomeExpenseGroupedChart(
    income: List<Int>,
    expenses: List<Int>,
    labels: List<String>
) {
    val count = minOf(labels.size, income.size, expenses.size)
    if (count == 0) {
        Text("No chart data for this period.", color = Muted, fontSize = 12.sp)
        return
    }
    val safeIncome = income.take(count).map { it.coerceAtLeast(0).toFloat() }
    val safeExpenses = expenses.take(count).map { it.coerceAtLeast(0).toFloat() }
    val maximum = (safeIncome + safeExpenses).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(148.dp)) {
            val left = 4.dp.toPx()
            val right = size.width - 4.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val chartHeight = bottom - top
            val groupWidth = (right - left) / count
            val barWidth = (groupWidth * 0.25f).coerceAtLeast(2.dp.toPx())
            val gap = 2.dp.toPx()
            repeat(4) { tick ->
                val y = top + chartHeight * tick / 3f
                drawLine(
                    color = Muted.copy(alpha = 0.13f),
                    start = androidx.compose.ui.geometry.Offset(left, y),
                    end = androidx.compose.ui.geometry.Offset(right, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            for (i in 0 until count) {
                val center = left + groupWidth * (i + 0.5f)
                val incomeHeight = chartHeight * safeIncome[i] / maximum
                val expenseHeight = chartHeight * safeExpenses[i] / maximum
                drawRoundRect(
                    color = Mint,
                    topLeft = androidx.compose.ui.geometry.Offset(center - barWidth - gap / 2f, bottom - incomeHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, incomeHeight.coerceAtLeast(1.dp.toPx())),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = Orange,
                    topLeft = androidx.compose.ui.geometry.Offset(center + gap / 2f, bottom - expenseHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, expenseHeight.coerceAtLeast(1.dp.toPx())),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.take(count).forEach { label ->
                Text(label, color = Muted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LifeTrackTrendChart(values: List<Float>, labels: List<String>, tint: Color, filled: Boolean) {
    val count = minOf(values.size, labels.size)
    if (count == 0) {
        Text("No chart data for this period.", color = Muted, fontSize = 12.sp)
        return
    }
    val safe = values.take(count).map { if (it.isFinite()) it.coerceAtLeast(0f) else 0f }
    val maxValue = safe.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val left = 5.dp.toPx()
            val right = size.width - 5.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val h = bottom - top
            val w = (right - left).coerceAtLeast(1f)
            repeat(4) { i ->
                val y = top + h * i / 3f
                drawLine(Muted.copy(alpha=.14f), androidx.compose.ui.geometry.Offset(left,y), androidx.compose.ui.geometry.Offset(right,y), 1.dp.toPx())
            }
            val points = safe.mapIndexed { index, value ->
                val x = if (count == 1) (left + right) / 2f else left + w * index / (count - 1).toFloat()
                val y = bottom - h * value / maxValue
                androidx.compose.ui.geometry.Offset(x,y)
            }
            if (filled && points.size > 1) {
                val area = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points.first().x,bottom)
                    points.forEach { lineTo(it.x,it.y) }
                    lineTo(points.last().x,bottom)
                    close()
                }
                drawPath(area,tint.copy(alpha=.22f))
            }
            if (points.size > 1) {
                for (i in 0 until points.lastIndex) drawLine(tint,points[i],points[i+1],2.5.dp.toPx(),cap=StrokeCap.Round)
            }
            points.forEach { point ->
                drawCircle(tint,3.5.dp.toPx(),point)
                drawCircle(Panel,1.5.dp.toPx(),point)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            labels.forEachIndexed { index, label ->
                if (count <= 8 || index == 0 || index == count-1 || index % (count/4).coerceAtLeast(1) == 0) Text(label,color=Muted,fontSize=9.sp,maxLines=1)
            }
        }
    }
}
