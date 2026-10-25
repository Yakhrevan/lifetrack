package com.yakhrevan.lifetrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate

/**
 * Handles interactive notification actions (Water logging, Habit check-off, Task completion)
 * without needing the user to launch the full application.
 */
class LifeTrackNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            ACTION_LOG_WATER -> handleLogWater(context, intent)
            ACTION_SNOOZE_WATER -> handleSnoozeWater(context, intent)
            ACTION_COMPLETE_HABIT -> handleCompleteHabit(context, intent)
            ACTION_COMPLETE_TASK -> handleCompleteTask(context, intent)
        }
    }

    private fun handleLogWater(context: Context, intent: Intent) {
        val amountMl = intent.getIntExtra(EXTRA_AMOUNT_ML, 250)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 4102)

        // 1. Log water intake in repository
        WaterRepository.addEntry(
            context = context,
            amountMl = amountMl,
            drinkType = DrinkType.WATER,
            note = "Logged from notification"
        )

        // 2. Fetch updated total and goal
        val todayStr = LocalDate.now().toString()
        val entries = WaterRepository.loadEntries(context)
        val todayTotal = entries.filter { it.date == todayStr }.sumOf { it.amountMl }
        val settings = WaterRepository.loadSettings(context)
        val goalMl = settings.dailyGoalMl
        val remainingMl = (goalMl - todayTotal).coerceAtLeast(0)

        // 3. Inform user with quick feedback toast
        val toastMsg = "💧 +${amountMl}ml logged! Today: ${todayTotal}ml / ${goalMl}ml"
        Toast.makeText(context.applicationContext, toastMsg, Toast.LENGTH_SHORT).show()

        // 4. Update or dismiss notification
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "lifetrack_water"

        if (todayTotal >= goalMl) {
            // Daily goal reached! Celebrate and dismiss or show completion badge
            val goalReachedNotif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🎉 Daily Hydration Goal Reached!")
                .setContentText("You reached ${todayTotal}ml today. Fantastic job staying hydrated!")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            manager.notify(notifId, goalReachedNotif)
        } else {
            // Update remaining hydration with action buttons retained
            val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_page", "Water")
            }
            val contentPendingIntent = if (openIntent != null) {
                PendingIntent.getActivity(
                    context,
                    notifId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null

            val log250Pending = createWaterActionPendingIntent(context, 250, notifId, 5001)
            val log500Pending = createWaterActionPendingIntent(context, 500, notifId, 5002)

            val updatedNotification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("💧 Hydration Updated (+${amountMl}ml)")
                .setContentText("Current: ${todayTotal}ml • ${remainingMl}ml remaining to goal")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Keep it up! Total logged today: ${todayTotal}ml. Remaining: ${remainingMl}ml."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .apply {
                    if (contentPendingIntent != null) setContentIntent(contentPendingIntent)
                }
                .addAction(android.R.drawable.ic_input_add, "+250 ml", log250Pending)
                .addAction(android.R.drawable.ic_input_add, "+500 ml", log500Pending)
                .build()

            manager.notify(notifId, updatedNotification)
        }
    }

    private fun handleSnoozeWater(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 4102)
        val snoozeMs = System.currentTimeMillis() + (30 * 60 * 1000L) // 30 minutes
        val currentSettings = WaterRepository.loadSettings(context)
        WaterRepository.saveSettings(context, currentSettings.copy(snoozeUntilMs = snoozeMs))

        NotificationManagerCompat.from(context).cancel(notifId)
        Toast.makeText(context.applicationContext, "Hydration reminder snoozed for 30m", Toast.LENGTH_SHORT).show()
    }

    private fun handleCompleteHabit(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 4201)

        HabitRoutineRepository.markHabitComplete(context, habitId, LocalDate.now().toString())
        NotificationManagerCompat.from(context).cancel(notifId)

        val habit = HabitRoutineRepository.getHabitById(context, habitId)
        val name = habit?.name ?: "Habit"
        Toast.makeText(context.applicationContext, "✓ '$name' marked as completed!", Toast.LENGTH_SHORT).show()
    }

    private fun handleCompleteTask(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, taskId.hashCode())

        val task = TaskRepository.getTaskById(context, taskId)
        if (task != null && !task.isCompleted) {
            TaskRepository.toggleTaskCompletion(context, taskId)
        }
        NotificationManagerCompat.from(context).cancel(notifId)

        val title = task?.title ?: "Task"
        Toast.makeText(context.applicationContext, "✓ '$title' completed!", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_LOG_WATER = "com.yakhrevan.lifetrack.ACTION_LOG_WATER"
        const val ACTION_SNOOZE_WATER = "com.yakhrevan.lifetrack.ACTION_SNOOZE_WATER"
        const val ACTION_COMPLETE_HABIT = "com.yakhrevan.lifetrack.ACTION_COMPLETE_HABIT"
        const val ACTION_COMPLETE_TASK = "com.yakhrevan.lifetrack.ACTION_COMPLETE_TASK"

        const val EXTRA_AMOUNT_ML = "extra_amount_ml"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_TASK_ID = "extra_task_id"

        fun createWaterActionPendingIntent(
            context: Context,
            amountMl: Int,
            notifId: Int,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, LifeTrackNotificationReceiver::class.java).apply {
                action = ACTION_LOG_WATER
                putExtra(EXTRA_AMOUNT_ML, amountMl)
                putExtra(EXTRA_NOTIF_ID, notifId)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun createHabitDonePendingIntent(
            context: Context,
            habitId: String,
            notifId: Int,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, LifeTrackNotificationReceiver::class.java).apply {
                action = ACTION_COMPLETE_HABIT
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_NOTIF_ID, notifId)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun createTaskDonePendingIntent(
            context: Context,
            taskId: String,
            notifId: Int,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, LifeTrackNotificationReceiver::class.java).apply {
                action = ACTION_COMPLETE_TASK
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_NOTIF_ID, notifId)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
