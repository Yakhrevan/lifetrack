package com.yakhrevan.lifetrack

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// ==========================================
// 1. DATA MODELS
// ==========================================

data class SubtaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false
)

data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val priority: String = "Medium", // "Low", "Medium", "High"
    val startDate: String? = null, // YYYY-MM-DD
    val dueDate: String = LocalDate.now().toString(), // YYYY-MM-DD
    val dueTime: String? = null, // HH:mm
    val isCompleted: Boolean = false,
    val completedAtMs: Long? = null,
    val subtasks: List<SubtaskItem> = emptyList(),
    val category: String = "",
    val iconName: String = "clipboard",
    val colorHex: String = "#8B5CF6", // Purple
    val repeatType: String = "Does not repeat", // "Does not repeat", "Daily", "Weekly", "Monthly"
    val reminderEnabled: Boolean = false,
    val reminderTime: String? = null, // HH:mm
    val reminderDays: Set<Int> = emptySet(),
    val notificationMessage: String = "",
    val snoozeMinutes: Int = 10,
    val isArchived: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class TaskCategory(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconName: String = "clipboard",
    val colorHex: String = "#8B5CF6"
)

// ==========================================
// 2. REPOSITORY & ENGINE
// ==========================================

object TaskRepository {
    private const val PREFS_NAME = "lifetrack_tasks_v2"
    private const val KEY_TASKS = "tasks_v2"
    private const val KEY_CATEGORIES = "task_categories_v2"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Strictly returns only saved user tasks. Never seeds default content.
     */
    fun getTasks(context: Context, includeArchived: Boolean = false): List<TaskItem> {
        val raw = prefs(context).getString(KEY_TASKS, null)
        val tasks = if (raw.isNullOrBlank()) emptyList() else decodeTasks(raw)
        return if (includeArchived) tasks else tasks.filterNot { it.isArchived }
    }

    fun getTaskById(context: Context, id: String): TaskItem? {
        return getTasks(context, includeArchived = true).find { it.id == id }
    }

    fun saveTask(context: Context, task: TaskItem) {
        val current = getTasks(context, includeArchived = true).toMutableList()
        val index = current.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            current[index] = task
        } else {
            current.add(0, task) // Newest first
        }
        prefs(context).edit().putString(KEY_TASKS, encodeTasks(current)).apply()
        TaskNotificationScheduler.reconcileTaskReminder(context, task)
    }

    fun toggleTaskCompletion(context: Context, id: String): Boolean {
        val task = getTaskById(context, id) ?: return false
        val newStatus = !task.isCompleted
        val updated = task.copy(
            isCompleted = newStatus,
            completedAtMs = if (newStatus) System.currentTimeMillis() else null
        )
        saveTask(context, updated)
        return newStatus
    }

    fun toggleSubtaskCompletion(context: Context, taskId: String, subtaskId: String): Boolean {
        val task = getTaskById(context, taskId) ?: return false
        val updatedSubtasks = task.subtasks.map { sub ->
            if (sub.id == subtaskId) sub.copy(isCompleted = !sub.isCompleted) else sub
        }
        saveTask(context, task.copy(subtasks = updatedSubtasks))
        return true
    }

    fun addSubtask(context: Context, taskId: String, title: String) {
        if (title.isBlank()) return
        val task = getTaskById(context, taskId) ?: return
        val updatedSubtasks = task.subtasks + SubtaskItem(title = title.trim())
        saveTask(context, task.copy(subtasks = updatedSubtasks))
    }

    fun duplicateTask(context: Context, id: String): TaskItem? {
        val existing = getTaskById(context, id) ?: return null
        val duplicated = existing.copy(
            id = UUID.randomUUID().toString(),
            title = "${existing.title} (Copy)",
            isCompleted = false,
            completedAtMs = null,
            subtasks = existing.subtasks.map { it.copy(id = UUID.randomUUID().toString(), isCompleted = false) },
            createdAtMs = System.currentTimeMillis()
        )
        saveTask(context, duplicated)
        return duplicated
    }

    fun toggleArchiveTask(context: Context, id: String): Boolean {
        val task = getTaskById(context, id) ?: return false
        val updated = task.copy(isArchived = !task.isArchived)
        saveTask(context, updated)
        return updated.isArchived
    }

    fun deleteTask(context: Context, id: String) {
        val task = getTaskById(context, id)
        val current = getTasks(context, includeArchived = true).filterNot { it.id == id }
        prefs(context).edit().putString(KEY_TASKS, encodeTasks(current)).apply()
        if (task != null) {
            TaskNotificationScheduler.cancelTaskReminder(context, task)
        }
    }

    // ------------------------------------------
    // FILTERED QUERIES
    // ------------------------------------------

    fun getTodayTasks(context: Context): List<TaskItem> {
        val todayStr = LocalDate.now().toString()
        return getTasks(context).filter { !it.isCompleted && it.dueDate == todayStr }
    }

    fun getUpcomingTasks(context: Context): List<TaskItem> {
        val today = LocalDate.now()
        return getTasks(context).filter { task ->
            if (task.isCompleted) return@filter false
            val due = runCatching { LocalDate.parse(task.dueDate) }.getOrNull()
            due != null && due.isAfter(today)
        }
    }

    fun getOverdueTasks(context: Context): List<TaskItem> {
        val today = LocalDate.now()
        return getTasks(context).filter { task ->
            if (task.isCompleted) return@filter false
            val due = runCatching { LocalDate.parse(task.dueDate) }.getOrNull()
            due != null && due.isBefore(today)
        }
    }

    fun getCompletedTasks(context: Context): List<TaskItem> {
        return getTasks(context).filter { it.isCompleted }
    }

    fun getTasksForDate(context: Context, date: LocalDate): List<TaskItem> {
        val dateStr = date.toString()
        return getTasks(context).filter { it.dueDate == dateStr }
    }

    // ------------------------------------------
    // USER CATEGORIES
    // ------------------------------------------

    fun getCategories(context: Context): List<TaskCategory> {
        val raw = prefs(context).getString(KEY_CATEGORIES, null)
        return if (raw.isNullOrBlank()) emptyList() else decodeCategories(raw)
    }

    fun saveCategory(context: Context, category: TaskCategory) {
        val current = getCategories(context).toMutableList()
        val index = current.indexOfFirst { it.id == category.id || it.name.equals(category.name, ignoreCase = true) }
        if (index >= 0) {
            current[index] = category
        } else {
            current.add(category)
        }
        prefs(context).edit().putString(KEY_CATEGORIES, encodeCategories(current)).apply()
    }

    fun deleteCategory(context: Context, id: String) {
        val current = getCategories(context).filterNot { it.id == id }
        prefs(context).edit().putString(KEY_CATEGORIES, encodeCategories(current)).apply()
    }

    // ------------------------------------------
    // JSON SERIALIZATION
    // ------------------------------------------

    private fun encodeTasks(tasks: List<TaskItem>): String {
        val arr = JSONArray()
        for (t in tasks) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("title", t.title)
            obj.put("description", t.description)
            obj.put("priority", t.priority)
            if (t.startDate != null) obj.put("startDate", t.startDate)
            obj.put("dueDate", t.dueDate)
            if (t.dueTime != null) obj.put("dueTime", t.dueTime)
            obj.put("isCompleted", t.isCompleted)
            if (t.completedAtMs != null) obj.put("completedAtMs", t.completedAtMs)
            obj.put("category", t.category)
            obj.put("iconName", t.iconName)
            obj.put("colorHex", t.colorHex)
            obj.put("repeatType", t.repeatType)
            obj.put("reminderEnabled", t.reminderEnabled)
            if (t.reminderTime != null) obj.put("reminderTime", t.reminderTime)
            obj.put("reminderDays", JSONArray(t.reminderDays.sorted()))
            obj.put("notificationMessage", t.notificationMessage)
            obj.put("snoozeMinutes", t.snoozeMinutes)
            obj.put("isArchived", t.isArchived)
            obj.put("createdAtMs", t.createdAtMs)

            val subArr = JSONArray()
            for (sub in t.subtasks) {
                val sObj = JSONObject()
                sObj.put("id", sub.id)
                sObj.put("title", sub.title)
                sObj.put("isCompleted", sub.isCompleted)
                subArr.put(sObj)
            }
            obj.put("subtasks", subArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeTasks(raw: String): List<TaskItem> = runCatching {
        val arr = JSONArray(raw)
        val list = mutableListOf<TaskItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val subArr = obj.optJSONArray("subtasks")
            val subtasks = mutableListOf<SubtaskItem>()
            if (subArr != null) {
                for (j in 0 until subArr.length()) {
                    val sObj = subArr.optJSONObject(j) ?: continue
                    subtasks.add(
                        SubtaskItem(
                            id = sObj.optString("id", UUID.randomUUID().toString()),
                            title = sObj.optString("title", ""),
                            isCompleted = sObj.optBoolean("isCompleted", false)
                        )
                    )
                }
            }

            val rDaysArr = obj.optJSONArray("reminderDays")
            val rDays = if (rDaysArr != null) (0 until rDaysArr.length()).map { rDaysArr.optInt(it) }.toSet() else emptySet()

            list.add(
                TaskItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", "Untitled Task"),
                    description = obj.optString("description", ""),
                    priority = obj.optString("priority", "Medium"),
                    startDate = if (obj.has("startDate")) obj.optString("startDate") else null,
                    dueDate = obj.optString("dueDate", LocalDate.now().toString()),
                    dueTime = if (obj.has("dueTime")) obj.optString("dueTime") else null,
                    isCompleted = obj.optBoolean("isCompleted", false),
                    completedAtMs = if (obj.has("completedAtMs")) obj.optLong("completedAtMs") else null,
                    subtasks = subtasks,
                    category = obj.optString("category", ""),
                    iconName = obj.optString("iconName", "clipboard"),
                    colorHex = obj.optString("colorHex", "#8B5CF6"),
                    repeatType = obj.optString("repeatType", "Does not repeat"),
                    reminderEnabled = obj.optBoolean("reminderEnabled", false),
                    reminderTime = if (obj.has("reminderTime")) obj.optString("reminderTime") else null,
                    reminderDays = rDays,
                    notificationMessage = obj.optString("notificationMessage", ""),
                    snoozeMinutes = obj.optInt("snoozeMinutes", 10),
                    isArchived = obj.optBoolean("isArchived", false),
                    createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                )
            )
        }
        list
    }.getOrDefault(emptyList())

    private fun encodeCategories(categories: List<TaskCategory>): String {
        val arr = JSONArray()
        for (c in categories) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("iconName", c.iconName)
            obj.put("colorHex", c.colorHex)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeCategories(raw: String): List<TaskCategory> = runCatching {
        val arr = JSONArray(raw)
        val list = mutableListOf<TaskCategory>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                TaskCategory(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "Category"),
                    iconName = obj.optString("iconName", "clipboard"),
                    colorHex = obj.optString("colorHex", "#8B5CF6")
                )
            )
        }
        list
    }.getOrDefault(emptyList())
}
