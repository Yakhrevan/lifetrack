package com.yakhrevan.lifetrack

import java.util.UUID

// ==========================================
// 1. STATE & CORE ENUMS
// ==========================================

enum class VoiceBotState {
    IDLE,
    LISTENING,
    PROCESSING,
    CLARIFICATION,
    CONFIRMATION,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class VoiceModule(val label: String, val iconName: String) {
    EXPENSE("Expense", "receipt"),
    INCOME("Income", "wallet"),
    TASK("Task", "check_circle"),
    NOTE("Note", "description"),
    HABIT("Habit", "repeat"),
    ROUTINE("Routine", "schedule"),
    WATER("Water", "water_drop"),
    STUDY("Study", "timer"),
    GOAL("Goal", "flag"),
    CALENDAR("Calendar", "event"),
    INSIGHTS("Insights", "analytics"),
    NAVIGATION("Navigation", "explore"),
    HELP("Help", "help"),
    UNKNOWN("General", "mic")
}

enum class VoiceActionType(val label: String) {
    CREATE("Create"),
    READ("Query / Read"),
    UPDATE("Update"),
    COMPLETE("Mark Complete"),
    RESCHEDULE("Reschedule"),
    ARCHIVE("Archive"),
    DELETE("Delete"),
    NAVIGATE("Navigate"),
    HELP("App Help")
}

enum class StepExecutionStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    FAILURE,
    SKIPPED
}

// ==========================================
// 2. PARSED ACTIONS & PLAN
// ==========================================

data class ParsedVoiceAction(
    val id: String = UUID.randomUUID().toString(),
    val rawText: String,
    val module: VoiceModule,
    val actionType: VoiceActionType,
    val title: String = "",
    val amount: Double? = null,
    val unit: String? = null,
    val category: String? = null,
    val date: String? = null, // YYYY-MM-DD
    val time: String? = null, // HH:mm
    val durationMinutes: Int? = null,
    val noteOrDescription: String? = null,
    val priority: String? = null,
    val targetRecordId: String? = null,
    val requiresConfirmation: Boolean = false,
    val missingFields: List<String> = emptyList(),
    val clarificationPrompt: String? = null,
    var status: StepExecutionStatus = StepExecutionStatus.PENDING,
    var resultMessage: String? = null
) {
    val isCompleteAndValid: Boolean
        get() = missingFields.isEmpty() && clarificationPrompt == null
}

data class VoiceActionPlan(
    val id: String = UUID.randomUUID().toString(),
    val userInput: String,
    val detectedLanguage: String = "en-IN",
    val actions: List<ParsedVoiceAction>,
    val spokenSummary: String = ""
) {
    val isMultiStep: Boolean get() = actions.size > 1
    val isFullyResolved: Boolean get() = actions.all { it.isCompleteAndValid }
    val requiresConfirmation: Boolean get() = actions.any { it.requiresConfirmation }
    val nextClarificationAction: ParsedVoiceAction? get() = actions.firstOrNull { !it.isCompleteAndValid }
}

// ==========================================
// 3. AUDIT & HISTORY ITEM
// ==========================================

data class VoiceHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val commandText: String,
    val module: VoiceModule,
    val actionSummary: String,
    val isSuccess: Boolean = true,
    val affectedRecordId: String? = null,
    val canUndo: Boolean = false,
    val isUndone: Boolean = false
)
