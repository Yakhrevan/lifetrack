package com.yakhrevan.lifetrack

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

object VoiceBotActionExecutor {

    suspend fun executePlan(
        context: Context,
        plan: VoiceActionPlan,
        onNavigate: (String) -> Unit = {}
    ): Pair<VoiceActionPlan, String> {
        val updatedActions = mutableListOf<ParsedVoiceAction>()
        val resultMessages = mutableListOf<String>()
        var allSucceeded = true

        for (action in plan.actions) {
            if (!action.isCompleteAndValid) {
                action.status = StepExecutionStatus.FAILURE
                action.resultMessage = action.clarificationPrompt ?: "Incomplete information"
                updatedActions.add(action)
                resultMessages.add(action.resultMessage!!)
                allSucceeded = false
                continue
            }

            action.status = StepExecutionStatus.IN_PROGRESS
            val outcome = executeSingleAction(context, action, onNavigate)
            action.status = if (outcome.isSuccess) StepExecutionStatus.SUCCESS else StepExecutionStatus.FAILURE
            action.resultMessage = outcome.message
            updatedActions.add(action)

            resultMessages.add(outcome.message)
            if (!outcome.isSuccess) {
                allSucceeded = false
            }

            // Record in audit history if it modified data
            if (outcome.isSuccess && action.actionType != VoiceActionType.READ && action.actionType != VoiceActionType.NAVIGATE && action.actionType != VoiceActionType.HELP) {
                VoiceBotHistoryRepository.recordAction(
                    context,
                    VoiceHistoryItem(
                        id = action.id,
                        commandText = plan.userInput,
                        module = action.module,
                        actionSummary = outcome.message,
                        isSuccess = true,
                        affectedRecordId = outcome.recordId,
                        canUndo = outcome.canUndo
                    )
                )
            }
        }

        val updatedPlan = plan.copy(actions = updatedActions)
        val finalResponseText = if (updatedActions.size == 1) {
            updatedActions.first().resultMessage ?: "Action processed."
        } else {
            val successCount = updatedActions.count { it.status == StepExecutionStatus.SUCCESS }
            if (allSucceeded) {
                "Successfully completed all ${updatedActions.size} actions:\n" + resultMessages.joinToString("\n• ", prefix = "• ")
            } else {
                "Completed $successCount of ${updatedActions.size} actions:\n" + resultMessages.joinToString("\n• ", prefix = "• ")
            }
        }

        return Pair(updatedPlan, finalResponseText)
    }

    private fun executeSingleAction(
        context: Context,
        action: ParsedVoiceAction,
        onNavigate: (String) -> Unit
    ): SingleExecutionResult {
        return try {
            when (action.module) {
                VoiceModule.WATER -> executeWater(context, action)
                VoiceModule.EXPENSE -> executeExpense(context, action, isIncome = false)
                VoiceModule.INCOME -> executeExpense(context, action, isIncome = true)
                VoiceModule.TASK -> executeTask(context, action)
                VoiceModule.NOTE -> executeNote(context, action)
                VoiceModule.STUDY -> executeStudy(context, action)
                VoiceModule.HABIT -> executeHabit(context, action)
                VoiceModule.CALENDAR -> executeCalendar(context, action)
                VoiceModule.NAVIGATION -> {
                    onNavigate(action.title)
                    SingleExecutionResult(true, "Navigated to ${action.title}.", canUndo = false)
                }
                VoiceModule.HELP -> {
                    SingleExecutionResult(true, action.resultMessage ?: "I can log expenses, track water, create tasks, and manage notes.", canUndo = false)
                }
                else -> SingleExecutionResult(false, "Unsupported module: ${action.module.label}")
            }
        } catch (e: Exception) {
            SingleExecutionResult(false, "Failed to execute: ${e.message ?: "Unknown error"}")
        }
    }

    // ------------------------------------------
    // Water Operations
    // ------------------------------------------
    private fun executeWater(context: Context, action: ParsedVoiceAction): SingleExecutionResult {
        if (action.actionType == VoiceActionType.READ) {
            val todayTotal = WaterRepository.getDayTotal(WaterRepository.loadEntries(context), LifeTrackRepository(context).loadEntries(), LocalDate.now().toString())
            val goal = WaterRepository.loadSettings(context).dailyGoalMl
            val pct = if (goal > 0) (todayTotal * 100) / goal else 0
            return SingleExecutionResult(
                isSuccess = true,
                message = "You drank $todayTotal mL of water today ($pct% of your $goal mL goal).",
                canUndo = false
            )
        }

        val amountMl = action.amount?.toInt() ?: return SingleExecutionResult(false, "Missing water quantity.")
        val entry = WaterRepository.addEntry(context, amountMl, DrinkType.WATER, note = action.noteOrDescription ?: "Logged by Voice Bot")
        val newTotal = WaterRepository.getDayTotal(WaterRepository.loadEntries(context), LifeTrackRepository(context).loadEntries(), LocalDate.now().toString())

        return SingleExecutionResult(
            isSuccess = true,
            message = "Logged $amountMl mL of water. Today's total is now $newTotal mL.",
            recordId = entry.id,
            canUndo = true
        )
    }

    // ------------------------------------------
    // Expense & Income Operations
    // ------------------------------------------
    private fun executeExpense(context: Context, action: ParsedVoiceAction, isIncome: Boolean): SingleExecutionResult {
        val repo = LifeTrackRepository(context)

        if (action.actionType == VoiceActionType.READ) {
            val logs = repo.loadEntries()
            val targetCategory = if (isIncome) "Income" else "Expense"
            val todayStr = LocalDate.now().toString()
            val relevantLogs = logs.filter { it.category == targetCategory && (action.date == null || it.date == action.date) }
            val sum = relevantLogs.sumOf { MoneyAmounts.value(it.amount) }
            val periodLabel = if (action.date != null) "on ${action.date}" else "in total"
            return SingleExecutionResult(
                isSuccess = true,
                message = "Total $targetCategory $periodLabel is ₹${String.format(java.util.Locale.US, "%.2f", sum)} across ${relevantLogs.size} records.",
                canUndo = false
            )
        }

        val amt = action.amount ?: return SingleExecutionResult(false, "Missing transaction amount.")
        val id = UUID.randomUUID().toString()
        val entry = LogEntry(
            id = id,
            category = if (isIncome) "Income" else "Expense",
            title = action.title.ifBlank { if (isIncome) "Income" else "Expense" },
            amount = amt.toString(),
            date = action.date ?: LocalDate.now().toString(),
            note = action.noteOrDescription ?: "Logged by Voice Bot",
            moneyCategory = action.category ?: "Other"
        )

        val current = repo.loadEntries()
        repo.saveEntries(listOf(entry) + current)

        return SingleExecutionResult(
            isSuccess = true,
            message = "Recorded ${if (isIncome) "income" else "expense"} of ₹$amt for '${entry.title}' (${entry.moneyCategory}).",
            recordId = id,
            canUndo = true
        )
    }

    // ------------------------------------------
    // Task Operations
    // ------------------------------------------
    private fun executeTask(context: Context, action: ParsedVoiceAction): SingleExecutionResult {
        if (action.actionType == VoiceActionType.READ) {
            val tasks = TaskRepository.getTodayTasks(context)
            return if (tasks.isEmpty()) {
                SingleExecutionResult(true, "You have no pending tasks scheduled for today.", canUndo = false)
            } else {
                val listStr = tasks.take(5).joinToString(", ") { it.title }
                SingleExecutionResult(true, "You have ${tasks.size} tasks for today: $listStr", canUndo = false)
            }
        }

        if (action.actionType == VoiceActionType.COMPLETE) {
            val allTasks = TaskRepository.getTasks(context)
            val match = allTasks.firstOrNull { it.title.contains(action.title, ignoreCase = true) && !it.isCompleted }
                ?: allTasks.firstOrNull { it.title.contains(action.title, ignoreCase = true) }

            if (match != null) {
                TaskRepository.toggleTaskCompletion(context, match.id)
                return SingleExecutionResult(true, "Marked task '${match.title}' as completed.", recordId = match.id, canUndo = true)
            } else {
                return SingleExecutionResult(false, "Could not find a task matching '${action.title}'.")
            }
        }

        // CREATE Task
        val taskId = UUID.randomUUID().toString()
        val hasReminder = !action.time.isNullOrBlank()
        val task = TaskItem(
            id = taskId,
            title = action.title.ifBlank { "Task" },
            dueDate = action.date ?: LocalDate.now().toString(),
            dueTime = action.time,
            priority = action.priority ?: "Medium",
            reminderEnabled = hasReminder,
            reminderTime = action.time,
            notificationMessage = "Reminder: ${action.title.ifBlank { "Task" }}"
        )
        TaskRepository.saveTask(context, task)

        val dueStr = if (task.dueDate == LocalDate.now().toString()) "today" else "for ${task.dueDate}"
        val msg = if (hasReminder) {
            "Created task '${task.title}' with reminder at ${task.dueTime} ($dueStr)."
        } else {
            "Created task '${task.title}' ($dueStr, ${task.priority} priority)."
        }
        return SingleExecutionResult(
            isSuccess = true,
            message = msg,
            recordId = taskId,
            canUndo = true
        )
    }

    // ------------------------------------------
    // Note Operations
    // ------------------------------------------
    private fun executeNote(context: Context, action: ParsedVoiceAction): SingleExecutionResult {
        if (action.actionType == VoiceActionType.READ) {
            val notes = NoteRepository.getNotes(context)
            return if (notes.isEmpty()) {
                SingleExecutionResult(true, "You have no saved notes yet.", canUndo = false)
            } else {
                val titles = notes.take(3).joinToString(", ") { it.title }
                SingleExecutionResult(true, "You have ${notes.size} notes: $titles", canUndo = false)
            }
        }

        val noteId = UUID.randomUUID().toString()
        val note = NoteItem(
            id = noteId,
            title = action.title.ifBlank { "Voice Note" },
            content = action.noteOrDescription ?: action.title
        )
        NoteRepository.saveNote(context, note)

        return SingleExecutionResult(
            isSuccess = true,
            message = "Saved note '${note.title}'.",
            recordId = noteId,
            canUndo = true
        )
    }

    // ------------------------------------------
    // Study Operations
    // ------------------------------------------
    private fun executeStudy(context: Context, action: ParsedVoiceAction): SingleExecutionResult {
        if (action.actionType == VoiceActionType.READ) {
            val mins = StudyRepository.loadSessions(context).filter { it.date == LocalDate.now().toString() && it.isCompleted }.sumOf { it.focusMinutes }
            return SingleExecutionResult(true, "You have studied for $mins minutes today.", canUndo = false)
        }

        val duration = action.durationMinutes ?: 30
        val subjectName = action.title.ifBlank { "General Study" }
        val sessionId = UUID.randomUUID().toString()
        val session = StudySessionRecord(
            id = sessionId,
            subjectName = subjectName,
            focusMinutes = duration,
            date = action.date ?: LocalDate.now().toString(),
            notes = action.noteOrDescription ?: "Voice Bot log"
        )
        StudyRepository.saveSession(context, session)

        // Also record in central tracker log
        val repo = LifeTrackRepository(context)
        val logRow = LogEntry(
            id = sessionId,
            category = "Study",
            title = subjectName,
            amount = duration.toString(),
            date = action.date ?: LocalDate.now().toString(),
            note = "Voice logged session"
        )
        repo.saveEntries(listOf(logRow) + repo.loadEntries())

        return SingleExecutionResult(
            isSuccess = true,
            message = "Logged $duration mins study session for '$subjectName'.",
            recordId = sessionId,
            canUndo = true
        )
    }

    // ------------------------------------------
    // Habit Operations
    // ------------------------------------------
    private fun executeHabit(context: Context, action: ParsedVoiceAction): SingleExecutionResult {
        val habits = HabitRoutineRepository.getHabits(context)
        val match = habits.firstOrNull { it.name.contains(action.title, ignoreCase = true) }

        if (action.actionType == VoiceActionType.COMPLETE) {
            if (match == null) {
                return SingleExecutionResult(false, "Could not find a habit named '${action.title}'.")
            }
            val completion = HabitCompletion(
                habitId = match.id,
                date = action.date ?: LocalDate.now().toString(),
                status = "completed",
                note = "Voice completed"
            )
            HabitRoutineRepository.saveCompletion(context, completion)
            return SingleExecutionResult(true, "Marked habit '${match.name}' as completed for today.", recordId = completion.id, canUndo = true)
        }

        // CREATE Habit
        val habitId = UUID.randomUUID().toString()
        val newHabit = Habit(
            id = habitId,
            name = action.title.ifBlank { "Daily Habit" },
            category = action.category ?: "General"
        )
        HabitRoutineRepository.saveHabit(context, newHabit)
        return SingleExecutionResult(true, "Created habit '${newHabit.name}'.", recordId = habitId, canUndo = true)
    }

    // ------------------------------------------
    // Calendar Operations
    // ------------------------------------------
    private fun executeCalendar(context: Context, action: ParsedVoiceAction): SingleExecutionResult {
        val eventId = UUID.randomUUID().toString()
        val item = CalendarItem(
            id = eventId,
            title = action.title.ifBlank { "Event" },
            type = ActivityType.EVENT,
            date = action.date ?: LocalDate.now().plusDays(1).toString(),
            startTime = action.time ?: "09:00 AM",
            description = action.noteOrDescription ?: "Added via Voice Bot"
        )
        val current = CalendarStorage.load(context)
        CalendarStorage.save(context, listOf(item) + current)

        return SingleExecutionResult(
            isSuccess = true,
            message = "Scheduled '${item.title}' on your calendar for ${item.date} at ${item.startTime}.",
            recordId = eventId,
            canUndo = true
        )
    }

    // ------------------------------------------
    // Undo Support
    // ------------------------------------------
    fun undoAction(context: Context, historyItem: VoiceHistoryItem): Boolean {
        val recordId = historyItem.affectedRecordId ?: return false
        return try {
            when (historyItem.module) {
                VoiceModule.WATER -> {
                    WaterRepository.deleteEntry(context, recordId)
                    true
                }
                VoiceModule.EXPENSE, VoiceModule.INCOME -> {
                    val repo = LifeTrackRepository(context)
                    val updated = repo.loadEntries().filterNot { it.id == recordId }
                    repo.saveEntries(updated)
                    true
                }
                VoiceModule.TASK -> {
                    TaskRepository.deleteTask(context, recordId)
                    true
                }
                VoiceModule.NOTE -> {
                    NoteRepository.deleteNote(context, recordId)
                    true
                }
                VoiceModule.STUDY -> {
                    val repo = LifeTrackRepository(context)
                    repo.saveEntries(repo.loadEntries().filterNot { it.id == recordId })
                    true
                }
                VoiceModule.CALENDAR -> {
                    val current = CalendarStorage.load(context).filterNot { it.id == recordId }
                    CalendarStorage.save(context, current)
                    true
                }
                else -> false
            }.also {
                if (it) {
                    VoiceBotHistoryRepository.markUndone(context, historyItem.id)
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}

private data class SingleExecutionResult(
    val isSuccess: Boolean,
    val message: String,
    val recordId: String? = null,
    val canUndo: Boolean = false
)
