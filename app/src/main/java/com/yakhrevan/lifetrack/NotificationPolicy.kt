package com.yakhrevan.lifetrack

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalTime

/** One source of truth for LifeTrack notification permission, preferences, quiet hours and branding. */
object NotificationPolicy {
    enum class Module { TASKS, HABITS, ROUTINES, CALENDAR, WATER, STUDY, WELLBEING, NOTES, SYSTEM }

    fun enabled(settings: AppSettings, module: Module): Boolean = settings.notificationsEnabled && when (module) {
        Module.TASKS -> settings.notifyTasks
        Module.CALENDAR -> settings.notifyCalendar
        Module.HABITS -> settings.notifyHabits
        Module.ROUTINES -> settings.notifyRoutines
        Module.WATER -> settings.notifyWater
        Module.STUDY -> settings.notifyStudy
        Module.WELLBEING -> settings.notifyDigitalWellbeing
        Module.NOTES -> settings.notifyNotes
        Module.SYSTEM -> true
    }

    fun canNotify(context: Context, module: Module): Boolean {
        if (!enabled(UserProfileRepository.getSettings(context), module)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** A quiet-hours notification remains visible, but its own channel has neither sound nor vibration. */
    fun inQuietHours(settings: AppSettings, current: LocalTime = LocalTime.now()): Boolean {
        if (!settings.quietHoursEnabled) return false
        val start = runCatching { LocalTime.parse(settings.quietHoursStart) }.getOrNull() ?: return false
        val end = runCatching { LocalTime.parse(settings.quietHoursEnd) }.getOrNull() ?: return false
        if (start == end) return false
        return if (start < end) !current.isBefore(start) && current.isBefore(end)
        else !current.isBefore(start) || current.isBefore(end)
    }

    fun snoozeMinutes(context: Context, task: TaskItem? = null): Int =
        (task?.snoozeMinutes ?: UserProfileRepository.getSettings(context).snoozeDurationMinutes).coerceIn(5, 120)

    fun builder(
        context: Context,
        module: Module,
        channelLabel: String,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT
    ): NotificationCompat.Builder {
        val settings = UserProfileRepository.getSettings(context)
        val quiet = inQuietHours(settings)
        val sound = if (quiet) "quiet" else settings.notificationSound.lowercase().let {
            if (it == "system" || it == "silent") it else "chime"
        }
        // v2 channel IDs intentionally migrate from the immutable old channel sound settings.
        val channelId = "lifetrack_${module.name.lowercase()}_v2_$sound"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val soundUri: Uri? = when (sound) {
                "quiet", "silent" -> null
                "system" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                else -> Uri.parse("android.resource://${context.packageName}/raw/lifetrack_chime")
            }
            manager.createNotificationChannel(
                NotificationChannel(channelId, "$channelLabel · ${sound.replaceFirstChar { it.uppercase() }}", importance).apply {
                    description = "LifeTrack $channelLabel notifications"
                    setSound(soundUri, attributes)
                    enableVibration(!quiet && sound != "silent")
                }
            )
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_lifetrack_notification)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_lifetrack_logo))
            .setColor(android.graphics.Color.rgb(139, 92, 246))
            .setPriority(if (importance >= NotificationManager.IMPORTANCE_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .also { builder ->
                if (quiet || sound == "silent") builder.setSilent(true)
            }
    }
}
