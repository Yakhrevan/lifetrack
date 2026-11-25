package com.yakhrevan.lifetrack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.Data
import java.time.LocalDateTime
import java.time.ZoneId

/** Optional exact-clock delivery for task reminders; WorkManager remains the safe fallback. */
object PreciseTaskAlarmScheduler {
    fun isAvailable(context: Context): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    }

    fun schedule(context: Context, taskId: String, at: LocalDateTime, data: Data): Boolean {
        if (!UserProfileRepository.getSettings(context).preciseTaskAlarms || !isAvailable(context)) return false
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        val atMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (atMillis <= System.currentTimeMillis()) return false
        return runCatching {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis,
                requireNotNull(intent(context, taskId, data, PendingIntent.FLAG_UPDATE_CURRENT)))
            true
        }.getOrDefault(false)
    }

    fun cancel(context: Context, taskId: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = intent(context, taskId, Data.EMPTY, PendingIntent.FLAG_NO_CREATE)
        if (pending != null) { manager.cancel(pending); pending.cancel() }
    }

    private fun intent(context: Context, taskId: String, data: Data, flags: Int): PendingIntent? {
        val action = Intent(context, PreciseTaskAlarmReceiver::class.java).apply {
            action = "com.yakhrevan.lifetrack.TASK_ALARM"
            this.data = Uri.parse("lifetrack://task-alarm/${Uri.encode(taskId)}")
            putExtra("task_id", taskId)
            putExtra("expected_date", data.getString(TaskReminderWorker.KEY_EXPECTED_DATE))
            putExtra("expected_time", data.getString(TaskReminderWorker.KEY_EXPECTED_TIME))
            putExtra("occurrence_date", data.getString(TaskReminderWorker.KEY_OCCURRENCE_DATE))
        }
        return PendingIntent.getBroadcast(context, taskId.hashCode(), action,
            flags or PendingIntent.FLAG_IMMUTABLE)
    }
}

class PreciseTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.yakhrevan.lifetrack.TASK_ALARM") return
        val id = intent.getStringExtra("task_id") ?: return
        // Live task state, module switches and notification permission are checked
        // again by the same delivery path used by WorkManager.
        val data = Data.Builder().putString(TaskReminderWorker.KEY_TASK_ID, id)
            .putString(TaskReminderWorker.KEY_EXPECTED_DATE, intent.getStringExtra("expected_date"))
            .putString(TaskReminderWorker.KEY_EXPECTED_TIME, intent.getStringExtra("expected_time"))
            .putString(TaskReminderWorker.KEY_OCCURRENCE_DATE, intent.getStringExtra("occurrence_date"))
            .build()
        TaskReminderWorker.deliver(context.applicationContext, data)
    }
}
