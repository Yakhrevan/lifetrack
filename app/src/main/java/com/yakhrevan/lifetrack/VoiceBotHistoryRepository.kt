package com.yakhrevan.lifetrack

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object VoiceBotHistoryRepository {
    private const val PREFS_NAME = "lifetrack_voice_bot_history"
    private const val KEY_HISTORY = "voice_action_history"
    private const val MAX_HISTORY = 25

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHistory(context: Context): List<VoiceHistoryItem> {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<VoiceHistoryItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VoiceHistoryItem(
                        id = obj.optString("id"),
                        timestamp = obj.optLong("timestamp"),
                        commandText = obj.optString("commandText"),
                        module = VoiceModule.valueOf(obj.optString("module", VoiceModule.UNKNOWN.name)),
                        actionSummary = obj.optString("actionSummary"),
                        isSuccess = obj.optBoolean("isSuccess", true),
                        affectedRecordId = obj.optString("affectedRecordId").takeIf { it.isNotBlank() },
                        canUndo = obj.optBoolean("canUndo", false),
                        isUndone = obj.optBoolean("isUndone", false)
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        }.getOrDefault(emptyList())
    }

    fun recordAction(context: Context, item: VoiceHistoryItem) {
        val current = getHistory(context).toMutableList()
        current.add(0, item)
        val trimmed = current.take(MAX_HISTORY)
        persistHistory(context, trimmed)
    }

    fun markUndone(context: Context, historyId: String) {
        val current = getHistory(context).toMutableList()
        val index = current.indexOfFirst { it.id == historyId }
        if (index != -1) {
            current[index] = current[index].copy(isUndone = true)
            persistHistory(context, current)
        }
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(KEY_HISTORY).apply()
    }

    private fun persistHistory(context: Context, list: List<VoiceHistoryItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("commandText", item.commandText)
                put("module", item.module.name)
                put("actionSummary", item.actionSummary)
                put("isSuccess", item.isSuccess)
                put("affectedRecordId", item.affectedRecordId ?: "")
                put("canUndo", item.canUndo)
                put("isUndone", item.isUndone)
            }
            arr.put(obj)
        }
        prefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }
}
