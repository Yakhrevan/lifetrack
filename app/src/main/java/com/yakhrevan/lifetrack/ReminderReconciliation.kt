package com.yakhrevan.lifetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Rebuilds every reminder category from the repositories after system clock,
 * timezone, reboot, or package lifecycle changes. Repositories remain authoritative.
 */
class ReminderReconciliationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val context = applicationContext

        HabitRoutineRepository.getHabits(context, includeArchived = true)
            .forEach { HabitRoutineNotificationScheduler.reconcileHabitReminders(context, it) }

        HabitRoutineRepository.getRoutines(context, includeArchived = true)
            .forEach { HabitRoutineNotificationScheduler.reconcileRoutineReminders(context, it) }

        TaskRepository.getTasks(context, includeArchived = true)
            .forEach { TaskNotificationScheduler.reconcileTaskReminder(context, it) }

        CalendarEventNotificationScheduler.reconcile(context, CalendarStorage.load(context))
        Result.success()
    }.getOrElse {
        Result.retry()
    }
}

/**
 * System broadcasts are deliberately limited to events that invalidate local-time
 * scheduling assumptions. No exact-alarm permission is required.
 */
class ReminderReconciliationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in RECONCILE_ACTIONS) return

        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            UNIQUE_RECONCILIATION_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderReconciliationWorker>().build()
        )
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_RECONCILIATION_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReminderReconciliationWorker>(12, TimeUnit.HOURS).build()
        )
    }

    companion object {
        private const val UNIQUE_RECONCILIATION_WORK = "lifetrack_reminder_reconciliation"
        private const val PERIODIC_RECONCILIATION_WORK = "lifetrack_reminder_reconciliation_periodic"

        private val RECONCILE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
