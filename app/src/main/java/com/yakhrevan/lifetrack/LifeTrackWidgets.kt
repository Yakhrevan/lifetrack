package com.yakhrevan.lifetrack

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

/** Native RemoteViews: no background service or third-party account required. */
object LifeTrackWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(TaskCountWidget::class.java, WaterProgressWidget::class.java, HabitStreakWidget::class.java)
            .forEach { type ->
                manager.getAppWidgetIds(ComponentName(context, type)).forEach { id ->
                    update(context, manager, id, type)
                }
            }
    }

    fun update(context: Context, manager: AppWidgetManager, id: Int, type: Class<*>) {
        val today = LocalDate.now()
        val views = RemoteViews(context.packageName, R.layout.lifetrack_widget)
        val destination: String
        when (type) {
            TaskCountWidget::class.java -> {
                val tasks = TaskRepository.getTasks(context).filter { TaskReminderSchedule.isScheduledFor(it, today) }
                val remaining = tasks.count { !TaskReminderSchedule.completedFor(it, today) }
                views.setTextViewText(R.id.widget_title, "Today's Tasks")
                views.setTextViewText(R.id.widget_summary, "$remaining remaining · ${tasks.size} planned")
                destination = "Tasks"
            }
            WaterProgressWidget::class.java -> {
                val total = WaterRepository.loadEntries(context).filter { it.date == today.toString() }.sumOf { it.amountMl }
                val goal = WaterRepository.loadSettings(context).dailyGoalMl
                views.setTextViewText(R.id.widget_title, "Water Progress")
                views.setTextViewText(R.id.widget_summary, "$total / $goal ml")
                destination = "Water"
            }
            else -> {
                val habits = HabitRoutineRepository.getHabits(context)
                    .filter { !it.isPaused && HabitRoutineRepository.isHabitScheduled(it, today) }
                val completeIds = HabitRoutineRepository.getCompletionsForDate(context, today.toString())
                    .filter { it.status == "completed" }.mapTo(mutableSetOf()) { it.habitId }
                views.setTextViewText(R.id.widget_title, "Habit Progress")
                views.setTextViewText(R.id.widget_summary, "${habits.count { it.id in completeIds }} / ${habits.size} completed")
                destination = "Habits"
            }
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", destination)
        }
        val pending = PendingIntent.getActivity(context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        manager.updateAppWidget(id, views)
    }
}

open class LifeTrackBaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { LifeTrackWidgetUpdater.update(context, manager, it, this::class.java) }
    }
}
class TaskCountWidget : LifeTrackBaseWidget()
class WaterProgressWidget : LifeTrackBaseWidget()
class HabitStreakWidget : LifeTrackBaseWidget()
