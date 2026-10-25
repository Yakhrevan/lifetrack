package com.yakhrevan.lifetrack

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// 1. DESIGN TOKENS
// ==========================================
private val DarkBackground = Color(0xFF090D1A)
private val CardBackground = Color(0xFF121929)
private val CardInner      = Color(0xFF1B2438)
private val CardBorder     = Color(0x1FFFFFFF)
private val PurpleBrand    = Color(0xFFA855F7)
private val CyanBrand      = Color(0xFF06B6D4)
private val EmeraldBrand   = Color(0xFF10B981)
private val AmberBrand     = Color(0xFFF59E0B)
private val CoralBrand     = Color(0xFFF43F5E)
private val TextWhite      = Color(0xFFF1F5F9)
private val TextMuted      = Color(0xFF94A3B8)

// ==========================================
// 2. MAIN BACKUP & SYNC SCREEN ROUTER
// ==========================================

@Composable
fun BackupSyncScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onImportBackupFile: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentView by remember { mutableStateOf("Dashboard") } 
    // Views: "SignIn", "Dashboard", "ManualBackup", "Progress", "Success", "RestoreData", "DetailedSettings", "StorageInfo", "ManageData", "Notifications", "History"

    var account by remember { mutableStateOf(CloudSyncRepository.getAccount(context)) }
    var preferences by remember { mutableStateOf(CloudSyncRepository.getPreferences(context)) }
    var syncState by remember { mutableStateOf(CloudSyncRepository.determineSyncState(context)) }
    var storageBreakdown by remember { mutableStateOf(CloudSyncRepository.calculateStorageBreakdown(context)) }
    var lastSnapshot by remember { mutableStateOf(CloudSyncRepository.getSnapshots(context).firstOrNull()) }

    fun refreshAll() {
        account = CloudSyncRepository.getAccount(context)
        preferences = CloudSyncRepository.getPreferences(context)
        syncState = CloudSyncRepository.determineSyncState(context)
        storageBreakdown = CloudSyncRepository.calculateStorageBreakdown(context)
        lastSnapshot = CloudSyncRepository.getSnapshots(context).firstOrNull()
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank()) {
                val displayName = accountName.substringBefore("@")
                    .replace(".", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                val newAcc = CloudAccount(
                    isConnected = true,
                    email = accountName,
                    displayName = displayName,
                    lastSyncTimestamp = System.currentTimeMillis()
                )
                CloudSyncRepository.saveAccount(context, newAcc)
                refreshAll()
                Toast.makeText(context, "Connected as $accountName", Toast.LENGTH_SHORT).show()
                if (currentView == "SignIn") currentView = "Dashboard"
            } else {
                Toast.makeText(context, "No account selected", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Sign-in cancelled — remaining in local-first mode", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchGoogleAccountChooser() {
        try {
            val intent = AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null
            )
            accountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Google Account service not available on this device", Toast.LENGTH_LONG).show()
        }
    }

    BackHandler(enabled = currentView != "Dashboard") {
        currentView = "Dashboard"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
    ) {
        when (currentView) {
        "SignIn" -> SignInWithGoogleScreen(
            onSignedIn = { email, name ->
                val newAcc = CloudAccount(
                    isConnected = true,
                    email = email,
                    displayName = name,
                    lastSyncTimestamp = System.currentTimeMillis()
                )
                CloudSyncRepository.saveAccount(context, newAcc)
                refreshAll()
                currentView = "Dashboard"
            },
            onLaunchChooser = { launchGoogleAccountChooser() },
            onBack = { currentView = "Dashboard" }
        )
        "ManualBackup" -> ManualBackupScreen(
            onStartBackup = { currentView = "Progress" },
            onBack = { currentView = "Dashboard" }
        )
        "Progress" -> BackupProgressScreen(
            onCompleted = {
                refreshAll()
                currentView = "Success"
            },
            onFailed = { currentView = "Dashboard" }
        )
        "Success" -> BackupSuccessScreen(
            snapshot = lastSnapshot,
            onDone = {
                refreshAll()
                currentView = "Dashboard"
            }
        )
        "RestoreData" -> RestoreDataScreen(
            snapshot = lastSnapshot,
            onRestoreComplete = {
                refreshAll()
                currentView = "Dashboard"
            },
            onImportBackupFile = onImportBackupFile,
            onBack = { currentView = "Dashboard" }
        )
        "DetailedSettings" -> DetailedSettingsScreen(
            preferences = preferences,
            onUpdate = {
                CloudSyncRepository.savePreferences(context, it)
                BackupSyncScheduler.scheduleDailyBackup(context)
                refreshAll()
            },
            onBack = { currentView = "Dashboard" }
        )
        "StorageInfo" -> StorageDataInfoScreen(
            breakdown = storageBreakdown,
            onBack = { currentView = "Dashboard" }
        )
        "ManageData" -> ManageDataScreen(
            onNavigateToHistory = { currentView = "History" },
            onNavigateToRestore = { currentView = "RestoreData" },
            onDataDeleted = { refreshAll() },
            onBack = { currentView = "Dashboard" }
        )
        "Notifications" -> SyncNotificationsScreen(
            preferences = preferences,
            onUpdate = {
                CloudSyncRepository.savePreferences(context, it)
                refreshAll()
            },
            onBack = { currentView = "Dashboard" }
        )
        "History" -> BackupHistoryScreen(
            onRestoreSnapshot = { snapshot ->
                CloudSyncRepository.restoreFromSnapshot(context, snapshot)
                refreshAll()
                currentView = "Dashboard"
            },
            onBack = { currentView = "ManageData" }
        )
        else -> SyncDashboardScreen(
            account = account,
            preferences = preferences,
            syncState = syncState,
            storageBreakdown = storageBreakdown,
            lastSnapshot = lastSnapshot,
            onOpenSignIn = { currentView = "SignIn" },
            onSwitchAccount = { launchGoogleAccountChooser() },
            onSignOut = {
                CloudSyncRepository.signOut(context)
                refreshAll()
                Toast.makeText(context, "Signed out. Local notes, tasks, and data preserved.", Toast.LENGTH_SHORT).show()
            },
            onOpenManualBackup = { currentView = "ManualBackup" },
            onOpenRestore = { currentView = "RestoreData" },
            onOpenDetailedSettings = { currentView = "DetailedSettings" },
            onOpenStorageInfo = { currentView = "StorageInfo" },
            onOpenManageData = { currentView = "ManageData" },
            onOpenNotifications = { currentView = "Notifications" },
            onToggleAutoBackup = { enabled ->
                val updated = preferences.copy(autoBackupEnabled = enabled)
                CloudSyncRepository.savePreferences(context, updated)
                BackupSyncScheduler.scheduleDailyBackup(context)
                refreshAll()
            },
            onToggleSyncAcrossDevices = { enabled ->
                val updated = preferences.copy(syncAcrossDevices = enabled)
                CloudSyncRepository.savePreferences(context, updated)
                refreshAll()
            },
            onNavigateBack = onNavigateBack,
            modifier = Modifier.fillMaxSize()
        )
    }
}
}

// ==========================================
// 3. SCREEN 1: SIGN IN WITH GOOGLE
// ==========================================

@Composable
fun SignInWithGoogleScreen(
    onSignedIn: (String, String) -> Unit,
    onLaunchChooser: () -> Unit,
    onBack: () -> Unit
) {
    val isDark = LifeTrackColors.darkMode.value
    val screenBackground = if (isDark) Color(0xFF090D1A) else LifeTrackColors.Background
    val cardBackground = if (isDark) Color(0xFF121929) else LifeTrackColors.Surface
    val innerBackground = if (isDark) Color(0xFF1B2438) else LifeTrackColors.SurfaceRaised
    val primaryText = if (isDark) Color(0xFFF1F5F9) else LifeTrackColors.TextPrimary
    val secondaryText = if (isDark) Color(0xFF94A3B8) else LifeTrackColors.TextSecondary

    Surface(color = screenBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = primaryText)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Account & backup",
                    color = primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(CyanBrand.copy(alpha = 0.28f), PurpleBrand.copy(alpha = 0.08f), Color.Transparent)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (isDark) Color(0xFF1B2B40) else Color(0xFFE8F2FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = if (isDark) CyanBrand else Color(0xFF1769AA),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Text(
                    "Your LifeTrack,\nin sync.",
                    color = primaryText,
                    fontSize = 30.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Choose a Google account to continue to backup settings.",
                    color = secondaryText,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }

            Surface(
                color = cardBackground,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, if (isDark) Color(0x1FFFFFFF) else LifeTrackColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(innerBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = PurpleBrand, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Connect your account", color = primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Account selection happens on your device.", color = secondaryText, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clickable(onClick = onLaunchChooser)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // A compact branded G mark without requiring a remote image or extra SDK.
                            Text("G", color = Color(0xFF4285F4), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.width(12.dp))
                            Text("Continue with Google", color = Color(0xFF202124), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    HorizontalDivider(color = if (isDark) Color(0x1FFFFFFF) else LifeTrackColors.Border)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureCheckRow("Your existing on-device data stays on this device", primaryText, EmeraldBrand)
                        FeatureCheckRow("You can disconnect the account later", primaryText, EmeraldBrand)
                        FeatureCheckRow("Review backup settings before enabling scheduled backups", primaryText, EmeraldBrand)
                    }
                }
            }

            Surface(
                color = innerBackground,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = secondaryText, modifier = Modifier.size(18.dp))
                    Text(
                        "This screen currently opens Android's Google account picker. Selecting an account alone does not authenticate with Google or upload your LifeTrack data.",
                        color = secondaryText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Text(
                "LifeTrack keeps your records local unless you explicitly configure a backup.",
                color = secondaryText,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun FeatureCheckRow(
    text: String,
    textColor: Color = TextWhite,
    iconColor: Color = EmeraldBrand
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = iconColor, modifier = Modifier.size(17.dp))
        Text(text, color = textColor, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

// ==========================================
// 4. SCREEN 2 & 3: BACKUP & SYNC DASHBOARD
// ==========================================

@Composable
fun SyncDashboardScreen(
    account: CloudAccount,
    preferences: SyncPreferences,
    syncState: SyncState,
    storageBreakdown: StorageBreakdown,
    lastSnapshot: BackupSnapshot?,
    onOpenSignIn: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
    onOpenManualBackup: () -> Unit,
    onOpenRestore: () -> Unit,
    onOpenDetailedSettings: () -> Unit,
    onOpenStorageInfo: () -> Unit,
    onOpenManageData: () -> Unit,
    onOpenNotifications: () -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onToggleSyncAcrossDevices: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = DarkBackground, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text("Backup & Sync", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Default.Notifications, contentDescription = "Sync Notifications", tint = TextWhite, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onOpenDetailedSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = PurpleBrand, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                // 1. Explicit Google Account Card
                item {
                    Surface(
                        color = if (account.isConnected) EmeraldBrand.copy(alpha = 0.12f) else CardBackground,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (account.isConnected) EmeraldBrand.copy(alpha = 0.35f) else CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (account.isConnected) EmeraldBrand.copy(alpha = 0.2f) else PurpleBrand.copy(alpha = 0.2f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (account.isConnected) Icons.Default.AccountCircle else Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = if (account.isConnected) EmeraldBrand else PurpleBrand,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (account.isConnected) account.email else "Account Not Connected",
                                        color = TextWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (account.isConnected) "${account.displayName.ifBlank { "User" }} · Authenticated (Auth Only)" else "Local-first mode · No account linked",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (account.isConnected) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onSwitchAccount,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, CyanBrand.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanBrand),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Switch Account", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    OutlinedButton(
                                        onClick = onSignOut,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, CoralBrand.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralBrand),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Sign Out", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onOpenSignIn,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Connect Google Account", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 2. Clear distinction: Backup & Sync Status (Not configured / Local-first)
                item {
                    Surface(
                        color = CardBackground,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetailedSettings)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(AmberBrand.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = AmberBrand, modifier = Modifier.size(18.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Backup & Sync: Not Configured",
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Local-first storage active. Data remains safely on this device.",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Central Graphic & Sync Status (Screen 3: "Everything is up to date")
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            if (syncState == SyncState.UP_TO_DATE) EmeraldBrand.copy(alpha = 0.25f) else CyanBrand.copy(alpha = 0.25f),
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(CardBackground, CircleShape)
                                    .border(1.dp, CardBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    when (syncState) {
                                        SyncState.UP_TO_DATE -> Icons.Default.CloudDone
                                        SyncState.OFFLINE -> Icons.Default.CloudOff
                                        SyncState.SYNC_PENDING -> Icons.Default.CloudUpload
                                        else -> Icons.Default.Cloud
                                    },
                                    contentDescription = null,
                                    tint = if (syncState == SyncState.UP_TO_DATE) EmeraldBrand else CyanBrand,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Text(syncState.label, color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                        val lastSyncTimeStr = if (account.lastSyncTimestamp > 0L) {
                            "Last synced ${SimpleDateFormat("hh:mm a", Locale.US).format(Date(account.lastSyncTimestamp))}"
                        } else {
                            "No cloud sync recorded yet"
                        }
                        Text(lastSyncTimeStr, color = TextMuted, fontSize = 12.sp)
                    }
                }

                // Status Info 3-Card Grid
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val lastBackupFormatted = if (lastSnapshot != null) {
                            SimpleDateFormat("hh:mm a", Locale.US).format(Date(lastSnapshot.timestamp))
                        } else "—"

                        DashboardMiniCard(
                            title = "Last Backup",
                            value = lastBackupFormatted,
                            icon = Icons.Default.Schedule,
                            tint = CyanBrand,
                            modifier = Modifier.weight(1f)
                        )

                        DashboardMiniCard(
                            title = "Pending Changes",
                            value = "${CloudSyncRepository.getPendingChangesCount(LocalContext.current)} items",
                            icon = Icons.Default.Sync,
                            tint = AmberBrand,
                            modifier = Modifier.weight(1f)
                        )

                        DashboardMiniCard(
                            title = "Total Data",
                            value = formatBytes(storageBreakdown.totalBytes),
                            icon = Icons.Default.Storage,
                            tint = PurpleBrand,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenStorageInfo
                        )
                    }
                }

                // Automatic Backup Toggle & Config Rows
                item {
                    Surface(
                        color = CardBackground,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Automatic Backup Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Automatic Backup", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Back up your data automatically every day", color = TextMuted, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = preferences.autoBackupEnabled,
                                    onCheckedChange = onToggleAutoBackup,
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurpleBrand)
                                )
                            }

                            Divider(color = CardBorder, thickness = 0.5.dp)

                            // Backup Frequency Row
                            DashboardConfigRow(
                                title = "Backup Frequency",
                                value = preferences.backupFrequency,
                                icon = Icons.Default.CalendarToday,
                                onClick = onOpenDetailedSettings
                            )

                            // Network Policy Row
                            DashboardConfigRow(
                                title = "Use Network",
                                value = preferences.networkPolicy,
                                icon = Icons.Default.Wifi,
                                onClick = onOpenDetailedSettings
                            )

                            Divider(color = CardBorder, thickness = 0.5.dp)

                            // Sync Across Devices Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Sync Across Devices", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Keep your data updated on all your devices", color = TextMuted, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = preferences.syncAcrossDevices,
                                    onCheckedChange = onToggleSyncAcrossDevices,
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurpleBrand)
                                )
                            }
                        }
                    }
                }

                // Action Buttons: Back Up Now & Secondary Actions
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onOpenManualBackup,
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Back Up Now", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onOpenRestore,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, tint = CyanBrand, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restore Data", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = onOpenManageData,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, tint = AmberBrand, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Manage Data", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardMiniCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = CardBackground,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(title, color = TextMuted, fontSize = 9.sp)
            Text(value, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DashboardConfigRow(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = CyanBrand, modifier = Modifier.size(16.dp))
            Text(title, color = TextWhite, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = PurpleBrand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
        }
    }
}

// ==========================================
// 5. SCREEN 4: MANUAL BACKUP
// ==========================================

@Composable
fun ManualBackupScreen(
    onStartBackup: () -> Unit,
    onBack: () -> Unit
) {
    Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Back Up Now", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Center Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.radialGradient(listOf(PurpleBrand.copy(alpha = 0.35f), Color.Transparent)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(CardBackground, CircleShape)
                            .border(1.5.dp, PurpleBrand, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = PurpleBrand, modifier = Modifier.size(42.dp))
                    }
                }

                Text("Ready to back up", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Your data will be securely serialized and saved with an encrypted snapshot.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Checklist of included data
                Surface(
                    color = CardBackground,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("This includes:", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        BackupModuleIncludeItem("Profile and preferences", Icons.Default.Person, PurpleBrand)
                        BackupModuleIncludeItem("Goals, tasks and checklists", Icons.Default.CheckCircle, EmeraldBrand)
                        BackupModuleIncludeItem("Notes, rich text and folders", Icons.Default.Note, CoralBrand)
                        BackupModuleIncludeItem("Habits and daily routines", Icons.Default.Spa, CyanBrand)
                        BackupModuleIncludeItem("Water and study records", Icons.Default.WaterDrop, Color(0xFF00B4D8))
                    }
                }
            }

            // Bottom Start Button
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onStartBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Start Backup", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text("This may take a few seconds.", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BackupModuleIncludeItem(title: String, icon: ImageVector, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(tint.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Text(title, color = TextWhite, fontSize = 12.sp)
    }
}

// ==========================================
// 6. SCREEN 6: BACKUP PROGRESS
// ==========================================

@Composable
fun BackupProgressScreen(
    onCompleted: () -> Unit,
    onFailed: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) } // 1: Preparing, 2: Uploading, 3: Finalizing, 4: Saving

    LaunchedEffect(Unit) {
        delay(600)
        currentStep = 2
        delay(800)
        currentStep = 3
        val result = CloudSyncRepository.performBackup(context)
        delay(600)
        currentStep = 4
        delay(400)
        if (result.isSuccess) {
            onCompleted()
        } else {
            onFailed()
        }
    }

    Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Circular Progress Indicator
            Box(
                modifier = Modifier.size(130.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { (currentStep / 4f) },
                    strokeWidth = 6.dp,
                    color = PurpleBrand,
                    trackColor = CardInner,
                    modifier = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = PurpleBrand, modifier = Modifier.size(36.dp))
                    Text("${(currentStep * 25)}%", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Uploading your data...", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Please keep the app open.", color = TextMuted, fontSize = 12.sp)

            Spacer(Modifier.height(32.dp))

            // Step Progress Checklist
            Surface(
                color = CardBackground,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressStepRow("Preparing data", isDone = currentStep > 1, isActive = currentStep == 1)
                    ProgressStepRow("Uploading records", isDone = currentStep > 2, isActive = currentStep == 2)
                    ProgressStepRow("Finalizing", isDone = currentStep > 3, isActive = currentStep == 3)
                    ProgressStepRow("Saving backup info", isDone = currentStep >= 4, isActive = currentStep == 4)
                }
            }
        }
    }
}

@Composable
private fun ProgressStepRow(label: String, isDone: Boolean, isActive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (isDone) EmeraldBrand else if (isActive) PurpleBrand else CardInner,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            } else if (isActive) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
            }
        }
        Text(
            label,
            color = if (isDone || isActive) TextWhite else TextMuted,
            fontSize = 13.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ==========================================
// 7. SCREEN 7: BACKUP SUCCESS
// ==========================================

@Composable
fun BackupSuccessScreen(
    snapshot: BackupSnapshot?,
    onDone: () -> Unit
) {
    Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(10.dp))

            // Center Celebration Card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .background(
                            Brush.radialGradient(listOf(EmeraldBrand.copy(alpha = 0.35f), Color.Transparent)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(CardBackground, CircleShape)
                            .border(1.5.dp, EmeraldBrand, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldBrand, modifier = Modifier.size(46.dp))
                    }
                }

                Text("Backup completed!", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Your data has been successfully backed up with a secure point-in-time snapshot.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Metadata Card
                Surface(
                    color = CardBackground,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SuccessMetaRow("Backup time", SimpleDateFormat("hh:mm a", Locale.US).format(Date()), Icons.Default.Schedule, CyanBrand)
                        SuccessMetaRow("Total data", snapshot?.formattedSize ?: "Calculated", Icons.Default.Storage, PurpleBrand)
                        SuccessMetaRow("Next backup", "Tomorrow (Scheduled)", Icons.Default.CalendarToday, AmberBrand)
                    }
                }
            }

            // Done Button
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Done", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SuccessMetaRow(label: String, value: String, icon: ImageVector, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, color = TextMuted, fontSize = 13.sp)
        }
        Text(value, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ==========================================
// 8. SCREEN 5: RESTORE DATA
// ==========================================

@Composable
fun RestoreDataScreen(
    snapshot: BackupSnapshot?,
    onRestoreComplete: () -> Unit,
    onImportBackupFile: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isRestoring by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Restore Data", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Center Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.radialGradient(listOf(CyanBrand.copy(alpha = 0.35f), Color.Transparent)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(CardBackground, CircleShape)
                            .border(1.5.dp, CyanBrand, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = CyanBrand, modifier = Modifier.size(42.dp))
                    }
                }

                if (snapshot != null) {
                    Text("Backup found", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Last backup: ${snapshot.formattedDate} · Size: ${snapshot.formattedSize}", color = CyanBrand, fontSize = 13.sp)
                } else {
                    Text("No local snapshots found", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("You can create a backup snapshot first or import a JSON backup file.", color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }

                if (restoreError != null) {
                    Text(restoreError!!, color = CoralBrand, fontSize = 12.sp)
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onImportBackupFile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyanBrand.copy(alpha = 0.65f))
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = CyanBrand)
                    Spacer(Modifier.width(8.dp))
                    Text("Import backup from Gmail / Drive / Downloads", color = CyanBrand, fontSize = 12.sp)
                }

                // Modules list
                Surface(
                    color = CardBackground,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("This will restore:", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        BackupModuleIncludeItem("Profile and preferences", Icons.Default.Person, PurpleBrand)
                        BackupModuleIncludeItem("Goals, tasks and checklists", Icons.Default.CheckCircle, EmeraldBrand)
                        BackupModuleIncludeItem("Notes, rich text and folders", Icons.Default.Note, CoralBrand)
                        BackupModuleIncludeItem("Habits and daily routines", Icons.Default.Spa, CyanBrand)
                        BackupModuleIncludeItem("Water and study records", Icons.Default.WaterDrop, Color(0xFF00B4D8))
                    }
                }
            }

            // Bottom Restore Button
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (snapshot != null) {
                            showConfirmDialog = true
                        }
                    },
                    enabled = snapshot != null && !isRestoring,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanBrand),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Restore Data", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = CardBackground,
            title = { Text("Confirm Restore?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Restoring will replace current local entries and settings with the selected snapshot data. Existing local records will be merged or replaced.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        isRestoring = true
                        val res = CloudSyncRepository.restoreFromSnapshot(context, snapshot!!)
                        isRestoring = false
                        if (res.isSuccess) {
                            onRestoreComplete()
                        } else {
                            restoreError = res.exceptionOrNull()?.message ?: "Restore failed"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanBrand)
                ) {
                    Text("Restore", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

// ==========================================
// 9. SCREEN 8: DETAILED SETTINGS
// ==========================================

@Composable
fun DetailedSettingsScreen(
    preferences: SyncPreferences,
    onUpdate: (SyncPreferences) -> Unit,
    onBack: () -> Unit
) {
    Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Backup & Sync Settings", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(18.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    SettingsSwitchRowItem(
                        title = "Automatic Backup",
                        subtitle = "Back up your data automatically in background",
                        checked = preferences.autoBackupEnabled,
                        onCheckedChange = { onUpdate(preferences.copy(autoBackupEnabled = it)) }
                    )
                }

                item {
                    Surface(
                        color = CardBackground,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = if (preferences.backupFrequency == "Daily") "Weekly" else "Daily"
                                onUpdate(preferences.copy(backupFrequency = next))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Backup Frequency", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(preferences.backupFrequency, color = PurpleBrand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item {
                    Surface(
                        color = CardBackground,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = if (preferences.networkPolicy == "Wi-Fi only") "Wi-Fi and mobile data" else "Wi-Fi only"
                                onUpdate(preferences.copy(networkPolicy = next))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Use Network", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(preferences.networkPolicy, color = CyanBrand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item {
                    SettingsSwitchRowItem(
                        title = "Sync Across Devices",
                        subtitle = "Keep your data updated on all signed-in devices",
                        checked = preferences.syncAcrossDevices,
                        onCheckedChange = { onUpdate(preferences.copy(syncAcrossDevices = it)) }
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Include in Backup", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground, RoundedCornerShape(16.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModuleToggleRow("Profile and settings", preferences.includeProfile) { onUpdate(preferences.copy(includeProfile = it)) }
                        ModuleToggleRow("Goals", preferences.includeGoals) { onUpdate(preferences.copy(includeGoals = it)) }
                        ModuleToggleRow("Tasks", preferences.includeTasks) { onUpdate(preferences.copy(includeTasks = it)) }
                        ModuleToggleRow("Notes", preferences.includeNotes) { onUpdate(preferences.copy(includeNotes = it)) }
                        ModuleToggleRow("Habits", preferences.includeHabits) { onUpdate(preferences.copy(includeHabits = it)) }
                        ModuleToggleRow("Routines", preferences.includeRoutines) { onUpdate(preferences.copy(includeRoutines = it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextWhite, fontSize = 13.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurpleBrand)
        )
    }
}

// ==========================================
// 10. SCREEN 9: STORAGE & DATA INFO
// ==========================================

@Composable
fun StorageDataInfoScreen(
    breakdown: StorageBreakdown,
    onBack: () -> Unit
) {
    Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Storage & Data", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(18.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Circular Ring / Donut Progress Card
                item {
                    Surface(
                        color = CardBackground,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { 0.05f },
                                    strokeWidth = 10.dp,
                                    color = CyanBrand,
                                    trackColor = CardInner,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(formatBytes(breakdown.totalBytes), color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("used", color = TextMuted, fontSize = 10.sp)
                                }
                            }

                            Text("Total Storage Used", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("1 GB cloud allocation available", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }

                // Breakdown List
                item {
                    Text("Storage Breakdown", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground, RoundedCornerShape(16.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StorageModuleRow("Notes", formatBytes(breakdown.notesBytes), CoralBrand)
                        StorageModuleRow("Tasks", formatBytes(breakdown.tasksBytes), PurpleBrand)
                        StorageModuleRow("Habits", formatBytes(breakdown.habitsBytes), EmeraldBrand)
                        StorageModuleRow("Routines", formatBytes(breakdown.routinesBytes), CyanBrand)
                        StorageModuleRow("Settings", formatBytes(breakdown.settingsBytes), AmberBrand)
                        StorageModuleRow("Other Tracker Logs", formatBytes(breakdown.otherBytes), Color(0xFF6366F1))
                    }
                }

                // Space Banner
                item {
                    Surface(
                        color = EmeraldBrand.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, EmeraldBrand.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldBrand, modifier = Modifier.size(20.dp))
                            Column {
                                Text("You have plenty of space", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Your data fits comfortably within cloud storage quotas.", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageModuleRow(name: String, sizeStr: String, dotColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
            Text(name, color = TextWhite, fontSize = 13.sp)
        }
        Text(sizeStr, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ==========================================
// 11. SCREEN 10: MANAGE DATA
// ==========================================

@Composable
fun ManageDataScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToRestore: () -> Unit,
    onDataDeleted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteCloudConfirm by remember { mutableStateOf(false) }

    Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Manage Data", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(18.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    ManageActionRow(
                        title = "Restore Data",
                        subtitle = "Restore from an existing backup snapshot",
                        icon = Icons.Default.Restore,
                        iconTint = CyanBrand,
                        onClick = onNavigateToRestore
                    )
                }

                item {
                    ManageActionRow(
                        title = "View Backup History",
                        subtitle = "See all your previous point-in-time snapshots",
                        icon = Icons.Default.History,
                        iconTint = PurpleBrand,
                        onClick = onNavigateToHistory
                    )
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    Text("Danger Zone", color = CoralBrand, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                item {
                    ManageActionRow(
                        title = "Delete Cloud Data",
                        subtitle = "Remove all your snapshots and data from cloud",
                        icon = Icons.Default.DeleteForever,
                        iconTint = CoralBrand,
                        titleColor = CoralBrand,
                        onClick = { showDeleteCloudConfirm = true }
                    )
                }
            }
        }
    }

    if (showDeleteCloudConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteCloudConfirm = false },
            containerColor = CardBackground,
            title = { Text("Delete Cloud Data?", color = CoralBrand, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove all cloud backup snapshots. Your local data on this phone will remain untouched.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        CloudSyncRepository.deleteCloudData(context)
                        showDeleteCloudConfirm = false
                        onDataDeleted()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralBrand)
                ) {
                    Text("Delete Cloud Data", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCloudConfirm = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

@Composable
private fun ManageActionRow(
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
// 12. SCREEN 11: SYNC NOTIFICATIONS
// ==========================================

@Composable
fun SyncNotificationsScreen(
    preferences: SyncPreferences,
    onUpdate: (SyncPreferences) -> Unit,
    onBack: () -> Unit
) {
    Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Notifications", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(18.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    SettingsSwitchRowItem(
                        title = "Show Sync Notifications",
                        subtitle = "Get notified about backup and sync status",
                        checked = preferences.showSyncNotifications,
                        onCheckedChange = { onUpdate(preferences.copy(showSyncNotifications = it)) }
                    )
                }

                item {
                    Text("Notify When", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground, RoundedCornerShape(16.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NotificationCheckboxRow("Backup completed", preferences.notifyBackupCompleted) { onUpdate(preferences.copy(notifyBackupCompleted = it)) }
                        NotificationCheckboxRow("Backup failed", preferences.notifyBackupFailed) { onUpdate(preferences.copy(notifyBackupFailed = it)) }
                        NotificationCheckboxRow("Sync completed", preferences.notifySyncCompleted) { onUpdate(preferences.copy(notifySyncCompleted = it)) }
                        NotificationCheckboxRow("Sync failed", preferences.notifySyncFailed) { onUpdate(preferences.copy(notifySyncFailed = it)) }
                        NotificationCheckboxRow("Large backup size", preferences.notifyLargeBackup) { onUpdate(preferences.copy(notifyLargeBackup = it)) }
                        NotificationCheckboxRow("Storage limit warning", preferences.notifyStorageLimit) { onUpdate(preferences.copy(notifyStorageLimit = it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = PurpleBrand)
        )
        Text(label, color = TextWhite, fontSize = 13.sp)
    }
}

// ==========================================
// 13. BACKUP HISTORY SCREEN
// ==========================================

@Composable
fun BackupHistoryScreen(
    onRestoreSnapshot: (BackupSnapshot) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snapshots = remember { CloudSyncRepository.getSnapshots(context) }
    var selectedSnapshot by remember { mutableStateOf<BackupSnapshot?>(null) }

    Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Backup History", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(18.dp))

            if (snapshots.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                        Text("No backup history yet", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Snapshots will appear here after manual or daily backups.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(snapshots, key = { it.id }) { snapshot ->
                        Surface(
                            color = CardBackground,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(snapshot.formattedDate, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("${snapshot.formattedSize} · ${snapshot.deviceName}", color = TextMuted, fontSize = 11.sp)
                                }

                                TextButton(
                                    onClick = { selectedSnapshot = snapshot },
                                    colors = ButtonDefaults.textButtonColors(contentColor = CyanBrand)
                                ) {
                                    Text("Restore", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedSnapshot != null) {
        AlertDialog(
            onDismissRequest = { selectedSnapshot = null },
            containerColor = CardBackground,
            title = { Text("Restore this snapshot?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("This will restore your data to the state recorded on ${selectedSnapshot?.formattedDate}.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        val s = selectedSnapshot!!
                        selectedSnapshot = null
                        onRestoreSnapshot(s)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanBrand)
                ) {
                    Text("Restore", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSnapshot = null }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

// ==========================================
// 14. SCREEN 12: IN-APP STATUS INDICATOR PILL
// ==========================================

@Composable
fun InAppStatusIndicator(
    account: CloudAccount,
    syncState: SyncState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor, statusIcon) = when (syncState) {
        SyncState.UP_TO_DATE -> Triple("Synced · Today", EmeraldBrand, Icons.Default.CloudDone)
        SyncState.SYNC_PENDING -> Triple("Sync pending", AmberBrand, Icons.Default.CloudUpload)
        SyncState.OFFLINE -> Triple("Offline", TextMuted, Icons.Default.CloudOff)
        SyncState.NOT_CONFIGURED -> Triple("Cloud Backup", PurpleBrand, Icons.Default.Cloud)
        else -> Triple(syncState.label, CyanBrand, Icons.Default.Cloud)
    }

    Surface(
        color = statusColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
            Text(statusText, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsSwitchRowItem(
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
