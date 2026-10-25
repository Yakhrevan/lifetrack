package com.yakhrevan.lifetrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ==========================================
// 1. DATA MODEL
// ==========================================

data class PersonalGoal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val iconName: String = "fitness",
    val colorHex: String = "#A855F7",
    val targetType: String = "Custom Target",
    val currentAmount: Double = 0.0,
    val targetAmount: Double = 1.0,
    val unit: String = "sessions",
    val startDate: String = "",
    val targetDate: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (targetAmount > 0.0) {
            (currentAmount / targetAmount).toFloat().coerceIn(0f, 1f)
        } else if (isCompleted) 1f else 0f

    val progressPercentage: Int
        get() = (progressFraction * 100).toInt()
}

// ==========================================
// 2. REPOSITORY (STRICT ZERO-SEED)
// ==========================================

object PersonalGoalsRepository {
    private const val PREFS = "lifetrack_personal_goals_v2"
    private const val KEY_GOALS = "user_goals_list"

    /**
     * Retrieves the user's personal goals.
     * Guaranteed zero-seed: Returns emptyList() on fresh install.
     */
    fun getGoals(context: Context): List<PersonalGoal> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_GOALS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<PersonalGoal>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PersonalGoal(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        description = obj.optString("description", ""),
                        iconName = obj.optString("iconName", "fitness"),
                        colorHex = obj.optString("colorHex", "#A855F7"),
                        targetType = obj.optString("targetType", "Custom Target"),
                        currentAmount = obj.optDouble("currentAmount", 0.0),
                        targetAmount = obj.optDouble("targetAmount", 1.0),
                        unit = obj.optString("unit", "sessions"),
                        startDate = obj.optString("startDate", ""),
                        targetDate = obj.optString("targetDate", ""),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveGoal(context: Context, goal: PersonalGoal) {
        val current = getGoals(context).toMutableList()
        val index = current.indexOfFirst { it.id == goal.id }
        if (index != -1) {
            current[index] = goal
        } else {
            current.add(0, goal)
        }
        persistGoals(context, current)
    }

    fun deleteGoal(context: Context, goalId: String) {
        val current = getGoals(context).filterNot { it.id == goalId }
        persistGoals(context, current)
    }

    fun updateProgress(context: Context, goalId: String, newProgress: Double) {
        val current = getGoals(context).toMutableList()
        val index = current.indexOfFirst { it.id == goalId }
        if (index != -1) {
            val goal = current[index]
            val clamped = newProgress.coerceAtLeast(0.0)
            val completed = goal.targetAmount > 0.0 && clamped >= goal.targetAmount
            current[index] = goal.copy(currentAmount = clamped, isCompleted = completed)
            persistGoals(context, current)
        }
    }

    fun incrementProgress(context: Context, goalId: String, delta: Double = 1.0) {
        val current = getGoals(context).toMutableList()
        val index = current.indexOfFirst { it.id == goalId }
        if (index != -1) {
            val goal = current[index]
            val newAmount = (goal.currentAmount + delta).coerceAtLeast(0.0)
            val completed = goal.targetAmount > 0.0 && newAmount >= goal.targetAmount
            current[index] = goal.copy(currentAmount = newAmount, isCompleted = completed)
            persistGoals(context, current)
        }
    }

    fun toggleCompleted(context: Context, goalId: String) {
        val current = getGoals(context).toMutableList()
        val index = current.indexOfFirst { it.id == goalId }
        if (index != -1) {
            val goal = current[index]
            val newCompleted = !goal.isCompleted
            val newAmount = if (newCompleted) goal.targetAmount else 0.0
            current[index] = goal.copy(isCompleted = newCompleted, currentAmount = newAmount)
            persistGoals(context, current)
        }
    }

    private fun persistGoals(context: Context, list: List<PersonalGoal>) {
        val arr = JSONArray()
        list.forEach { goal ->
            val obj = JSONObject().apply {
                put("id", goal.id)
                put("title", goal.title)
                put("description", goal.description)
                put("iconName", goal.iconName)
                put("colorHex", goal.colorHex)
                put("targetType", goal.targetType)
                put("currentAmount", goal.currentAmount)
                put("targetAmount", goal.targetAmount)
                put("unit", goal.unit)
                put("startDate", goal.startDate)
                put("targetDate", goal.targetDate)
                put("isCompleted", goal.isCompleted)
                put("createdAt", goal.createdAt)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_GOALS, arr.toString())
            .apply()
    }
}
