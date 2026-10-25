package com.yakhrevan.lifetrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class RoutineReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val name = inputData.getString(KEY_NAME)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Notifications are optional. Avoid a SecurityException when permission is denied.
            return Result.success()
        }
        val channelId = "lifetrack_routine_reminders"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Routine reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Scheduled habit and personal-care reminders" })
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("LifeTrack routine")
            .setContentText("Time for: $name")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your scheduled routine is ready: $name"))
            .setAutoCancel(true)
            .build()
        manager.notify(name.hashCode(), notification)
        return Result.success()
    }
    companion object { const val KEY_NAME = "routine_name" }
}

object RoutineReminderScheduler {
    fun schedule(context: Context, name: String, hour: Int, minute: Int) {
        require(name.isNotBlank()) { "Routine name cannot be blank." }
        require(hour in 0..23) { "Hour must be between 0 and 23." }
        require(minute in 0..59) { "Minute must be between 0 and 59." }
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); if (!after(now)) add(Calendar.DAY_OF_YEAR, 1) }
        val delay = (next.timeInMillis - now.timeInMillis).coerceAtLeast(1L)
        val request = PeriodicWorkRequestBuilder<RoutineReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(RoutineReminderWorker.KEY_NAME, name).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("routine_reminder_${name.hashCode()}", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    fun cancel(context: Context, name: String) {
        WorkManager.getInstance(context).cancelUniqueWork("routine_reminder_${name.hashCode()}")
    }

    /** Cancel reminders based on the currently stored routine list before replacing preferences. */
    fun cancelAllCurrent(context: Context) {
        val names = linkedSetOf<String>()
        // Read dynamic user routines
        HabitRoutineRepository.getRoutines(context, includeArchived = true).forEach { names.add(it.name) }
        HabitRoutineRepository.getHabits(context, includeArchived = true).forEach { names.add(it.name) }

        val prefs = context.applicationContext.getSharedPreferences("lifetrack_routines", Context.MODE_PRIVATE)
        prefs.all.filterKeys { it.startsWith("routine_name_") }.values
            .mapNotNull { it as? String }
            .filter { it.isNotBlank() }
            .forEach(names::add)
        names.forEach { cancel(context, it) }
    }

    /** Rebuild scheduled routine work after a full backup restore or device-side preference replacement. */
    fun restoreEnabled(context: Context) {
        val names = linkedSetOf<String>()
        HabitRoutineRepository.getRoutines(context, includeArchived = false).forEach { names.add(it.name) }
        HabitRoutineRepository.getHabits(context, includeArchived = false).forEach { names.add(it.name) }

        val prefs = context.applicationContext.getSharedPreferences("lifetrack_routines", Context.MODE_PRIVATE)
        prefs.all.filterKeys { it.startsWith("routine_name_") }.values
            .mapNotNull { it as? String }
            .filter { it.isNotBlank() }
            .forEach(names::add)

        names.forEach { name ->
            val hash = name.hashCode().toString()
            if (prefs.getBoolean("reminder_enabled_" + hash, false)) {
                val time = prefs.getString("reminder_time_" + hash, "20:00") ?: "20:00"
                val parts = time.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull()
                val minute = parts.getOrNull(1)?.toIntOrNull()
                if (hour != null && hour in 0..23 && minute != null && minute in 0..59) {
                    schedule(context, name, hour, minute)
                }
            } else {
                cancel(context, name)
            }
        }
    }
}