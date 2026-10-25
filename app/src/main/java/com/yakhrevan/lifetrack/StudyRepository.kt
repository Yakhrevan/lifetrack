package com.yakhrevan.lifetrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.UUID

// ==========================================
// 1. DATA MODELS
// ==========================================

data class Subject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val icon: String = "book", // "book", "code", "chip", "cube", "flask", "palette", "globe", "math"
    val color: Long = 0xFFA855F7L,
    val weeklyGoalHours: Float = 0f,
    val notes: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val order: Int = 0
)

enum class TopicStatus(val label: String) {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    NEEDS_REVISION("Needs Revision")
}

data class Topic(
    val id: String = UUID.randomUUID().toString(),
    val subjectId: String,
    val title: String,
    val description: String = "",
    val icon: String = "book",
    val color: Long = 0xFFA855F7L,
    val targetDurationMinutes: Int = 0,
    val notes: String = "",
    val status: TopicStatus = TopicStatus.NOT_STARTED,
    val dueDate: String = "", // "YYYY-MM-DD"
    val revisionScheduledDate: String = "", // "YYYY-MM-DD"
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class StudySessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val subjectId: String? = null,
    val topicId: String? = null,
    val subjectName: String = "",
    val topicName: String = "",
    val focusMinutes: Int = 0,
    val breakMinutes: Int = 0,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val sessionType: String = "Focus", // "Focus", "Pomodoro", "Stopwatch", "Custom"
    val notes: String = "",
    val date: String = "", // "YYYY-MM-DD"
    val isCompleted: Boolean = true
)

data class Flashcard(
    val id: String = UUID.randomUUID().toString(),
    val subjectId: String? = null,
    val topicId: String? = null,
    val question: String,
    val answer: String,
    val explanation: String = "",
    val tags: List<String> = emptyList(),
    val qualityRating: Int = 0, // 0: unrated, 1: Again, 2: Hard, 3: Good, 4: Easy
    val lastReviewedAt: Long = 0L,
    val nextReviewDate: String = "" // "YYYY-MM-DD"
)

data class QuizQuestion(
    val id: String = UUID.randomUUID().toString(),
    val subjectId: String? = null,
    val topicId: String? = null,
    val question: String,
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int = 0,
    val explanation: String = ""
)

data class QuizAttempt(
    val id: String = UUID.randomUUID().toString(),
    val subjectId: String? = null,
    val subjectName: String = "",
    val date: String = "",
    val score: Int = 0,
    val totalQuestions: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

enum class StudyResourceType(val label: String) {
    NOTE("Note"),
    LINK("Link"),
    BOOKMARK("Bookmark"),
    DOCUMENT("Document")
}

data class StudyResource(
    val id: String = UUID.randomUUID().toString(),
    val subjectId: String? = null,
    val topicId: String? = null,
    val title: String,
    val contentOrUrl: String,
    val type: StudyResourceType = StudyResourceType.NOTE,
    val date: String = "",
    val tags: List<String> = emptyList()
)

data class StudySettings(
    val studyReminderEnabled: Boolean = true,
    val breakReminderEnabled: Boolean = true,
    val dailyGoalReminderEnabled: Boolean = true,
    val defaultFocusMinutes: Int = 25,
    val defaultBreakMinutes: Int = 5,
    val autoStartBreak: Boolean = false,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0
)

enum class StudyPeriod(val label: String) {
    TODAY("Today"),
    WEEK("Week"),
    MONTH("Month"),
    CUSTOM("Custom")
}

data class SubjectDistributionItem(
    val subjectName: String,
    val minutes: Int,
    val percentage: Float,
    val color: Long
)

data class StudyAchievement(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val progressText: String,
    val progressFraction: Float,
    val iconName: String
)

// ==========================================
// 2. REPOSITORY & STORAGE IMPLEMENTATION
// ==========================================

object StudyRepository {
    private const val PREFS = "lifetrack_study_v2"
    private const val KEY_SUBJECTS = "study_subjects"
    private const val KEY_TOPICS = "study_topics"
    private const val KEY_SESSIONS = "study_session_records"
    private const val KEY_FLASHCARDS = "study_flashcards"
    private const val KEY_QUIZZES = "study_quiz_questions"
    private const val KEY_QUIZ_ATTEMPTS = "study_quiz_attempts"
    private const val KEY_RESOURCES = "study_resources"
    private const val KEY_SETTINGS = "study_settings"

    // ------------------------------------------
    // Subjects CRUD
    // ------------------------------------------
    fun loadSubjects(context: Context): List<Subject> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SUBJECTS, null)
        val list = mutableListOf<Subject>()

        if (!jsonStr.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        Subject(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "Untitled"),
                            description = obj.optString("description", ""),
                            icon = obj.optString("icon", "book"),
                            color = obj.optLong("color", 0xFFA855F7L),
                            weeklyGoalHours = obj.optDouble("weeklyGoalHours", 0.0).toFloat(),
                            notes = obj.optString("notes", ""),
                            archived = obj.optBoolean("archived", false),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            order = obj.optInt("order", i)
                        )
                    )
                }
            }
        }

        // Migrate legacy projects from "lifetrack_study" if present and not migrated
        val legacyPrefs = context.applicationContext.getSharedPreferences("lifetrack_study", Context.MODE_PRIVATE)
        val legacyRaw = legacyPrefs.getString("study_projects", null)
        if (!legacyRaw.isNullOrBlank() && list.isEmpty()) {
            val legacyProjects = ProjectLogic.decode(legacyRaw)
            legacyProjects.forEachIndexed { idx, p ->
                list.add(
                    Subject(
                        id = p.id,
                        name = p.name,
                        archived = p.archived,
                        order = idx
                    )
                )
            }
            saveSubjects(context, list)
        }

        return list.sortedBy { it.order }
    }

    fun saveSubjects(context: Context, subjects: List<Subject>) {
        val arr = JSONArray()
        subjects.forEachIndexed { idx, s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("description", s.description)
                put("icon", s.icon)
                put("color", s.color)
                put("weeklyGoalHours", s.weeklyGoalHours.toDouble())
                put("notes", s.notes)
                put("archived", s.archived)
                put("createdAt", s.createdAt)
                put("order", idx)
            }
            arr.put(obj)
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SUBJECTS, arr.toString())
            .apply()
    }

    fun deleteSubject(context: Context, subjectId: String) {
        val subjects = loadSubjects(context).filterNot { it.id == subjectId }
        saveSubjects(context, subjects)
        // Decouple associated topics rather than corrupting them
        val topics = loadTopics(context).filterNot { it.subjectId == subjectId }
        saveTopics(context, topics)
    }

    // ------------------------------------------
    // Topics CRUD
    // ------------------------------------------
    fun loadTopics(context: Context): List<Topic> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TOPICS, null) ?: return emptyList()
        val list = mutableListOf<Topic>()
        runCatching {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Topic(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        subjectId = obj.optString("subjectId", ""),
                        title = obj.optString("title", "Untitled Topic"),
                        description = obj.optString("description", ""),
                        icon = obj.optString("icon", "book"),
                        color = obj.optLong("color", 0xFFA855F7L),
                        targetDurationMinutes = obj.optInt("targetDurationMinutes", 0),
                        notes = obj.optString("notes", ""),
                        status = runCatching { TopicStatus.valueOf(obj.optString("status", TopicStatus.NOT_STARTED.name)) }.getOrDefault(TopicStatus.NOT_STARTED),
                        dueDate = obj.optString("dueDate", ""),
                        revisionScheduledDate = obj.optString("revisionScheduledDate", ""),
                        order = obj.optInt("order", i),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
        return list.sortedBy { it.order }
    }

    fun saveTopics(context: Context, topics: List<Topic>) {
        val arr = JSONArray()
        topics.forEachIndexed { idx, t ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("subjectId", t.subjectId)
                put("title", t.title)
                put("description", t.description)
                put("icon", t.icon)
                put("color", t.color)
                put("targetDurationMinutes", t.targetDurationMinutes)
                put("notes", t.notes)
                put("status", t.status.name)
                put("dueDate", t.dueDate)
                put("revisionScheduledDate", t.revisionScheduledDate)
                put("order", idx)
                put("createdAt", t.createdAt)
            }
            arr.put(obj)
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOPICS, arr.toString())
            .apply()
    }

    fun deleteTopic(context: Context, topicId: String) {
        val topics = loadTopics(context).filterNot { it.id == topicId }
        saveTopics(context, topics)
    }

    // ------------------------------------------
    // Sessions Records CRUD
    // ------------------------------------------
    fun loadSessions(context: Context): List<StudySessionRecord> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        val list = mutableListOf<StudySessionRecord>()
        runCatching {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    StudySessionRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        subjectId = obj.optString("subjectId", "").takeIf { it.isNotEmpty() },
                        topicId = obj.optString("topicId", "").takeIf { it.isNotEmpty() },
                        subjectName = obj.optString("subjectName", "Study session"),
                        topicName = obj.optString("topicName", ""),
                        focusMinutes = obj.optInt("focusMinutes", 0),
                        breakMinutes = obj.optInt("breakMinutes", 0),
                        startTimeMs = obj.optLong("startTimeMs", 0L),
                        endTimeMs = obj.optLong("endTimeMs", 0L),
                        sessionType = obj.optString("sessionType", "Focus"),
                        notes = obj.optString("notes", ""),
                        date = obj.optString("date", LocalDate.now().toString()),
                        isCompleted = obj.optBoolean("isCompleted", true)
                    )
                )
            }
        }
        return list.sortedByDescending { it.startTimeMs }
    }

    fun saveSession(context: Context, session: StudySessionRecord) {
        val existing = loadSessions(context).filterNot { it.id == session.id }
        val updated = listOf(session) + existing
        saveSessionsList(context, updated)
    }

    fun deleteSession(context: Context, sessionId: String) {
        val updated = loadSessions(context).filterNot { it.id == sessionId }
        saveSessionsList(context, updated)
    }

    private fun saveSessionsList(context: Context, sessions: List<StudySessionRecord>) {
        val arr = JSONArray()
        sessions.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("subjectId", s.subjectId.orEmpty())
                put("topicId", s.topicId.orEmpty())
                put("subjectName", s.subjectName)
                put("topicName", s.topicName)
                put("focusMinutes", s.focusMinutes)
                put("breakMinutes", s.breakMinutes)
                put("startTimeMs", s.startTimeMs)
                put("endTimeMs", s.endTimeMs)
                put("sessionType", s.sessionType)
                put("notes", s.notes)
                put("date", s.date)
                put("isCompleted", s.isCompleted)
            }
            arr.put(obj)
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SESSIONS, arr.toString())
            .apply()
    }

    // ------------------------------------------
    // Flashcards CRUD
    // ------------------------------------------
    fun loadFlashcards(context: Context): List<Flashcard> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_FLASHCARDS, null) ?: return emptyList()
        val list = mutableListOf<Flashcard>()
        runCatching {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tagsArr = obj.optJSONArray("tags")
                val tagsList = mutableListOf<String>()
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) tagsList.add(tagsArr.getString(t))
                }
                list.add(
                    Flashcard(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        subjectId = obj.optString("subjectId", "").takeIf { it.isNotEmpty() },
                        topicId = obj.optString("topicId", "").takeIf { it.isNotEmpty() },
                        question = obj.optString("question", ""),
                        answer = obj.optString("answer", ""),
                        explanation = obj.optString("explanation", ""),
                        tags = tagsList,
                        qualityRating = obj.optInt("qualityRating", 0),
                        lastReviewedAt = obj.optLong("lastReviewedAt", 0L),
                        nextReviewDate = obj.optString("nextReviewDate", "")
                    )
                )
            }
        }
        return list
    }

    fun saveFlashcards(context: Context, flashcards: List<Flashcard>) {
        val arr = JSONArray()
        flashcards.forEach { f ->
            val obj = JSONObject().apply {
                put("id", f.id)
                put("subjectId", f.subjectId.orEmpty())
                put("topicId", f.topicId.orEmpty())
                put("question", f.question)
                put("answer", f.answer)
                put("explanation", f.explanation)
                put("qualityRating", f.qualityRating)
                put("lastReviewedAt", f.lastReviewedAt)
                put("nextReviewDate", f.nextReviewDate)
                val tArr = JSONArray()
                f.tags.forEach { tArr.put(it) }
                put("tags", tArr)
            }
            arr.put(obj)
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FLASHCARDS, arr.toString())
            .apply()
    }

    fun deleteFlashcard(context: Context, flashcardId: String) {
        val updated = loadFlashcards(context).filterNot { it.id == flashcardId }
        saveFlashcards(context, updated)
    }

    // ------------------------------------------
    // Quizzes CRUD
    // ------------------------------------------
    fun loadQuizzes(context: Context): List<QuizQuestion> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_QUIZZES, null) ?: return emptyList()
        val list = mutableListOf<QuizQuestion>()
        runCatching {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val optArr = obj.optJSONArray("options")
                val optList = mutableListOf<String>()
                if (optArr != null) {
                    for (o in 0 until optArr.length()) optList.add(optArr.getString(o))
                }
                list.add(
                    QuizQuestion(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        subjectId = obj.optString("subjectId", "").takeIf { it.isNotEmpty() },
                        topicId = obj.optString("topicId", "").takeIf { it.isNotEmpty() },
                        question = obj.optString("question", ""),
                        options = optList,
                        correctOptionIndex = obj.optInt("correctOptionIndex", 0),
                        explanation = obj.optString("explanation", "")
                    )
                )
            }
        }
        return list
    }

    fun saveQuizzes(context: Context, questions: List<QuizQuestion>) {
        val arr = JSONArray()
        questions.forEach { q ->
            val obj = JSONObject().apply {
                put("id", q.id)
                put("subjectId", q.subjectId.orEmpty())
                put("topicId", q.topicId.orEmpty())
                put("question", q.question)
                put("correctOptionIndex", q.correctOptionIndex)
                put("explanation", q.explanation)
                val oArr = JSONArray()
                q.options.forEach { oArr.put(it) }
                put("options", oArr)
            }
            arr.put(obj)
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_QUIZZES, arr.toString())
            .apply()
    }

    fun deleteQuiz(context: Context, questionId: String) {
        val updated = loadQuizzes(context).filterNot { it.id == questionId }
        saveQuizzes(context, updated)
    }

    fun loadQuizAttempts(context: Context): List<QuizAttempt> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_QUIZ_ATTEMPTS, null) ?: return emptyList()
        val list = mutableListOf<QuizAttempt>()
        runCatching {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    QuizAttempt(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        subjectId = obj.optString("subjectId", "").takeIf { it.isNotEmpty() },
                        subjectName = obj.optString("subjectName", ""),
                        date = obj.optString("date", ""),
                        score = obj.optInt("score", 0),
                        totalQuestions = obj.optInt("totalQuestions", 0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun saveQuizAttempt(context: Context, attempt: QuizAttempt) {
        val list = listOf(attempt) + loadQuizAttempts(context)
        val arr = JSONArray()
        list.forEach { a ->
            val obj = JSONObject().apply {
                put("id", a.id)
                put("subjectId", a.subjectId.orEmpty())
                put("subjectName", a.subjectName)
                put("date", a.date)
                put("score", a.score)
                put("totalQuestions", a.totalQuestions)
                put("timestamp", a.timestamp)
            }
            arr.put(obj)
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_QUIZ_ATTEMPTS, arr.toString())
            .apply()
    }

    // ------------------------------------------
    // Resources & Notes CRUD
    // ------------------------------------------
    fun loadResources(context: Context): List<StudyResource> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_RESOURCES, null) ?: return emptyList()
        val list = mutableListOf<StudyResource>()
        runCatching {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tArr = obj.optJSONArray("tags")
                val tList = mutableListOf<String>()
                if (tArr != null) {
                    for (t in 0 until tArr.length()) tList.add(tArr.getString(t))
                }
                list.add(
                    StudyResource(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        subjectId = obj.optString("subjectId", "").takeIf { it.isNotEmpty() },
                        topicId = obj.optString("topicId", "").takeIf { it.isNotEmpty() },
                        title = obj.optString("title", ""),
                        contentOrUrl = obj.optString("contentOrUrl", ""),
                        type = runCatching { StudyResourceType.valueOf(obj.optString("type", StudyResourceType.NOTE.name)) }.getOrDefault(StudyResourceType.NOTE),
                        date = obj.optString("date", ""),
                        tags = tList
                    )
                )
            }
        }
        return list
    }

    fun saveResources(context: Context, resources: List<StudyResource>) {
        val arr = JSONArray()
        resources.forEach { r ->
            val obj = JSONObject().apply {
                put("id", r.id)
                put("subjectId", r.subjectId.orEmpty())
                put("topicId", r.topicId.orEmpty())
                put("title", r.title)
                put("contentOrUrl", r.contentOrUrl)
                put("type", r.type.name)
                put("date", r.date)
                val tArr = JSONArray()
                r.tags.forEach { tArr.put(it) }
                put("tags", tArr)
            }
            arr.put(obj)
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RESOURCES, arr.toString())
            .apply()
    }

    fun deleteResource(context: Context, resourceId: String) {
        val updated = loadResources(context).filterNot { it.id == resourceId }
        saveResources(context, updated)
    }

    // ------------------------------------------
    // Settings CRUD
    // ------------------------------------------
    fun loadSettings(context: Context): StudySettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SETTINGS, null) ?: return StudySettings()
        return runCatching {
            val obj = JSONObject(jsonStr)
            StudySettings(
                studyReminderEnabled = obj.optBoolean("studyReminderEnabled", true),
                breakReminderEnabled = obj.optBoolean("breakReminderEnabled", true),
                dailyGoalReminderEnabled = obj.optBoolean("dailyGoalReminderEnabled", true),
                defaultFocusMinutes = obj.optInt("defaultFocusMinutes", 25),
                defaultBreakMinutes = obj.optInt("defaultBreakMinutes", 5),
                autoStartBreak = obj.optBoolean("autoStartBreak", false),
                reminderHour = obj.optInt("reminderHour", 18),
                reminderMinute = obj.optInt("reminderMinute", 0)
            )
        }.getOrDefault(StudySettings())
    }

    fun saveSettings(context: Context, settings: StudySettings) {
        val obj = JSONObject().apply {
            put("studyReminderEnabled", settings.studyReminderEnabled)
            put("breakReminderEnabled", settings.breakReminderEnabled)
            put("dailyGoalReminderEnabled", settings.dailyGoalReminderEnabled)
            put("defaultFocusMinutes", settings.defaultFocusMinutes)
            put("defaultBreakMinutes", settings.defaultBreakMinutes)
            put("autoStartBreak", settings.autoStartBreak)
            put("reminderHour", settings.reminderHour)
            put("reminderMinute", settings.reminderMinute)
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SETTINGS, obj.toString())
            .apply()

        if (settings.studyReminderEnabled) {
            StudyReminderScheduler.scheduleDailyReminder(context, settings.reminderHour, settings.reminderMinute)
        } else {
            StudyReminderScheduler.cancelDailyReminder(context)
        }
    }

    // ------------------------------------------
    // Analytics & Metrics Calculations
    // ------------------------------------------

    /** Calculates streak strictly from saved qualifying study sessions. */
    fun calculateStreak(logs: List<LogEntry>): Int {
        val studyDates = logs.filter { it.category == "Study" && (it.amount.trim().toIntOrNull() ?: 0) > 0 }
            .map { it.date }
            .toSet()

        if (studyDates.isEmpty()) return 0

        var current = LocalDate.now()
        var streak = 0

        // If no study today, check if yesterday had study (streak still alive)
        if (!studyDates.contains(current.toString())) {
            val yesterday = current.minusDays(1)
            if (studyDates.contains(yesterday.toString())) {
                current = yesterday
            } else {
                return 0
            }
        }

        while (studyDates.contains(current.toString())) {
            streak++
            current = current.minusDays(1)
        }
        return streak
    }

    /** Returns Mon..Sun daily study hours for the current week. */
    fun calculateWeeklyHours(logs: List<LogEntry>): List<Pair<String, Float>> {
        val today = LocalDate.now()
        // Find Monday of this week
        val dayOfWeek = today.dayOfWeek.value // 1 = Monday, 7 = Sunday
        val monday = today.minusDays((dayOfWeek - 1).toLong())

        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong()).toString()
            val minutes = logs.filter { it.category == "Study" && it.date == date }
                .sumOf { it.amount.trim().toIntOrNull() ?: 0 }
            Pair(dayNames[offset], minutes / 60f)
        }
    }

    /** Returns daily study trend points for the past N days. */
    fun calculateDailyTrend(logs: List<LogEntry>, daysCount: Int = 7): List<Pair<String, Float>> {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("EEE")
        return (daysCount - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val dateStr = date.toString()
            val minutes = logs.filter { it.category == "Study" && it.date == dateStr }
                .sumOf { it.amount.trim().toIntOrNull() ?: 0 }
            Pair(date.format(fmt), minutes / 60f)
        }
    }

    /** Calculates real subject-wise distribution from logs and session records. */
    fun calculateSubjectDistribution(
        logs: List<LogEntry>,
        sessions: List<StudySessionRecord>,
        subjects: List<Subject>
    ): List<SubjectDistributionItem> {
        val totalMinutes = logs.filter { it.category == "Study" }.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
        if (totalMinutes <= 0) return emptyList()

        // Map minutes by subject name
        val minutesBySubject = mutableMapOf<String, Int>()
        logs.filter { it.category == "Study" }.forEach { log ->
            val min = log.amount.trim().toIntOrNull() ?: 0
            if (min > 0) {
                // Match with session record or title prefix
                val matchingSession = sessions.firstOrNull { it.notes == log.note && it.date == log.date }
                val subjectName = when {
                    matchingSession != null && matchingSession.subjectName.isNotBlank() -> matchingSession.subjectName
                    log.title.contains(" - ") -> log.title.substringBefore(" - ").trim()
                    log.title.contains(" · ") -> log.title.substringBefore(" · ").trim()
                    else -> log.title.trim()
                }
                minutesBySubject[subjectName] = (minutesBySubject[subjectName] ?: 0) + min
            }
        }

        val colors = listOf(
            0xFFA855F7L, // Purple
            0xFF38BDF8L, // Blue
            0xFF10B981L, // Emerald
            0xFFF59E0BL, // Amber
            0xFFEC4899L, // Pink
            0xFF06B6D4L, // Cyan
            0xFFF43F5EL  // Coral
        )

        var colorIdx = 0
        return minutesBySubject.entries.sortedByDescending { it.value }.map { entry ->
            val subColor = subjects.firstOrNull { it.name.equals(entry.key, ignoreCase = true) }?.color
                ?: colors[colorIdx % colors.size]
            colorIdx++
            SubjectDistributionItem(
                subjectName = entry.key,
                minutes = entry.value,
                percentage = entry.value.toFloat() / totalMinutes.toFloat(),
                color = subColor
            )
        }
    }

    /** Returns achievements with actual live progress calculated from user data. */
    fun calculateAchievements(
        logs: List<LogEntry>,
        sessions: List<StudySessionRecord>,
        subjects: List<Subject>,
        topics: List<Topic>,
        flashcards: List<Flashcard>,
        quizAttempts: List<QuizAttempt>
    ): List<StudyAchievement> {
        val streak = calculateStreak(logs)
        val completedSessionsCount = sessions.count { it.isCompleted } + logs.count { it.category == "Study" && (it.amount.trim().toIntOrNull() ?: 0) > 0 }
        val totalFocusMinutes = logs.filter { it.category == "Study" }.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
        val totalFocusHours = totalFocusMinutes / 60
        val activeSubjects = subjects.count { !it.archived }
        val completedTopics = topics.count { it.status == TopicStatus.COMPLETED }

        return listOf(
            StudyAchievement(
                id = "first_session",
                title = "First Session",
                description = "Complete your first study session",
                isCompleted = completedSessionsCount >= 1,
                progressText = if (completedSessionsCount >= 1) "Completed" else "0/1",
                progressFraction = (completedSessionsCount.toFloat() / 1f).coerceIn(0f, 1f),
                iconName = "play"
            ),
            StudyAchievement(
                id = "streak_3",
                title = "3 Day Streak",
                description = "Study for 3 consecutive days",
                isCompleted = streak >= 3,
                progressText = if (streak >= 3) "Completed" else "$streak/3 days",
                progressFraction = (streak / 3f).coerceIn(0f, 1f),
                iconName = "flame"
            ),
            StudyAchievement(
                id = "streak_7",
                title = "7 Day Streak",
                description = "Study for 7 consecutive days",
                isCompleted = streak >= 7,
                progressText = if (streak >= 7) "Completed" else "$streak/7 days",
                progressFraction = (streak / 7f).coerceIn(0f, 1f),
                iconName = "flame"
            ),
            StudyAchievement(
                id = "focus_master",
                title = "Focus Master",
                description = "Reach 25 hours of total focus time",
                isCompleted = totalFocusHours >= 25,
                progressText = if (totalFocusHours >= 25) "Completed" else "$totalFocusHours/25 hours",
                progressFraction = (totalFocusHours / 25f).coerceIn(0f, 1f),
                iconName = "timer"
            ),
            StudyAchievement(
                id = "subject_explorer",
                title = "Subject Explorer",
                description = "Create 3 user-defined subjects",
                isCompleted = activeSubjects >= 3,
                progressText = if (activeSubjects >= 3) "Completed" else "$activeSubjects/3 subjects",
                progressFraction = (activeSubjects / 3f).coerceIn(0f, 1f),
                iconName = "book"
            ),
            StudyAchievement(
                id = "quiz_ace",
                title = "Quiz Ace",
                description = "Score 100% on a study quiz",
                isCompleted = quizAttempts.any { it.totalQuestions > 0 && it.score == it.totalQuestions },
                progressText = if (quizAttempts.any { it.totalQuestions > 0 && it.score == it.totalQuestions }) "Completed" else "0/1",
                progressFraction = if (quizAttempts.any { it.totalQuestions > 0 && it.score == it.totalQuestions }) 1f else 0f,
                iconName = "star"
            ),
            StudyAchievement(
                id = "syllabus_finisher",
                title = "Syllabus Finisher",
                description = "Mark 5 learning topics as completed",
                isCompleted = completedTopics >= 5,
                progressText = if (completedTopics >= 5) "Completed" else "$completedTopics/5 topics",
                progressFraction = (completedTopics / 5f).coerceIn(0f, 1f),
                iconName = "check"
            ),
            StudyAchievement(
                id = "flashcard_master",
                title = "Recall Apprentice",
                description = "Create 10 active-recall flashcards",
                isCompleted = flashcards.size >= 10,
                progressText = if (flashcards.size >= 10) "Completed" else "${flashcards.size}/10 cards",
                progressFraction = (flashcards.size / 10f).coerceIn(0f, 1f),
                iconName = "card"
            )
        )
    }

    // ------------------------------------------
    // Export / Import / Reset
    // ------------------------------------------
    fun exportDataJson(context: Context): String {
        val root = JSONObject()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("subjects", JSONArray(prefs.getString(KEY_SUBJECTS, "[]")))
        root.put("topics", JSONArray(prefs.getString(KEY_TOPICS, "[]")))
        root.put("sessions", JSONArray(prefs.getString(KEY_SESSIONS, "[]")))
        root.put("flashcards", JSONArray(prefs.getString(KEY_FLASHCARDS, "[]")))
        root.put("quizzes", JSONArray(prefs.getString(KEY_QUIZZES, "[]")))
        root.put("quizAttempts", JSONArray(prefs.getString(KEY_QUIZ_ATTEMPTS, "[]")))
        root.put("resources", JSONArray(prefs.getString(KEY_RESOURCES, "[]")))
        return root.toString(2)
    }

    fun importDataJson(context: Context, jsonStr: String): Boolean {
        return runCatching {
            val root = JSONObject(jsonStr)
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (root.has("subjects")) prefs.putString(KEY_SUBJECTS, root.getJSONArray("subjects").toString())
            if (root.has("topics")) prefs.putString(KEY_TOPICS, root.getJSONArray("topics").toString())
            if (root.has("sessions")) prefs.putString(KEY_SESSIONS, root.getJSONArray("sessions").toString())
            if (root.has("flashcards")) prefs.putString(KEY_FLASHCARDS, root.getJSONArray("flashcards").toString())
            if (root.has("quizzes")) prefs.putString(KEY_QUIZZES, root.getJSONArray("quizzes").toString())
            if (root.has("quizAttempts")) prefs.putString(KEY_QUIZ_ATTEMPTS, root.getJSONArray("quizAttempts").toString())
            if (root.has("resources")) prefs.putString(KEY_RESOURCES, root.getJSONArray("resources").toString())
            prefs.apply()
            true
        }.getOrDefault(false)
    }

    fun clearAllData(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
