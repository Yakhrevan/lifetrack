package com.yakhrevan.lifetrack

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// ==========================================
// 1. EXTRACTED ENTITY CONTAINER
// ==========================================

data class ExtractedEntities(
    val amount: Double? = null,
    val unit: String? = null,
    val date: String? = null,
    val time: String? = null,
    val durationMinutes: Int? = null,
    val cleanedText: String = "",
    val categoryHint: String? = null
)

// ==========================================
// 2. PARSER INTERFACE & IMPLEMENTATION
// ==========================================

interface VoiceIntentInterpreter {
    suspend fun parse(input: String): VoiceActionPlan
}

object LocalVoiceIntentParser : VoiceIntentInterpreter {

    override suspend fun parse(input: String): VoiceActionPlan {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return VoiceActionPlan(
                userInput = input,
                actions = emptyList(),
                spokenSummary = "I didn't hear anything. Please try again."
            )
        }

        val lang = detectLanguage(trimmed)

        // Check for compound requests
        val subCommandStrings = splitCompoundCommands(trimmed)
        val actions = mutableListOf<ParsedVoiceAction>()

        for (cmd in subCommandStrings) {
            val action = parseSingleCommand(cmd, lang)
            actions.add(action)
        }

        val summary = generatePlanSummary(actions, lang)

        return VoiceActionPlan(
            userInput = trimmed,
            detectedLanguage = lang,
            actions = actions,
            spokenSummary = summary
        )
    }

    // ------------------------------------------
    // Compound Command Splitter
    // ------------------------------------------
    private fun splitCompoundCommands(text: String): List<String> {
        val s = text.trim()

        // Common Tanglish / Tamil / English compound patterns
        // e.g. "Tomorrow ROS2 study session create panni calendar-la add pannu"
        val panniPattern = Regex("(?i)(.*\\b(?:create|log|add)\\b.*)\\s+(?:panni|pannitu|செய்து)\\s+(.*)", RegexOption.IGNORE_CASE)
        val matchPanni = panniPattern.find(s)
        if (matchPanni != null) {
            val part1 = matchPanni.groupValues[1].trim()
            val part2 = matchPanni.groupValues[2].trim()
            if (part1.isNotEmpty() && part2.isNotEmpty()) {
                return listOf(part1, part2)
            }
        }

        // Standard conjunctions: " and ", " & ", " aprom ", " appuram ", " அப்புறம் "
        val conjunctions = listOf(" and ", " & ", " aprom ", " appuram ", " apram ", " அப்புறம் ", " மற்றும் ")
        for (conj in conjunctions) {
            if (s.contains(conj, ignoreCase = true)) {
                val parts = s.split(Regex("(?i)$conj")).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    return parts
                }
            }
        }

        return listOf(s)
    }

    // ------------------------------------------
    // Single Command Parser
    // ------------------------------------------
    private fun parseSingleCommand(cmd: String, lang: String): ParsedVoiceAction {
        val lower = cmd.lowercase(Locale.ROOT)
        val entities = extractEntities(cmd)

        // 1. NAVIGATION
        if (matchesAny(lower, "go to ", "open ", "view ", "show screen", "திற", "போ", "ponga", "screen kaattu")) {
            val target = when {
                matchesAny(lower, "task", "tasks", "பணி", "வேலை") -> "Tasks"
                matchesAny(lower, "note", "notes", "குறிப்பு") -> "Notes"
                matchesAny(lower, "water", "hydration", "தண்ணீர்") -> "Water"
                matchesAny(lower, "study", "focus", "படிப்பு") -> "Study"
                matchesAny(lower, "habit", "routine", "பழக்கம்") -> "Habits"
                matchesAny(lower, "calendar", "நாட்காட்டி") -> "Calendar"
                matchesAny(lower, "money", "expense", "budget", "பணம்", "செலவு") -> "Money"
                matchesAny(lower, "insight", "analytics", "புள்ளிவிவரம்") -> "Insights"
                matchesAny(lower, "setting", "settings", "அமைப்புகள்") -> "Settings"
                matchesAny(lower, "profile", "சுயவிவரம்") -> "Profile"
                matchesAny(lower, "home", "முகப்பு") -> "Home"
                else -> null
            }
            if (target != null) {
                return ParsedVoiceAction(
                    rawText = cmd,
                    module = VoiceModule.NAVIGATION,
                    actionType = VoiceActionType.NAVIGATE,
                    title = target,
                    resultMessage = "Navigating to $target"
                )
            }
        }

        // 2. APP HELP
        if (matchesAny(lower, "what can you do", "help", "who are you", "features", "நீ என்ன செய்ய முடியும்", "உதவி")) {
            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.HELP,
                actionType = VoiceActionType.HELP,
                title = "LifeTrack Voice Agent Help",
                resultMessage = "I can log expenses, track water, create tasks, take notes, log study time, schedule calendar events, and check insights."
            )
        }

        // 3. WATER
        val isWaterQuery = matchesAny(lower, "how much water", "water total", "water today", "thanni evlo", "தண்ணீர் எவ்வளவு")
        if (isWaterQuery) {
            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.WATER,
                actionType = VoiceActionType.READ,
                title = "Today's Water Intake"
            )
        }
        val isWaterLogging = matchesAny(lower, "water", "thanni", "தண்ணீர்", "drank", "kudichen", "குடித்தேன்", "ml", "liter", "litre")
        if (isWaterLogging) {
            val amount = entities.amount ?: when {
                matchesAny(lower, "half bottle", "arai bottle") -> 375.0
                matchesAny(lower, "one bottle", "1 bottle", "oru bottle") -> 750.0
                matchesAny(lower, "glass", "damler", "tumbler", "டம்ளர்") -> 250.0
                else -> null
            }

            if (amount == null) {
                return ParsedVoiceAction(
                    rawText = cmd,
                    module = VoiceModule.WATER,
                    actionType = VoiceActionType.CREATE,
                    missingFields = listOf("amount"),
                    clarificationPrompt = "How much water did you drink in ml or bottles? (எவ்வளவு தண்ணீர் குடித்தீர்கள்?)"
                )
            }

            val finalMl = if (entities.unit == "L" || entities.unit == "litre" || entities.unit == "liter") {
                amount * 1000.0
            } else {
                amount
            }

            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.WATER,
                actionType = VoiceActionType.CREATE,
                title = "Water intake",
                amount = finalMl,
                unit = "ml",
                date = entities.date ?: LocalDate.now().toString(),
                time = entities.time ?: LocalTime.now().toString().take(5)
            )
        }

        // 4. EXPENSES & INCOME
        val isIncome = matchesAny(lower, "salary", "income", "received", "earned", "வருமானம்", "சம்பளம்", "கிடைத்தது", "panam vanthuchu")
        val isExpenseQuery = matchesAny(lower, "how much did i spend", "total expense", "spending today", "spending this week", "evlo selavu", "எவ்வளவு செலவு")
        val isExpense = matchesAny(lower, "spent", "paid", "bought", "expense", "cost", "செலவு", "வாங்கினேன்", "கொடுத்தேன்", "selavu", "vaanginen", "kuduthen", "rs", "₹", "rupees", "ruba")

        if (isExpenseQuery) {
            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.EXPENSE,
                actionType = VoiceActionType.READ,
                title = "Spending Query",
                date = entities.date
            )
        }

        if (isIncome || isExpense) {
            val module = if (isIncome) VoiceModule.INCOME else VoiceModule.EXPENSE
            val amount = entities.amount

            if (amount == null) {
                return ParsedVoiceAction(
                    rawText = cmd,
                    module = module,
                    actionType = VoiceActionType.CREATE,
                    missingFields = listOf("amount"),
                    clarificationPrompt = "How much was the ${if (isIncome) "income" else "expense"}? (எவ்வளவு ரூபாய்?)"
                )
            }

            // Extract purpose / title
            val cleanTitle = cleanExpenseTitle(cmd, isIncome)
            val category = entities.categoryHint ?: detectExpenseCategory(cleanTitle)

            return ParsedVoiceAction(
                rawText = cmd,
                module = module,
                actionType = VoiceActionType.CREATE,
                title = cleanTitle.ifBlank { if (isIncome) "Income" else "Expense" },
                amount = amount,
                unit = "INR",
                category = category,
                date = entities.date ?: LocalDate.now().toString(),
                requiresConfirmation = false // Direct voice logging with undo option
            )
        }

        // 5. TASKS & REMINDERS (Evaluated before study session logging so "Remind me to study at 7 PM" creates a task)
        val isTaskComplete = matchesAny(lower, "complete task", "finish task", "task done", "task mudichiten", "பணி முடிந்தது")
        if (isTaskComplete) {
            val title = cleanTaskTitle(cmd)
            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.TASK,
                actionType = VoiceActionType.COMPLETE,
                title = title.ifBlank { "Task" },
                requiresConfirmation = false
            )
        }

        val isTaskQuery = matchesAny(lower, "show tasks", "my tasks", "pending tasks", "tasks today", "enna tasks", "பணிகள்")
        if (isTaskQuery) {
            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.TASK,
                actionType = VoiceActionType.READ,
                title = "Pending Tasks Query"
            )
        }

        val isReminder = matchesAny(lower, "remind me", "reminder", "remind", "ஞாபகப்படுத்து", "remind pannu", "remind panu", "alarm")
        val isTaskCreate = isReminder || matchesAny(lower, "task", "todo", "to-do", "வேலை", "பணி")
        if (isTaskCreate) {
            val title = cleanTaskTitle(cmd)
            if (title.isBlank()) {
                return ParsedVoiceAction(
                    rawText = cmd,
                    module = VoiceModule.TASK,
                    actionType = VoiceActionType.CREATE,
                    missingFields = listOf("title"),
                    clarificationPrompt = "What is the task title? (டாஸ்க்கின் பெயர் என்ன?)"
                )
            }

            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.TASK,
                actionType = VoiceActionType.CREATE,
                title = title,
                date = entities.date ?: LocalDate.now().toString(),
                time = entities.time,
                priority = if (matchesAny(lower, "urgent", "high priority", "important", "முக்கியம்")) "High" else "Medium",
                requiresConfirmation = false
            )
        }

        // 6. STUDY LOGS
        val isStudyQuery = matchesAny(lower, "how much study", "study time", "studied today", "evlo neram padichen", "படிப்பு நேரம்")
        if (isStudyQuery) {
            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.STUDY,
                actionType = VoiceActionType.READ,
                title = "Study Progress Query"
            )
        }
        val isStudy = matchesAny(lower, "study", "studied", "learning", "padichen", "படிப்பு", "படித்தேன்", "revision")
        if (isStudy) {
            val duration = entities.durationMinutes ?: entities.amount?.toInt()
            val subject = cleanStudySubject(cmd)

            if (duration == null) {
                return ParsedVoiceAction(
                    rawText = cmd,
                    module = VoiceModule.STUDY,
                    actionType = VoiceActionType.CREATE,
                    title = subject.ifBlank { "Study session" },
                    missingFields = listOf("duration"),
                    clarificationPrompt = "How many minutes or hours did you study? (எத்தனை நிமிடங்கள் படித்தீர்கள்?)"
                )
            }

            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.STUDY,
                actionType = VoiceActionType.CREATE,
                title = subject.ifBlank { "Study session" },
                durationMinutes = duration,
                date = entities.date ?: LocalDate.now().toString(),
                requiresConfirmation = false
            )
        }

        // 7. NOTES
        val isNote = matchesAny(lower, "note", "notes", "memo", "குறிப்பு", "எழுதி வை")
        if (isNote) {
            val content = cleanNoteContent(cmd)
            if (content.isBlank()) {
                return ParsedVoiceAction(
                    rawText = cmd,
                    module = VoiceModule.NOTE,
                    actionType = VoiceActionType.CREATE,
                    missingFields = listOf("content"),
                    clarificationPrompt = "What would you like to write in the note? (குறிப்பில் என்ன எழுத வேண்டும்?)"
                )
            }

            val title = if (content.contains(":")) {
                content.substringBefore(":").trim()
            } else {
                content.take(30).trim()
            }

            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.NOTE,
                actionType = VoiceActionType.CREATE,
                title = title,
                noteOrDescription = content,
                date = LocalDate.now().toString()
            )
        }

        // 8. HABITS
        val isHabitDone = matchesAny(lower, "habit completed", "habit done", "completed habit", "பழக்கம் முடிந்தது")
        if (isHabitDone) {
            val title = cleanHabitTitle(cmd)
            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.HABIT,
                actionType = VoiceActionType.COMPLETE,
                title = title.ifBlank { "Habit" },
                date = entities.date ?: LocalDate.now().toString()
            )
        }

        // 9. CALENDAR
        val isCalendar = matchesAny(lower, "calendar", "event", "meeting", "appointment", "நிகழ்வு", "மீட்டிங்")
        if (isCalendar) {
            val title = cleanCalendarTitle(cmd)
            return ParsedVoiceAction(
                rawText = cmd,
                module = VoiceModule.CALENDAR,
                actionType = VoiceActionType.CREATE,
                title = title.ifBlank { "Calendar Event" },
                date = entities.date ?: LocalDate.now().plusDays(1).toString(),
                time = entities.time ?: "09:00"
            )
        }

        // 10. UNKNOWN / GENERAL FALLBACK
        return ParsedVoiceAction(
            rawText = cmd,
            module = VoiceModule.UNKNOWN,
            actionType = VoiceActionType.HELP,
            title = "Command Not Understood",
            missingFields = listOf("intent"),
            clarificationPrompt = "I couldn't identify the module or action. You can say 'I drank 500ml water', '₹150 for lunch', or 'Task finish ROS2 assignment'."
        )
    }

    // ------------------------------------------
    // Entity Extraction Helpers
    // ------------------------------------------
    private fun extractEntities(text: String): ExtractedEntities {
        val s = text.lowercase(Locale.ROOT)

        // 1. Amount Extraction
        val numberRegex = Regex("(?:rs\\.?|inr|₹|ruba)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:rs|rupees|ruba|ரூபாய்|ml|l|litres?|liters?|hours?|hrs?|mins?|minutes?|மணி|நிமிடங்கள்)?")
        val match = numberRegex.find(s)
        val amount = match?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        // 2. Unit Extraction
        val unit = when {
            matchesAny(s, "ml", "மில்லி") -> "ml"
            matchesAny(s, "litre", "liter", "லிட்டர்") -> "L"
            matchesAny(s, "rs", "₹", "rupee", "ruba", "ரூபாய்") -> "INR"
            matchesAny(s, "hour", "hrs", "மணி") -> "hours"
            matchesAny(s, "min", "minute", "நிமிடம்") -> "minutes"
            else -> null
        }

        // 3. Duration Extraction
        var durationMinutes: Int? = null
        if (unit == "hours") {
            durationMinutes = amount?.let { (it * 60).toInt() }
        } else if (unit == "minutes") {
            durationMinutes = amount?.toInt()
        } else {
            val hrMatch = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(?:hours?|hrs?|மணி)").find(s)
            val minMatch = Regex("([0-9]+)\\s*(?:mins?|minutes?|நிமிடம்)").find(s)
            if (hrMatch != null) {
                durationMinutes = ((hrMatch.groupValues[1].toDoubleOrNull() ?: 1.0) * 60).toInt()
            } else if (minMatch != null) {
                durationMinutes = minMatch.groupValues[1].toIntOrNull()
            }
        }

        // 4. Date Extraction (Relative & Days)
        val today = LocalDate.now()
        val date = when {
            matchesAny(s, "today", "iniku", "இன்று") -> today.toString()
            matchesAny(s, "tomorrow", "naalaiku", "நாளை") -> today.plusDays(1).toString()
            matchesAny(s, "yesterday", "nethu", "நேற்று") -> today.minusDays(1).toString()
            matchesAny(s, "day after tomorrow", "naalaniku") -> today.plusDays(2).toString()
            matchesAny(s, "next monday", "adutha thingal") -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString()
            matchesAny(s, "next tuesday") -> today.with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).toString()
            matchesAny(s, "next wednesday") -> today.with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY)).toString()
            matchesAny(s, "next thursday") -> today.with(TemporalAdjusters.next(DayOfWeek.THURSDAY)).toString()
            matchesAny(s, "next friday") -> today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).toString()
            matchesAny(s, "next saturday") -> today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString()
            matchesAny(s, "next sunday") -> today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).toString()
            else -> null
        }

        // 5. Time Extraction
        val timeRegex = Regex("\\b([0-1]?[0-9]|2[0-3])(?::([0-5][0-9]))?\\s*(am|pm)\\b")
        val timeMatch = timeRegex.find(s)
        var formattedTime: String? = null
        if (timeMatch != null) {
            val hourRaw = timeMatch.groupValues[1].toInt()
            val minRaw = timeMatch.groupValues[2].ifBlank { "00" }
            val ampm = timeMatch.groupValues[3]
            var hour24 = hourRaw
            if (ampm == "pm" && hour24 < 12) hour24 += 12
            if (ampm == "am" && hour24 == 12) hour24 = 0
            formattedTime = String.format(Locale.US, "%02d:%s", hour24, minRaw)
        } else {
            // Tamil or conversational formats: "7 mani ku", "7 மணிக்கு", "evening 7", "night 8", "morning 6"
            val tamilManiRegex = Regex("(?i)\\b([0-1]?[0-9]|2[0-3])(?::([0-5][0-9]))?\\s*(?:mani|மணி|manikku|o'?clock)\\b")
            val maniMatch = tamilManiRegex.find(s)
            if (maniMatch != null) {
                var hourRaw = maniMatch.groupValues[1].toInt()
                val minRaw = maniMatch.groupValues[2].ifBlank { "00" }
                val isEvening = matchesAny(s, "evening", "night", "maalai", "iravu", "sayanthram", "pm")
                val isMorning = matchesAny(s, "morning", "kaalai", "vidiyarkaala", "am")
                if (isEvening && hourRaw < 12) {
                    hourRaw += 12
                } else if (!isMorning && hourRaw in 1..6) {
                    hourRaw += 12
                }
                formattedTime = String.format(Locale.US, "%02d:%s", hourRaw, minRaw)
            } else {
                val periodHourRegex = Regex("(?i)\\b(?:at|evening|night|morning|maalai|iravu|kaalai)\\s+([0-1]?[0-9])\\b")
                val phMatch = periodHourRegex.find(s)
                if (phMatch != null) {
                    var hourRaw = phMatch.groupValues[1].toInt()
                    val isEvening = matchesAny(s, "evening", "night", "maalai", "iravu")
                    if (isEvening && hourRaw < 12) hourRaw += 12
                    formattedTime = String.format(Locale.US, "%02d:00", hourRaw)
                }
            }
        }

        return ExtractedEntities(
            amount = amount,
            unit = unit,
            date = date,
            time = formattedTime,
            durationMinutes = durationMinutes,
            cleanedText = text
        )
    }

    // ------------------------------------------
    // String Cleaning & Categories
    // ------------------------------------------
    private fun cleanExpenseTitle(cmd: String, isIncome: Boolean): String {
        var clean = cmd
        val stripWords = listOf(
            "spent", "paid", "bought", "expense", "for", "on", "received", "earned", "salary",
            "income", "rs", "₹", "rupees", "ruba", "ரூபாய்", "செலவு", "வாங்கினேன்", "கொடுத்தேன்",
            "vaanginen", "kuduthen", "selavu", "panam", "iniku", "today", "yesterday", "ku", "kaga"
        )
        for (w in stripWords) {
            clean = clean.replace(Regex("(?i)\\b$w\\b"), " ")
        }
        clean = clean.replace(Regex("[0-9]+(?:\\.[0-9]+)?"), " ").trim()
        clean = clean.replace(Regex("\\s+"), " ").trim()
        val res = clean.ifBlank { if (isIncome) "Income" else "Expense" }
        return res.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun detectExpenseCategory(title: String): String {
        val s = title.lowercase(Locale.ROOT)
        return when {
            matchesAny(s, "petrol", "diesel", "fuel", "gas", "cng", "எரிபொருள்") -> "Fuel"
            matchesAny(s, "food", "lunch", "dinner", "breakfast", "tea", "coffee", "chai", "snack", "biryani", "hotel", "restaurant", "சாப்பாடு", "உணவு") -> "Food"
            matchesAny(s, "auto", "cab", "uber", "ola", "bus", "train", "flight", "ticket", "fare", "போக்குவரத்து") -> "Transport"
            matchesAny(s, "movie", "game", "ott", "netflix", "cinema", "திரைப்படம்") -> "Entertainment"
            matchesAny(s, "book", "course", "fees", "exam", "tution", "புத்தகம்") -> "Study"
            matchesAny(s, "groceries", "supermarket", "vegetable", "milk", "paal", "மளிகை") -> "Groceries"
            matchesAny(s, "doctor", "medicine", "tablet", "hospital", "மருந்து") -> "Health"
            matchesAny(s, "recharge", "wifi", "bill", "electricity", "eb") -> "Bills"
            matchesAny(s, "dress", "shirt", "pant", "cloth", "clothing", "துணி") -> "Clothing"
            else -> "Other"
        }
    }

    private fun cleanTaskTitle(cmd: String): String {
        var clean = cmd
        val stripWords = listOf(
            "remind me to", "remind me", "reminder for", "reminder", "set reminder", "set a reminder",
            "remind pannu", "remind panu", "ஞாபகப்படுத்து", "ஞாபகம்", "add task", "create task",
            "new task", "complete task", "finish task", "task add pannu", "task", "todo", "to-do",
            "tomorrow", "today", "naalaiku", "iniku", "urgent", "by", "for", "பணி", "வேலை",
            "panna", "pannu", "podu", "ku"
        )
        for (w in stripWords) {
            clean = clean.replace(Regex("(?i)\\b$w\\b"), " ")
        }
        clean = clean.replace(Regex("(?i)\\b(?:at\\s+)?([0-1]?[0-9]|2[0-3])(?::([0-5][0-9]))?\\s*(?:am|pm|mani|மணி|manikku|o'?clock)\\b"), " ")
        clean = clean.replace(Regex("(?i)\\b(?:at\\s+)?(?:evening|night|morning|maalai|iravu|kaalai)\\s+([0-1]?[0-9])\\b"), " ")
        clean = clean.replace(Regex("(?i)\\bat\\b"), " ")
        clean = clean.replace(Regex("\\s+"), " ").trim()
        val res = clean.ifBlank { "Task" }
        return res.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun cleanStudySubject(cmd: String): String {
        var clean = cmd
        val stripWords = listOf(
            "studied", "study", "learning", "for", "mins", "minutes", "hours", "hrs", "padichen",
            "மணி", "படித்தேன்", "படிப்பு", "today", "iniku", "yesterday"
        )
        for (w in stripWords) {
            clean = clean.replace(Regex("(?i)\\b$w\\b"), "")
        }
        clean = clean.replace(Regex("[0-9]+"), "").trim()
        return clean.replace(Regex("\\s+"), " ").trim()
    }

    private fun cleanNoteContent(cmd: String): String {
        var clean = cmd
        val stripWords = listOf("take note", "create note", "add note", "new note", "note", "குறிப்பு", "எழுது")
        for (w in stripWords) {
            clean = clean.replace(Regex("(?i)\\b$w\\b"), "")
        }
        return clean.replace(Regex("\\s+"), " ").trim()
    }

    private fun cleanHabitTitle(cmd: String): String {
        var clean = cmd
        val stripWords = listOf("habit completed", "completed habit", "habit done", "habit", "பழக்கம்", "done")
        for (w in stripWords) {
            clean = clean.replace(Regex("(?i)\\b$w\\b"), "")
        }
        return clean.replace(Regex("\\s+"), " ").trim()
    }

    private fun cleanCalendarTitle(cmd: String): String {
        var clean = cmd
        val stripWords = listOf("add event", "calendar", "event", "schedule", "add to calendar", "calendar-la add pannu", "நாட்காட்டி")
        for (w in stripWords) {
            clean = clean.replace(Regex("(?i)\\b$w\\b"), "")
        }
        return clean.replace(Regex("\\s+"), " ").trim()
    }

    private fun matchesAny(text: String, vararg keywords: String): Boolean {
        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true)) return true
        }
        return false
    }

    private fun detectLanguage(text: String): String {
        val hasTamil = text.any { it in '\u0B80'..'\u0BFF' }
        if (hasTamil) return "ta-IN"
        val tanglishKeywords = listOf("kudichen", "padichen", "sapten", "vanthuchu", "pannu", "podu", "iniku", "naalaiku", "nethu", "thanni", "ruba", "selavu")
        if (tanglishKeywords.any { text.contains(it, ignoreCase = true) }) return "ta-Tanglish"
        return "en-IN"
    }

    private fun generatePlanSummary(actions: List<ParsedVoiceAction>, lang: String): String {
        if (actions.isEmpty()) return "No actions identified."
        val isTamil = lang == "ta-IN" || lang == "ta-Tanglish"

        if (actions.size == 1) {
            val a = actions[0]
            if (!a.isCompleteAndValid) {
                return a.clarificationPrompt ?: "Please provide more details."
            }
            return when (a.module) {
                VoiceModule.WATER -> if (isTamil) "${a.amount?.toInt() ?: 0} மிலி தண்ணீர் பதிவு செய்ய தயார்." else "Ready to log ${a.amount?.toInt() ?: 0} ml of water."
                VoiceModule.EXPENSE -> if (isTamil) "ரூ. ${a.amount} ${a.title} செலவு பதிவு செய்ய தயார்." else "Ready to record ₹${a.amount} expense for ${a.title}."
                VoiceModule.INCOME -> if (isTamil) "ரூ. ${a.amount} வருமானம் பதிவு செய்ய தயார்." else "Ready to record ₹${a.amount} income."
                VoiceModule.TASK -> if (a.actionType == VoiceActionType.COMPLETE) "Ready to mark task '${a.title}' complete." else "Ready to create task '${a.title}'."
                VoiceModule.STUDY -> if (isTamil) "${a.durationMinutes} நிமிட படிப்பு பதிவு செய்ய தயார்." else "Ready to log ${a.durationMinutes} mins study for ${a.title}."
                VoiceModule.NOTE -> "Ready to create note '${a.title}'."
                VoiceModule.CALENDAR -> "Ready to schedule '${a.title}' on calendar."
                VoiceModule.NAVIGATION -> "Opening ${a.title}."
                VoiceModule.HELP -> a.resultMessage ?: "Here is what I can do."
                else -> "Ready to proceed."
            }
        }

        return if (isTamil) "${actions.size} செயல்கள் கண்டறியப்பட்டன. உறுதி செய்து தொடரவும்." else "Found ${actions.size} actions in your request. Ready to execute."
    }
}
