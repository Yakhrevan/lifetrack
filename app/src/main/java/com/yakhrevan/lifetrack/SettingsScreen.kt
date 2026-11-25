package com.yakhrevan.lifetrack

import android.content.Context
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors

// ==========================================
// 1. DESIGN TOKENS
// ==========================================
private val DarkBackground get() = LifeTrackColors.Background
private val CardBackground get() = LifeTrackColors.Surface
private val CardInner      get() = LifeTrackColors.SurfaceRaised
private val CardBorder     get() = LifeTrackColors.Border
private val PurpleBrand    get() = LifeTrackColors.Primary
private val CyanBrand      get() = LifeTrackColors.Blue
private val EmeraldBrand   get() = Color(0xFF10B981)
private val AmberBrand     get() = LifeTrackColors.Orange
private val CoralBrand     get() = LifeTrackColors.Pink
private val TextWhite      get() = LifeTrackColors.TextPrimary
private val TextMuted      get() = LifeTrackColors.TextSecondary

private val ACCENT_COLORS = listOf(
    "#A855F7", "#06B6D4", "#3B82F6", "#10B981", "#EAB308", "#F97316", "#F43F5E", "#EC4899"
)

// ==========================================
// 2. MAIN SETTINGS ROUTER
// ==========================================

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    onNavigateToCloudSync: () -> Unit = {},
    initialSubScreen: String = "Main"
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    var currentSubScreen by remember(initialSubScreen) { mutableStateOf(initialSubScreen) }
    // Sub-screens: "Main", "Appearance", "Notifications", "Personalisation", "DataBackup", "AppPreferences", "Privacy", "About"
    var settings by remember { mutableStateOf(UserProfileRepository.getSettings(context)) }

    fun updateSettings(newSettings: AppSettings) {
        NotificationPolicy.onSettingsChanged(context, settings, newSettings)
        UserProfileRepository.saveSettings(context, newSettings)
        settings = newSettings
        val resolvedSettings = if (newSettings.themeMode == "System") {
            newSettings.copy(themeMode = if (isSystemDark) "Dark" else "Light")
        } else newSettings
        LifeTrackColors.applySettings(resolvedSettings)
        val dark = LifeTrackColors.darkMode.value
        context.getSharedPreferences("lifetrack_profile", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("dark_theme", dark)
            .putString("theme_mode", newSettings.themeMode)
            .apply()
    }

    BackHandler(enabled = currentSubScreen != "Main") {
        currentSubScreen = "Main"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        when (currentSubScreen) {
            "Appearance" -> AppearanceSettingsScreen(
                settings = settings,
                onUpdate = { updateSettings(it) },
                onBack = { currentSubScreen = "Main" }
            )
            "Notifications" -> NotificationSettingsScreen(
                settings = settings,
                onUpdate = { updateSettings(it) },
                onBack = { currentSubScreen = "Main" }
            )
            "Personalisation" -> PersonalisationScreen(
                settings = settings,
                onUpdate = { updateSettings(it) },
                onBack = { currentSubScreen = "Main" },
                onNavigateToProfile = onNavigateToProfile
            )
            "DataBackup" -> DataBackupScreen(
                onBack = { currentSubScreen = "Main" },
                onNavigateToCloudSync = onNavigateToCloudSync
            )
            "AppPreferences" -> AppPreferencesScreen(
                settings = settings,
                onUpdate = { updateSettings(it) },
                onBack = { currentSubScreen = "Main" }
            )
            "Privacy" -> PrivacySecurityScreen(
                onBack = { currentSubScreen = "Main" }
            )
            "About" -> AboutLifeTrackScreen(
                onBack = { currentSubScreen = "Main" }
            )
            else -> SettingsMainContent(
                onOpenSubScreen = { currentSubScreen = it },
                onNavigateBack = onNavigateBack,
                onNavigateToCategories = onNavigateToCategories,
                onNavigateToCloudSync = onNavigateToCloudSync
            )
        }
    }
}

// ==========================================
// 3. REUSABLE SETTINGS SUB-HEADER
// ==========================================

@Composable
internal fun SettingsSubHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = CardInner,
            shape = CircleShape,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onBack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column {
            Text(
                title,
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ==========================================
// 4. SCREEN 7: SETTINGS MAIN MENU
// ==========================================

@Composable
private fun SettingsMainContent(
    onOpenSubScreen: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToCloudSync: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SettingsSubHeader(
            title = "Settings",
            subtitle = "Preferences, appearance & system",
            onBack = onNavigateBack
        )

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                item { SettingsSectionLabel("APP") }
                item {
                    SettingsRowItem(
                        icon = Icons.Default.Palette,
                        iconTint = PurpleBrand,
                        title = "Appearance",
                        subtitle = "Theme, colors, text size",
                        onClick = { onOpenSubScreen("Appearance") }
                    )
                }
                item {
                    SettingsRowItem(
                        icon = Icons.Default.Notifications,
                        iconTint = AmberBrand,
                        title = "Notifications",
                        subtitle = "Reminders, quiet hours",
                        onClick = { onOpenSubScreen("Notifications") }
                    )
                }
                item {
                    SettingsRowItem(
                        icon = Icons.Default.Tune,
                        iconTint = CyanBrand,
                        title = "Personalisation",
                        subtitle = "Units, date & time, language",
                        onClick = { onOpenSubScreen("Personalisation") }
                    )
                }
                item { SettingsSectionLabel("DATA & SECURITY") }
                item {
                    SettingsRowItem(
                        icon = Icons.Default.CloudSync,
                        iconTint = CyanBrand,
                        title = "Backup & Cloud Sync",
                        subtitle = "Google sign-in, daily backup & snapshots",
                        onClick = onNavigateToCloudSync
                    )
                }
                item {
                    SettingsRowItem(
                        icon = Icons.Default.Backup,
                        iconTint = EmeraldBrand,
                        title = "Data & Backup",
                        subtitle = "Export, import, restore",
                        onClick = { onOpenSubScreen("DataBackup") }
                    )
                }
                item {
                    SettingsRowItem(
                        icon = Icons.Default.Security,
                        iconTint = CoralBrand,
                        title = "Privacy & Security",
                        subtitle = "App lock, permissions",
                        onClick = { onOpenSubScreen("Privacy") }
                    )
                }
                item { SettingsSectionLabel("MORE") }
                item {
                    SettingsRowItem(
                        icon = Icons.Default.Category,
                        iconTint = Color(0xFF6366F1),
                        title = "Manage Categories",
                        subtitle = "Tags, folders and more",
                        onClick = onNavigateToCategories
                    )
                }
                item {
                    SettingsRowItem(
                        icon = Icons.Default.DisplaySettings,
                        iconTint = Color(0xFF38BDF8),
                        title = "App Preferences",
                        subtitle = "Behaviour and advanced options",
                        onClick = { onOpenSubScreen("AppPreferences") }
                    )
                }
                item {
                    SettingsRowItem(
                        icon = Icons.Default.Info,
                        iconTint = Color(0xFFA855F7),
                        title = "About LifeTrack",
                        subtitle = "Version, help & licences",
                        onClick = { onOpenSubScreen("About") }
                    )
                }

                item {
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.brand_lyratech_transparent),
                            contentDescription = "LYRATECH",
                            modifier = Modifier.height(28.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Crafted with precision by LYRATECH",
                            color = TextMuted.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 0.dp)
    )
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = CardBackground,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextMuted, fontSize = 11.sp)
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

// ==========================================
// 4. SCREEN 8: APPEARANCE SETTINGS
// ==========================================

@Composable
fun AppearanceSettingsScreen(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SettingsSubHeader(
            title = "Appearance Settings",
            subtitle = "Themes, dark mode & accent colors",
            onBack = onBack
        )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(LifeTrackColors.CompactGap),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                // 1. Theme Mode: 3 Cards (System, Light, Dark)
                item {
                    Text("Theme Mode", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            Triple("System", Icons.Default.BrightnessAuto, "System"),
                            Triple("Light", Icons.Default.LightMode, "Light"),
                            Triple("Dark", Icons.Default.DarkMode, "Dark")
                        ).forEach { (label, icon, value) ->
                            val isSelected = settings.themeMode == value
                            Surface(
                                color = if (isSelected) PurpleBrand.copy(alpha = 0.25f) else CardBackground,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, if (isSelected) PurpleBrand else CardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onUpdate(settings.copy(themeMode = value)) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = label,
                                        tint = if (isSelected) PurpleBrand else TextMuted,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        label,
                                        color = if (isSelected) Color.White else TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Accent Color Palette
                item {
                    Text("Accent Color", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ACCENT_COLORS) { hex ->
                            val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(PurpleBrand)
                            val isSelected = settings.accentColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onUpdate(settings.copy(accentColorHex = hex)) }
                            )
                        }
                    }
                }

                // 3. Text Size Slider
                item {
                    Text("Text Size", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("A", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Slider(
                            value = settings.textSizeMultiplier,
                            onValueChange = { onUpdate(settings.copy(textSizeMultiplier = it)) },
                            valueRange = 0.85f..1.15f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = PurpleBrand,
                                activeTrackColor = PurpleBrand,
                                inactiveTrackColor = CardInner
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text("A", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 4. Dashboard Style
                item {
                    Text("Dashboard Style", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Compact", "Comfortable").forEach { style ->
                            val isSelected = settings.dashboardStyle == style
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSelected) PurpleBrand else Color.Transparent)
                                    .clickable { onUpdate(settings.copy(dashboardStyle = style)) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    style,
                                    color = if (isSelected) Color.White else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // 5. Switches: Enable Animations & Reduce Motion
                item {
                    SettingsSwitchRow(
                        title = "Enable Animations",
                        subtitle = "Smooth transitions and micro-interactions",
                        checked = settings.enableAnimations,
                        onCheckedChange = { onUpdate(settings.copy(enableAnimations = it)) }
                    )
                }

                item {
                    SettingsSwitchRow(
                        title = "Reduce Motion (Accessibility)",
                        subtitle = "Minimise movement effects across screens",
                        checked = settings.reduceMotion,
                        onCheckedChange = { onUpdate(settings.copy(reduceMotion = it)) }
                    )
                }
            }
        }
    }

// ==========================================
// 5. SCREEN 9: NOTIFICATION SETTINGS
// ==========================================

@Composable
fun NotificationSettingsScreen(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SettingsSubHeader(
            title = "Notification Settings",
            subtitle = "Reminders, alerts & quiet hours",
            onBack = onBack
        )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Master Toggle
                item {
                    SettingsSwitchRow(
                        title = "Enable Notifications",
                        subtitle = "Allow LifeTrack to deliver scheduled reminders",
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { onUpdate(settings.copy(notificationsEnabled = it)) }
                    )
                }

                item {
                    ReminderTimePreferenceRow("Default Reminder Time", "Used when creating new reminders", settings.defaultReminderTime) {
                        onUpdate(settings.copy(defaultReminderTime = it))
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSwitchRow(
                            title = "Quiet Hours",
                            subtitle = "Keep notifications visible, but silent during this time",
                            checked = settings.quietHoursEnabled,
                            onCheckedChange = { onUpdate(settings.copy(quietHoursEnabled = it)) }
                        )
                        if (settings.quietHoursEnabled) {
                            ReminderTimePreferenceRow("Quiet hours start", "Sound turns off", settings.quietHoursStart) {
                                onUpdate(settings.copy(quietHoursStart = it))
                            }
                            ReminderTimePreferenceRow("Quiet hours end", "Sound returns", settings.quietHoursEnd) {
                                onUpdate(settings.copy(quietHoursEnd = it))
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Default snooze", color = TextWhite, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15, 30).forEach { minutes ->
                                FilterChip(
                                    selected = settings.snoozeDurationMinutes == minutes,
                                    onClick = { onUpdate(settings.copy(snoozeDurationMinutes = minutes)) },
                                    label = { Text("${minutes}m") }
                                )
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSwitchRow(
                            title = "Precise task reminders",
                            subtitle = "Use exact Android alarms when permitted; otherwise keep WorkManager fallback",
                            checked = settings.preciseTaskAlarms,
                            onCheckedChange = { enabled ->
                                onUpdate(settings.copy(preciseTaskAlarms = enabled))
                                if (enabled && !PreciseTaskAlarmScheduler.isAvailable(context)) {
                                    runCatching {
                                        context.startActivity(android.content.Intent(
                                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        ))
                                    }
                                }
                                ReminderReconciliationScheduler.ensureScheduled(context)
                            }
                        )
                        Text(if (PreciseTaskAlarmScheduler.isAvailable(context)) "Android exact-alarm access available"
                            else "Grant Alarms & reminders special access for minute-accurate task alerts",
                            color = TextMuted, fontSize = 11.sp)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSwitchRow(
                            title = "Smart follow-ups",
                            subtitle = "Bounded repeat alerts for incomplete priority tasks",
                            checked = settings.taskFollowUpEnabled,
                            onCheckedChange = { onUpdate(settings.copy(taskFollowUpEnabled = it)) }
                        )
                        if (settings.taskFollowUpEnabled) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 60).forEach { minutes ->
                                FilterChip(selected = settings.taskFollowUpMinutes == minutes,
                                    onClick = { onUpdate(settings.copy(taskFollowUpMinutes = minutes)) },
                                    label = { Text("${minutes}m") })
                            }
                        }
                        if (settings.taskFollowUpEnabled) {
                            SettingsSwitchRow(
                                title = "High-priority only",
                                subtitle = "Use follow-ups only for tasks marked High",
                                checked = settings.taskFollowUpHighPriorityOnly,
                                onCheckedChange = { onUpdate(settings.copy(taskFollowUpHighPriorityOnly = it)) }
                            )
                            Text("Maximum follow-ups", color = TextWhite, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 3).forEach { count ->
                                    FilterChip(selected = settings.taskFollowUpCount == count,
                                        onClick = { onUpdate(settings.copy(taskFollowUpCount = count)) },
                                        label = { Text("$count") })
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notification sound", color = TextWhite, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Chime", "System", "Silent").forEach { sound ->
                                FilterChip(
                                    selected = settings.notificationSound == sound,
                                    onClick = { onUpdate(settings.copy(notificationSound = sound)) },
                                    label = { Text(sound) }
                                )
                            }
                        }
                        Text("Soft LifeTrack chime, phone default sound, or silent. Android system channel overrides still apply.", color = TextMuted, fontSize = 11.sp)
                    }
                }
                item {
                    OutlinedButton(onClick = {
                        if (!NotificationPolicy.canNotify(context, NotificationPolicy.Module.TASKS)) {
                            Toast.makeText(context, "Enable Task notifications and allow Android notification permission first", Toast.LENGTH_LONG).show()
                        } else {
                            val test = NotificationPolicy.builder(context, NotificationPolicy.Module.TASKS, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
                                .setContentTitle("LifeTrack notification test")
                                .setContentText("Your reminder sound and logo are ready")
                                .build()
                            try {
                                NotificationManagerCompat.from(context).notify(90011, test)
                            } catch (_: SecurityException) {
                                Toast.makeText(context, "Android blocked notification permission", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Send test notification")
                    }
                }

                // Module Notifications Section
                item {
                    Text("Module Notifications", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground, RoundedCornerShape(16.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModuleNotificationRow("Habits", Icons.Default.Spa, EmeraldBrand, settings.notifyHabits) { onUpdate(settings.copy(notifyHabits = it)) }
                        ModuleNotificationRow("Routines", Icons.Default.Schedule, CyanBrand, settings.notifyRoutines) { onUpdate(settings.copy(notifyRoutines = it)) }
                        ModuleNotificationRow("Tasks", Icons.Default.CheckCircle, PurpleBrand, settings.notifyTasks) { onUpdate(settings.copy(notifyTasks = it)) }
                        ModuleNotificationRow("Calendar", Icons.Default.Event, CyanBrand, settings.notifyCalendar) { onUpdate(settings.copy(notifyCalendar = it)) }
                        ModuleNotificationRow("Notes", Icons.Default.Note, CoralBrand, settings.notifyNotes) { onUpdate(settings.copy(notifyNotes = it)) }
                        ModuleNotificationRow("Water", Icons.Default.WaterDrop, Color(0xFF00B4D8), settings.notifyWater) { onUpdate(settings.copy(notifyWater = it)) }
                        ModuleNotificationRow("Study", Icons.Default.MenuBook, AmberBrand, settings.notifyStudy) { onUpdate(settings.copy(notifyStudy = it)) }
                        ModuleNotificationRow("Digital Wellbeing", Icons.Default.PhoneAndroid, Color(0xFF6366F1), settings.notifyDigitalWellbeing) { onUpdate(settings.copy(notifyDigitalWellbeing = it)) }
                    }
                }
            }
        }
    }

@Composable
private fun ModuleNotificationRow(
    title: String,
    icon: ImageVector,
    tint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(tint.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurpleBrand)
        )
    }
}

/** Shared native picker: no typing or locale-specific HH:mm mistakes. */
@Composable
fun ReminderTimeButton(label: String, time: String, onSelected: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val parts = time.split(':')
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 20
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        TimePickerDialog(context, { _, h, m ->
            onSelected("%02d:%02d".format(java.util.Locale.US, h, m))
        }, hour, minute, false).show()
    }) {
        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("$label: $time")
    }
}

@Composable
private fun ReminderTimePreferenceRow(title: String, subtitle: String, time: String, onTimeChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CardBackground, RoundedCornerShape(14.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        ReminderTimeButton("Set", time, onTimeChange)
    }
}

// ==========================================
// 6. SCREEN 10: PERSONALISATION & UNITS
// ==========================================

@Composable
fun PersonalisationScreen(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SettingsSubHeader(
            title = "Personalisation",
            subtitle = "Profile, avatar & daily preferences",
            onBack = onBack
        )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Avatar & Profile Icon
                item {
                    Surface(
                        color = CardBackground,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToProfile() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(PurpleBrand.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = PurpleBrand, modifier = Modifier.size(24.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Avatar & Profile Icon", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Choose from LifeTrack, neutral, male, female or custom photo", color = TextMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                        }
                    }
                }

                // App Language
                item {
                    PersonalisationValueRow("App Language", settings.appLanguage) { /* Language selection */ }
                }

                // Date Format
                item {
                    PersonalisationValueRow("Date Format", settings.dateFormat) { /* Date format */ }
                }

                // Time Format
                item {
                    PersonalisationValueRow("Time Format", settings.timeFormat) {
                        val next = if (settings.timeFormat == "12-hour") "24-hour" else "12-hour"
                        onUpdate(settings.copy(timeFormat = next))
                    }
                }

                // Week Starts On
                item {
                    PersonalisationValueRow("Week Starts On", settings.weekStartsOn) {
                        val next = if (settings.weekStartsOn == "Monday") "Sunday" else "Monday"
                        onUpdate(settings.copy(weekStartsOn = next))
                    }
                }

                // First Day of Week in Calendar
                item {
                    Text("First Day of Week in Calendar", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Monday", "Sunday").forEach { day ->
                            val isSelected = settings.calendarFirstDay == day
                            Surface(
                                color = if (isSelected) PurpleBrand else CardBackground,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) PurpleBrand else CardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onUpdate(settings.copy(calendarFirstDay = day)) }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        day,
                                        color = if (isSelected) Color.White else TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Measurement Units Section
                item {
                    Text("Measurement Units", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground, RoundedCornerShape(16.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        UnitRow("Weight", settings.weightUnit) {
                            val next = if (settings.weightUnit == "kg") "lbs" else "kg"
                            onUpdate(settings.copy(weightUnit = next))
                        }
                        UnitRow("Height", settings.heightUnit) {
                            val next = if (settings.heightUnit == "cm") "ft" else "cm"
                            onUpdate(settings.copy(heightUnit = next))
                        }
                        UnitRow("Water Intake", settings.waterUnit) {
                            val next = if (settings.waterUnit == "ml") "oz" else "ml"
                            onUpdate(settings.copy(waterUnit = next))
                        }
                        UnitRow("Temperature", settings.tempUnit) {
                            val next = if (settings.tempUnit == "°C") "°F" else "°C"
                            onUpdate(settings.copy(tempUnit = next))
                        }
                    }
                }
            }
        }
    }

@Composable
private fun PersonalisationValueRow(title: String, value: String, onClick: () -> Unit) {
    Surface(
        color = CardBackground,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value, color = CyanBrand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun UnitRow(label: String, currentUnit: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextWhite, fontSize = 13.sp)
        Text(currentUnit, color = PurpleBrand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ==========================================
// 7. SCREEN 11: DATA & BACKUP
// ==========================================

@Composable
fun DataBackupScreen(
    onBack: () -> Unit,
    onNavigateToCloudSync: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { LifeTrackRepository(context) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open storage.")
                val logs = repository.loadEntries()
                output.bufferedWriter().use { it.write(LifeTrackBackup.encode(context, logs)) }
            }.onSuccess {
                backupStatus = "Backup saved successfully."
            }.onFailure {
                backupStatus = "Failed to save: ${it.message}"
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read backup file.")
                val data = LifeTrackBackup.decode(raw)
                repository.saveEntries(data.entries)
            }.onSuccess {
                backupStatus = "Backup restored successfully."
            }.onFailure {
                backupStatus = "Restore failed: ${it.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SettingsSubHeader(
            title = "Data & Backup",
            subtitle = "Local backup, CSV export & cloud sync",
            onBack = onBack
        )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    BackupActionRow(
                        title = "Cloud Backup & Sync",
                        subtitle = "Automatic daily backups & cross-device sync",
                        icon = Icons.Default.CloudSync,
                        iconTint = PurpleBrand,
                        onClick = onNavigateToCloudSync
                    )
                }

                item {
                    BackupActionRow(
                        title = "Export Data",
                        subtitle = "Export all your data (JSON)",
                        icon = Icons.Default.CloudDownload,
                        iconTint = CyanBrand,
                        onClick = { createBackupLauncher.launch("LifeTrack-export.json") }
                    )
                }

                item {
                    BackupActionRow(
                        title = "Import Data",
                        subtitle = "Import from a backup file",
                        icon = Icons.Default.CloudUpload,
                        iconTint = Color(0xFF6366F1),
                        onClick = { restoreBackupLauncher.launch(arrayOf("*/*")) }
                    )
                }

                item {
                    BackupActionRow(
                        title = "Create Backup",
                        subtitle = "Save a backup file to storage",
                        icon = Icons.Default.Archive,
                        iconTint = AmberBrand,
                        onClick = { createBackupLauncher.launch("LifeTrack-backup.json") }
                    )
                }

                item {
                    BackupActionRow(
                        title = "Restore Backup",
                        subtitle = "Restore from a backup file",
                        icon = Icons.Default.Restore,
                        iconTint = EmeraldBrand,
                        onClick = { restoreBackupLauncher.launch(arrayOf("*/*")) }
                    )
                }

                if (backupStatus != null) {
                    item {
                        Text(backupStatus!!, color = CyanBrand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    Text("Data Management", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                item {
                    BackupActionRow(
                        title = "Clear Cached Data",
                        subtitle = "Free up storage space",
                        icon = Icons.Default.CleaningServices,
                        iconTint = TextMuted,
                        onClick = {
                            context.cacheDir.deleteRecursively()
                            backupStatus = "Cache cleared successfully."
                        }
                    )
                }

                item {
                    BackupActionRow(
                        title = "Delete All Data",
                        subtitle = "Permanently remove all data",
                        icon = Icons.Default.DeleteForever,
                        iconTint = CoralBrand,
                        titleColor = CoralBrand,
                        onClick = { showDeleteConfirm = true }
                    )
                }
            }
        }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = CardBackground,
            title = { Text("Delete All Data?", color = CoralBrand, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove all your tasks, notes, habits, routines, and tracker entries on this device. This cannot be undone.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        repository.saveEntries(emptyList())
                        showDeleteConfirm = false
                        backupStatus = "All tracker data reset."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralBrand)
                ) {
                    Text("Delete Everything", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

@Composable
private fun BackupActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    titleColor: Color = TextWhite,
    onClick: () -> Unit
) {
    Surface(
        color = CardBackground,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextMuted, fontSize = 11.sp)
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

// ==========================================
// 8. SCREEN 12: APP PREFERENCES
// ==========================================

@Composable
fun AppPreferencesScreen(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        SettingsSubHeader(
            title = "App Preferences",
            subtitle = "Start page, feedback & behavior defaults",
            onBack = onBack
        )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    SettingsRowItem(
                        icon = Icons.Default.ViewAgenda,
                        iconTint = CyanBrand,
                        title = "Home Screen",
                        subtitle = "Customise visible cards and layout",
                        onClick = {}
                    )
                }

                item {
                    SettingsRowItem(
                        icon = Icons.Default.Launch,
                        iconTint = EmeraldBrand,
                        title = "Start Page",
                        subtitle = "Open '${settings.startPage}' on launch",
                        onClick = {
                            val next = when (settings.startPage) {
                                "Home" -> "Tasks"
                                "Tasks" -> "Notes"
                                "Notes" -> "Calendar"
                                else -> "Home"
                            }
                            onUpdate(settings.copy(startPage = next))
                        }
                    )
                }

                item {
                    SettingsSwitchRow(
                        title = "Haptic Feedback",
                        subtitle = "Gentle vibrations on interactions",
                        checked = settings.hapticFeedback,
                        onCheckedChange = { onUpdate(settings.copy(hapticFeedback = it)) }
                    )
                }

                item {
                    SettingsSwitchRow(
                        title = "Sound Effects",
                        subtitle = "Play subtle sounds when completing tasks",
                        checked = settings.soundEffects,
                        onCheckedChange = { onUpdate(settings.copy(soundEffects = it)) }
                    )
                }

                item {
                    SettingsSwitchRow(
                        title = "Auto Save",
                        subtitle = "Save changes automatically without manual prompts",
                        checked = settings.autoSave,
                        onCheckedChange = { onUpdate(settings.copy(autoSave = it)) }
                    )
                }

                item {
                    SettingsSwitchRow(
                        title = "Confirm Before Delete",
                        subtitle = "Show warning dialog before deleting records",
                        checked = settings.confirmBeforeDelete,
                        onCheckedChange = { onUpdate(settings.copy(confirmBeforeDelete = it)) }
                    )
                }

                item {
                    SettingsSwitchRow(
                        title = "Show Tips",
                        subtitle = "Display helpful guidance on empty views",
                        checked = settings.showTips,
                        onCheckedChange = { onUpdate(settings.copy(showTips = it)) }
                    )
                }
            }
        }
    }

// ==========================================
// 9. PRIVACY & ABOUT SCREENS
// ==========================================

@Composable
fun PrivacySecurityScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSubHeader(
            title = "Privacy & Security",
            subtitle = "On-device storage & zero cloud tracking",
            onBack = onBack
        )

            Surface(
                color = CardBackground,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Zero Cloud Tracking", color = EmeraldBrand, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "LifeTrack stores all your tasks, notes, habits, routines, hydration logs and financial records 100% locally on this device. No remote telemetry or user tracking is enabled.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }

@Composable
fun AboutLifeTrackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "5.0.0" }.getOrDefault("5.0.0")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSubHeader(
            title = "About LifeTrack",
            subtitle = "Version $appVersion · Built with Jetpack Compose",
            onBack = onBack
        )

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = "LifeTrack Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Text("LifeTrack", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Version $appVersion", color = CyanBrand, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

            Text(
                "A cohesive, premium personal productivity and wellness system with reliable offline persistence and complete user control.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(10.dp))

            // LYRATECH Brand Showcase Card
            Surface(
                color = CardBackground,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "ENGINEERED BY",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Image(
                        painter = painterResource(R.drawable.brand_lyratech_transparent),
                        contentDescription = "LYRATECH Logo",
                        modifier = Modifier.height(56.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        "Pioneering Intelligent Productivity Systems",
                        color = CyanBrand,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "© 2026 LYRATECH. All rights reserved.",
                        color = TextMuted.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = CardBackground,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextMuted, fontSize = 11.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurpleBrand)
            )
        }
    }
}
