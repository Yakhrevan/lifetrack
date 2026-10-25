package com.yakhrevan.lifetrack

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// 1. DATA MODELS & ENUMS
// ==========================================

enum class SyncState(val label: String) {
    NOT_CONFIGURED("Not configured (Local-first mode)"),
    UP_TO_DATE("Local snapshot up to date"),
    SYNC_PENDING("Local changes pending backup"),
    BACKING_UP("Creating local backup..."),
    RESTORING("Restoring data..."),
    OFFLINE("Offline"),
    FAILED("Backup failed")
}

data class CloudAccount(
    val isConnected: Boolean = false,
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val lastSyncTimestamp: Long = 0L,
    val authProvider: String = "Google"
)

data class BackupSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0L,
    val recordCount: Int = 0,
    val status: String = "Completed", // "Completed", "Failed"
    val deviceName: String = "Android Device",
    val filePath: String = ""
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(timestamp))

    val formattedSize: String
        get() = formatBytes(sizeBytes)
}

data class SyncPreferences(
    val autoBackupEnabled: Boolean = true,
    val backupFrequency: String = "Daily", // "Daily", "Weekly", "Manual"
    val networkPolicy: String = "Wi-Fi only", // "Wi-Fi only", "Wi-Fi and mobile data"
    val syncAcrossDevices: Boolean = true,
    val includeProfile: Boolean = true,
    val includeGoals: Boolean = true,
    val includeTasks: Boolean = true,
    val includeNotes: Boolean = true,
    val includeHabits: Boolean = true,
    val includeRoutines: Boolean = true,
    val includeWaterStudy: Boolean = true,
    
    // Notifications
    val showSyncNotifications: Boolean = true,
    val notifyBackupCompleted: Boolean = true,
    val notifyBackupFailed: Boolean = true,
    val notifySyncCompleted: Boolean = true,
    val notifySyncFailed: Boolean = true,
    val notifyLargeBackup: Boolean = false,
    val notifyStorageLimit: Boolean = true
)

data class StorageBreakdown(
    val totalBytes: Long,
    val notesBytes: Long,
    val tasksBytes: Long,
    val habitsBytes: Long,
    val routinesBytes: Long,
    val settingsBytes: Long,
    val otherBytes: Long
)

// ==========================================
// 2. REPOSITORY
// ==========================================

object CloudSyncRepository {
    private const val PREFS_SYNC = "lifetrack_cloud_sync_v2"
    private const val PREFS_SNAPSHOTS = "lifetrack_cloud_backup_history"

    private fun getSyncPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)

    private fun getSnapshotPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_SNAPSHOTS, Context.MODE_PRIVATE)

    // --- Account Management ---
    fun getAccount(context: Context): CloudAccount {
        val prefs = getSyncPrefs(context)
        val connected = prefs.getBoolean("account_connected", false)
        return CloudAccount(
            isConnected = connected,
            email = prefs.getString("account_email", "") ?: "",
            displayName = prefs.getString("account_name", "") ?: "",
            photoUrl = prefs.getString("account_photo", null),
            lastSyncTimestamp = prefs.getLong("last_sync_timestamp", 0L),
            authProvider = prefs.getString("auth_provider", "Google") ?: "Google"
        )
    }

    fun saveAccount(context: Context, account: CloudAccount) {
        getSyncPrefs(context).edit()
            .putBoolean("account_connected", account.isConnected)
            .putString("account_email", account.email)
            .putString("account_name", account.displayName)
            .putString("account_photo", account.photoUrl)
            .putLong("last_sync_timestamp", account.lastSyncTimestamp)
            .putString("auth_provider", account.authProvider)
            .apply()
    }

    fun signOut(context: Context) {
        getSyncPrefs(context).edit()
            .putBoolean("account_connected", false)
            .remove("account_email")
            .remove("account_name")
            .remove("account_photo")
            .apply()
    }

    // --- Sync Preferences ---
    fun getPreferences(context: Context): SyncPreferences {
        val prefs = getSyncPrefs(context)
        return SyncPreferences(
            autoBackupEnabled = prefs.getBoolean("auto_backup_enabled", true),
            backupFrequency = prefs.getString("backup_frequency", "Daily") ?: "Daily",
            networkPolicy = prefs.getString("network_policy", "Wi-Fi only") ?: "Wi-Fi only",
            syncAcrossDevices = prefs.getBoolean("sync_across_devices", true),
            includeProfile = prefs.getBoolean("include_profile", true),
            includeGoals = prefs.getBoolean("include_goals", true),
            includeTasks = prefs.getBoolean("include_tasks", true),
            includeNotes = prefs.getBoolean("include_notes", true),
            includeHabits = prefs.getBoolean("include_habits", true),
            includeRoutines = prefs.getBoolean("include_routines", true),
            includeWaterStudy = prefs.getBoolean("include_water_study", true),

            showSyncNotifications = prefs.getBoolean("show_sync_notifications", true),
            notifyBackupCompleted = prefs.getBoolean("notify_backup_completed", true),
            notifyBackupFailed = prefs.getBoolean("notify_backup_failed", true),
            notifySyncCompleted = prefs.getBoolean("notify_sync_completed", true),
            notifySyncFailed = prefs.getBoolean("notify_sync_failed", true),
            notifyLargeBackup = prefs.getBoolean("notify_large_backup", false),
            notifyStorageLimit = prefs.getBoolean("notify_storage_limit", true)
        )
    }

    fun savePreferences(context: Context, prefs: SyncPreferences) {
        getSyncPrefs(context).edit()
            .putBoolean("auto_backup_enabled", prefs.autoBackupEnabled)
            .putString("backup_frequency", prefs.backupFrequency)
            .putString("network_policy", prefs.networkPolicy)
            .putBoolean("sync_across_devices", prefs.syncAcrossDevices)
            .putBoolean("include_profile", prefs.includeProfile)
            .putBoolean("include_goals", prefs.includeGoals)
            .putBoolean("include_tasks", prefs.includeTasks)
            .putBoolean("include_notes", prefs.includeNotes)
            .putBoolean("include_habits", prefs.includeHabits)
            .putBoolean("include_routines", prefs.includeRoutines)
            .putBoolean("include_water_study", prefs.includeWaterStudy)

            .putBoolean("show_sync_notifications", prefs.showSyncNotifications)
            .putBoolean("notify_backup_completed", prefs.notifyBackupCompleted)
            .putBoolean("notify_backup_failed", prefs.notifyBackupFailed)
            .putBoolean("notify_sync_completed", prefs.notifySyncCompleted)
            .putBoolean("notify_sync_failed", prefs.notifySyncFailed)
            .putBoolean("notify_large_backup", prefs.notifyLargeBackup)
            .putBoolean("notify_storage_limit", prefs.notifyStorageLimit)
            .apply()
    }

    // --- State & Status Calculation ---
    fun determineSyncState(context: Context): SyncState {
        val lastBackup = getLastSuccessfulBackupTimestamp(context)
        if (lastBackup == 0L) return SyncState.NOT_CONFIGURED

        val pendingChanges = getPendingChangesCount(context)
        if (pendingChanges > 0) return SyncState.SYNC_PENDING

        return SyncState.UP_TO_DATE
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun getPendingChangesCount(context: Context): Int {
        val prefs = getSyncPrefs(context)
        return prefs.getInt("pending_changes_count", 0)
    }

    fun recordLocalChange(context: Context) {
        val prefs = getSyncPrefs(context)
        val current = prefs.getInt("pending_changes_count", 0)
        prefs.edit().putInt("pending_changes_count", current + 1).apply()
    }

    fun clearPendingChanges(context: Context) {
        getSyncPrefs(context).edit().putInt("pending_changes_count", 0).apply()
    }

    fun getLastSuccessfulBackupTimestamp(context: Context): Long {
        return getSyncPrefs(context).getLong("last_backup_timestamp", 0L)
    }

    fun setLastSuccessfulBackupTimestamp(context: Context, timestamp: Long) {
        getSyncPrefs(context).edit().putLong("last_backup_timestamp", timestamp).apply()
    }

    // --- Real Storage Size Calculation ---
    fun calculateStorageBreakdown(context: Context): StorageBreakdown {
        val repository = LifeTrackRepository(context)
        val logs = repository.loadEntries()

        // Calculate actual serialized lengths of each module
        val notesJson = context.getSharedPreferences("lifetrack_notes_v2", Context.MODE_PRIVATE).all.toString()
        val tasksJson = context.getSharedPreferences("lifetrack_tasks_v2", Context.MODE_PRIVATE).all.toString()
        val habitsJson = context.getSharedPreferences("lifetrack_habits_routines_v2", Context.MODE_PRIVATE).all.toString()
        val routinesJson = context.getSharedPreferences("lifetrack_routines", Context.MODE_PRIVATE).all.toString()
        val settingsJson = context.getSharedPreferences("lifetrack_settings_v2", Context.MODE_PRIVATE).all.toString()
        val logsJson = EntryCodec.encode(logs)

        val notesBytes = notesJson.toByteArray(Charsets.UTF_8).size.toLong()
        val tasksBytes = tasksJson.toByteArray(Charsets.UTF_8).size.toLong()
        val habitsBytes = habitsJson.toByteArray(Charsets.UTF_8).size.toLong()
        val routinesBytes = routinesJson.toByteArray(Charsets.UTF_8).size.toLong()
        val settingsBytes = settingsJson.toByteArray(Charsets.UTF_8).size.toLong()
        val otherBytes = logsJson.toByteArray(Charsets.UTF_8).size.toLong()

        val totalBytes = notesBytes + tasksBytes + habitsBytes + routinesBytes + settingsBytes + otherBytes

        return StorageBreakdown(
            totalBytes = totalBytes.coerceAtLeast(1024L), // Minimum realistic size
            notesBytes = notesBytes,
            tasksBytes = tasksBytes,
            habitsBytes = habitsBytes,
            routinesBytes = routinesBytes,
            settingsBytes = settingsBytes,
            otherBytes = otherBytes
        )
    }

    // --- Real Point-in-time Snapshot Backup ---
    fun performBackup(context: Context): Result<BackupSnapshot> {
        return runCatching {
            val repository = LifeTrackRepository(context)
            val logs = repository.loadEntries()
            val backupJson = LifeTrackBackup.encode(context, logs)

            // Save to app local snapshots directory
            val backupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
            val snapshotId = UUID.randomUUID().toString()
            val snapshotFile = File(backupDir, "lifetrack_backup_$snapshotId.json")
            snapshotFile.writeText(backupJson)

            val now = System.currentTimeMillis()
            val snapshot = BackupSnapshot(
                id = snapshotId,
                timestamp = now,
                sizeBytes = snapshotFile.length(),
                recordCount = logs.size,
                status = "Completed",
                deviceName = android.os.Build.MODEL ?: "Android Device",
                filePath = snapshotFile.absolutePath
            )

            // Save snapshot record to history
            saveSnapshotRecord(context, snapshot)

            // Update sync and backup times
            setLastSuccessfulBackupTimestamp(context, now)
            val currentAccount = getAccount(context)
            saveAccount(context, currentAccount.copy(lastSyncTimestamp = now))
            clearPendingChanges(context)

            snapshot
        }
    }

    fun getSnapshots(context: Context): List<BackupSnapshot> {
        val prefs = getSnapshotPrefs(context)
        val raw = prefs.getString("snapshots_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<BackupSnapshot>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    BackupSnapshot(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        recordCount = obj.optInt("recordCount", 0),
                        status = obj.optString("status", "Completed"),
                        deviceName = obj.optString("deviceName", "Android Device"),
                        filePath = obj.optString("filePath", "")
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        }.getOrDefault(emptyList())
    }

    private fun saveSnapshotRecord(context: Context, snapshot: BackupSnapshot) {
        val current = getSnapshots(context).toMutableList()
        current.add(0, snapshot)
        // Keep up to 10 snapshots
        val trimmed = current.take(10)
        val arr = JSONArray()
        trimmed.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("timestamp", s.timestamp)
                put("sizeBytes", s.sizeBytes)
                put("recordCount", s.recordCount)
                put("status", s.status)
                put("deviceName", s.deviceName)
                put("filePath", s.filePath)
            }
            arr.put(obj)
        }
        getSnapshotPrefs(context).edit().putString("snapshots_json", arr.toString()).apply()
    }

    fun restoreFromSnapshot(context: Context, snapshot: BackupSnapshot): Result<Unit> {
        return runCatching {
            val file = File(snapshot.filePath)
            require(file.exists()) { "Backup snapshot file not found on device." }
            val raw = file.readText()
            val backupData = LifeTrackBackup.decode(raw)

            // Restore entries and preferences
            val repository = LifeTrackRepository(context)
            repository.saveEntries(backupData.entries)
            LifeTrackBackup.restorePreferences(context, backupData.preferences)
        }
    }

    fun deleteCloudData(context: Context): Result<Unit> {
        return runCatching {
            // Delete local snapshots
            val backupDir = File(context.filesDir, "backups")
            if (backupDir.exists()) {
                backupDir.deleteRecursively()
            }
            getSnapshotPrefs(context).edit().clear().apply()
            getSyncPrefs(context).edit().putLong("last_backup_timestamp", 0L).apply()
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}
