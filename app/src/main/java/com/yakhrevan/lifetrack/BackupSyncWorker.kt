package com.yakhrevan.lifetrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

// ==========================================
// 1. WORKMANAGER WORKERS
// ==========================================

class DailyBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = CloudSyncRepository.getPreferences(context)
        if (!prefs.autoBackupEnabled) return Result.success()

        // Respect Wi-Fi only policy
        if (OffDeviceBackupStore.isConfigured(context) && prefs.networkPolicy == "Wi-Fi only" && !CloudSyncRepository.isWifiConnected(context)) {
            return Result.retry()
        }

        val result = CloudSyncRepository.performBackup(context)
        return if (result.isSuccess) {
            val snapshot = result.getOrNull()
            if (prefs.showSyncNotifications && prefs.notifyBackupCompleted && snapshot != null) {
                showBackupNotification(
                    context,
                    "Backup Completed",
                    "Your LifeTrack data was automatically backed up (${snapshot.formattedSize})."
                )
            }
            Result.success()
        } else {
            if (prefs.showSyncNotifications && prefs.notifyBackupFailed) {
                showBackupNotification(
                    context,
                    "Backup Failed",
                    "Automatic backup could not be completed. Will retry automatically."
                )
            }
            Result.retry()
        }
    }
}

class ImmediateSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val result = CloudSyncRepository.performBackup(context)
        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.failure()
        }
    }
}

// ==========================================
// 2. SCHEDULER & NOTIFICATION HELPER
// ==========================================

object BackupSyncScheduler {
    private const val DAILY_WORK_TAG = "lifetrack_daily_backup_work"
    private const val CHANNEL_ID = "lifetrack_backup_channel"

    fun scheduleDailyBackup(context: Context) {
        val prefs = CloudSyncRepository.getPreferences(context)
        val workManager = WorkManager.getInstance(context)

        if (!prefs.autoBackupEnabled) {
            workManager.cancelAllWorkByTag(DAILY_WORK_TAG)
            return
        }

        val networkType = if (!OffDeviceBackupStore.isConfigured(context)) {
            NetworkType.NOT_REQUIRED
        } else if (prefs.networkPolicy == "Wi-Fi only") {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodHours = if (prefs.backupFrequency == "Weekly") 7L * 24L else 24L
        val dailyWork = PeriodicWorkRequestBuilder<DailyBackupWorker>(periodHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag(DAILY_WORK_TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            DAILY_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyWork
        )
    }

    fun triggerImmediateBackup(context: Context) {
        val work = OneTimeWorkRequestBuilder<ImmediateSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueue(work)
    }
}

private fun showBackupNotification(context: Context, title: String, message: String) {
    if (!NotificationPolicy.canNotify(context, NotificationPolicy.Module.SYSTEM)) return
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    val notification = NotificationPolicy.builder(context, NotificationPolicy.Module.SYSTEM, "Backup and sync")
        .setContentTitle(title)
        .setContentText(message)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    nm.notify(1099, notification)
}
