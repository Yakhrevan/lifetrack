package com.yakhrevan.lifetrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class HabitReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (!NotificationPolicy.canNotify(applicationContext, NotificationPolicy.Module.HABITS)) return Result.success()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationPolicy.builder(applicationContext, NotificationPolicy.Module.HABITS, "Habit reminders")
            .setContentTitle("LifeTrack habit reminder")
            .setContentText("Take a moment to check in with your routine.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(4201, notification)
        return Result.success()
    }
}
