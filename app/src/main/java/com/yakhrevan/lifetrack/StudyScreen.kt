package com.yakhrevan.lifetrack

import android.app.TimePickerDialog
import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

// ==========================================
// 1. DESIGN SYSTEM & COLOR PALETTE
// ==========================================
private val DarkBg         = Color(0xFF070B14)
private val CardBg         = Color(0xFF0E1626)
private val CardInner      = Color(0xFF141F33)
private val CardBorder     = Color(0x1AFFFFFF)
private val TextWhite      = Color(0xFFF1F5F9)
private val TextMuted      = Color(0xFF8896AB)
private val PurplePrimary  = Color(0xFFA855F7)
private val PurpleLight    = Color(0xFFC084FC)
private val VioletBrand    = Color(0xFF6366F1)
private val BlueAccent     = Color(0xFF38BDF8)
private val CyanBrand      = Color(0xFF06B6D4)
private val EmeraldAccent  = Color(0xFF10B981)
private val AmberWarning   = Color(0xFFF59E0B)
private val CoralAccent    = Color(0xFFF43F5E)

// 16 Screen Destinations
enum class StudySubScreen {
    DASHBOARD,          // Screen 1: Study Dashboard
    SUBJECTS,           // Screen 2: Subjects (User Created)
    ADD_EDIT_SUBJECT,   // Screen 3: Add / Edit Subject
    SUBJECT_DETAILS,    // Screen 4: Subject Details & Topics
    ADD_EDIT_TOPIC,     // Screen 5: Add / Edit Topic
    STUDY_SESSION,      // Screen 6 & 7: Study Session (Running)
    SESSION_COMPLETE,   // Screen 8: Session Complete
    STUDY_PLANNER,      // Screen 9: Study Planner (Calendar Integration)
    ANALYTICS,          // Screen 10: Study Analytics
    SESSION_HISTORY,    // Screen 11: Session History
    FLASHCARDS,         // Screen 12: Flashcards & Quiz
    NOTES_RESOURCES,    // Screen 13: Notes & Learning Resources
    GOALS_PROGRESS,     // Screen 14: Goals & Progress
    ACHIEVEMENTS,       // Screen 15: Achievements
    SETTINGS            // Screen 16: Study Settings
}

// ==========================================
// 2. MAIN COMPOSABLE: StudyScreen
// ==========================================
@Composable
fun StudyScreen(
    logs: List<LogEntry>,
    today: String,
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onSaveSession: (title: String, minutes: Int, note: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("lifetrack_study_timer", Context.MODE_PRIVATE) }

    // Screen navigation stack
    var currentScreen by remember { mutableStateOf(StudySubScreen.DASHBOARD) }

    // Selection contexts
    var selectedSubjectId by remember { mutableStateOf<String?>(null) }
    var selectedTopicId by remember { mutableStateOf<String?>(null) }
    var editingSubject by remember { mutableStateOf<Subject?>(null) }
    var editingTopic by remember { mutableStateOf<Topic?>(null) }
    var lastCompletedSession by remember { mutableStateOf<StudySessionRecord?>(null) }

    // Data lists loaded from authoritative persistence
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val subjects = remember(refreshTrigger) { StudyRepository.loadSubjects(context) }
    val topics = remember(refreshTrigger) { StudyRepository.loadTopics(context) }
    val sessions = remember(refreshTrigger) { StudyRepository.loadSessions(context) }
    val flashcards = remember(refreshTrigger) { StudyRepository.loadFlashcards(context) }
    val quizzes = remember(refreshTrigger) { StudyRepository.loadQuizzes(context) }
    val quizAttempts = remember(refreshTrigger) { StudyRepository.loadQuizAttempts(context) }
    val resources = remember(refreshTrigger) { StudyRepository.loadResources(context) }
    val settings = remember(refreshTrigger) { StudyRepository.loadSettings(context) }
    val calendarItems = remember(refreshTrigger) { CalendarStorage.load(context).filter { it.type == ActivityType.STUDY } }

    // Global filters
    var selectedPeriod by remember { mutableStateOf(StudyPeriod.TODAY) }
    var customStartDate by remember { mutableStateOf(LocalDate.now().minusDays(7).toString()) }
    var customEndDate by remember { mutableStateOf(LocalDate.now().toString()) }

    // Wall-clock resilient timer state
    var timerState by remember {
        mutableStateOf(
            TimerState(
                mode = runCatching { TimerMode.valueOf(prefs.getString("timer_mode", "POMODORO") ?: "POMODORO") }.getOrDefault(TimerMode.POMODORO),
                targetMin = prefs.getInt("timer_target", settings.defaultFocusMinutes).coerceIn(1, 720),
                running = prefs.getBoolean("timer_running", false),
                startedAt = prefs.getLong("timer_started", 0L),
                accMs = prefs.getLong("timer_acc", 0L),
                onBreak = prefs.getBoolean("timer_onbreak", false),
                active = prefs.getBoolean("timer_active", false)
            )
        )
    }

    var timerSubjectId by remember { mutableStateOf(prefs.getString("timer_subject_id", null)) }
    var timerTopicId by remember { mutableStateOf(prefs.getString("timer_topic_id", null)) }
    var timerSessionNotes by remember { mutableStateOf(prefs.getString("timer_notes", "") ?: "") }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun persistTimer(t: TimerState) {
        timerState = t
        prefs.edit()
            .putString("timer_mode", t.mode.name)
            .putInt("timer_target", t.targetMin)
            .putBoolean("timer_running", t.running)
            .putLong("timer_started", t.startedAt)
            .putLong("timer_acc", t.accMs)
            .putBoolean("timer_onbreak", t.onBreak)
            .putBoolean("timer_active", t.active)
            .putString("timer_subject_id", timerSubjectId)
            .putString("timer_topic_id", timerTopicId)
            .putString("timer_notes", timerSessionNotes)
            .apply()
    }

    // Timer wall-clock tick
    LaunchedEffect(timerState.running) {
        while (timerState.running) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
        nowMs = System.currentTimeMillis()
    }

    // Auto-pause when timer finishes
    LaunchedEffect(nowMs, timerState.running) {
        if (timerState.running && timerState.isDone(nowMs)) {
            persistTimer(timerState.pause(nowMs))
        }
    }

    // Dialog states
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showGoalEditDialog by remember { mutableStateOf(false) }
    var showAddFlashcardDialog by remember { mutableStateOf(false) }
    var showAddQuizDialog by remember { mutableStateOf(false) }
    var showAddResourceDialog by remember { mutableStateOf(false) }
    var showCustomTimerDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main content area based on current subscreen
            Box(modifier = Modifier.weight(1f)) {
                when (currentScreen) {
                    StudySubScreen.DASHBOARD -> StudyDashboardContent(
                        logs = logs,
                        today = today,
                        subjects = subjects,
                        topics = topics,
                        calendarItems = calendarItems,
                        selectedPeriod = selectedPeriod,
                        onSelectPeriod = { selectedPeriod = it },
                        customStartDate = customStartDate,
                        customEndDate = customEndDate,
                        onCustomStartDateChange = { value ->
                            customStartDate = value
                            if (value > customEndDate) customEndDate = value
                        },
                        onCustomEndDateChange = { value ->
                            customEndDate = if (value < customStartDate) customStartDate else value
                        },
                        onEditDailyGoal = { showGoalEditDialog = true },
                        onOpenCalendar = { currentScreen = StudySubScreen.STUDY_PLANNER },
                        onOpenSettings = { currentScreen = StudySubScreen.SETTINGS },
                        onOpenSubjects = { currentScreen = StudySubScreen.SUBJECTS },
                        onAddSubject = { editingSubject = null; currentScreen = StudySubScreen.ADD_EDIT_SUBJECT },
                        onStartTimer = {
                            if (subjects.isNotEmpty() && timerSubjectId == null) {
                                timerSubjectId = subjects.first().id
                            }
                            currentScreen = StudySubScreen.STUDY_SESSION
                        },
                        onSelectSubject = { subId ->
                            selectedSubjectId = subId
                            currentScreen = StudySubScreen.SUBJECT_DETAILS
                        },
                        onViewAllSessions = { currentScreen = StudySubScreen.SESSION_HISTORY },
                        onOpenAnalytics = { currentScreen = StudySubScreen.ANALYTICS },
                        onOpenGoals = { currentScreen = StudySubScreen.GOALS_PROGRESS }
                    )

                    StudySubScreen.SUBJECTS -> SubjectsListContent(
                        subjects = subjects,
                        topics = topics,
                        logs = logs,
                        onBack = { currentScreen = StudySubScreen.DASHBOARD },
                        onAddSubject = { editingSubject = null; currentScreen = StudySubScreen.ADD_EDIT_SUBJECT },
                        onSelectSubject = { subId ->
                            selectedSubjectId = subId
                            currentScreen = StudySubScreen.SUBJECT_DETAILS
                        }
                    )

                    StudySubScreen.ADD_EDIT_SUBJECT -> AddEditSubjectContent(
                        subject = editingSubject,
                        onBack = { currentScreen = StudySubScreen.SUBJECTS },
                        onSave = { newSub ->
                            val updated = if (editingSubject == null) {
                                subjects + newSub
                            } else {
                                subjects.map { if (it.id == newSub.id) newSub else it }
                            }
                            StudyRepository.saveSubjects(context, updated)
                            refreshTrigger++
                            currentScreen = StudySubScreen.SUBJECTS
                        },
                        onDelete = { subId ->
                            StudyRepository.deleteSubject(context, subId)
                            refreshTrigger++
                            currentScreen = StudySubScreen.SUBJECTS
                        }
                    )

                    StudySubScreen.SUBJECT_DETAILS -> SubjectDetailsContent(
                        subjectId = selectedSubjectId ?: "",
                        subjects = subjects,
                        topics = topics,
                        logs = logs,
                        resources = resources,
                        onBack = { currentScreen = StudySubScreen.SUBJECTS },
                        onEditSubject = { sub ->
                            editingSubject = sub
                            currentScreen = StudySubScreen.ADD_EDIT_SUBJECT
                        },
                        onDeleteSubject = { subId ->
                            StudyRepository.deleteSubject(context, subId)
                            refreshTrigger++
                            currentScreen = StudySubScreen.SUBJECTS
                        },
                        onAddTopic = {
                            editingTopic = null
                            currentScreen = StudySubScreen.ADD_EDIT_TOPIC
                        },
                        onSelectTopic = { top ->
                            editingTopic = top
                            currentScreen = StudySubScreen.ADD_EDIT_TOPIC
                        },
                        onStartTopicTimer = { subId, topId ->
                            timerSubjectId = subId
                            timerTopicId = topId
                            currentScreen = StudySubScreen.STUDY_SESSION
                        },
                        onAddResource = { showAddResourceDialog = true }
                    )

                    StudySubScreen.ADD_EDIT_TOPIC -> AddEditTopicContent(
                        topic = editingTopic,
                        subjectId = selectedSubjectId ?: "",
                        subjects = subjects,
                        onBack = { currentScreen = StudySubScreen.SUBJECT_DETAILS },
                        onSave = { newTopic ->
                            val updated = if (editingTopic == null) {
                                topics + newTopic
                            } else {
                                topics.map { if (it.id == newTopic.id) newTopic else it }
                            }
                            StudyRepository.saveTopics(context, updated)
                            refreshTrigger++
                            currentScreen = StudySubScreen.SUBJECT_DETAILS
                        },
                        onDelete = { topId ->
                            StudyRepository.deleteTopic(context, topId)
                            refreshTrigger++
                            currentScreen = StudySubScreen.SUBJECT_DETAILS
                        }
                    )

                    StudySubScreen.STUDY_SESSION -> StudySessionContent(
                        timerState = timerState,
                        nowMs = nowMs,
                        subjects = subjects,
                        topics = topics,
                        selectedSubjectId = timerSubjectId,
                        selectedTopicId = timerTopicId,
                        sessionNotes = timerSessionNotes,
                        onSelectSubject = { timerSubjectId = it },
                        onSelectTopic = { timerTopicId = it },
                        onUpdateNotes = { timerSessionNotes = it; persistTimer(timerState) },
                        onStartTimer = { persistTimer(timerState.start(nowMs)) },
                        onPauseTimer = { persistTimer(timerState.pause(nowMs)) },
                        onResumeTimer = { persistTimer(timerState.resume(nowMs)) },
                        onResetTimer = { persistTimer(timerState.reset()) },
                        onSelectPreset = { min ->
                            val isBreak = timerState.onBreak
                            persistTimer(timerState.reset().copy(targetMin = min, onBreak = isBreak))
                        },
                        onToggleBreak = {
                            val nextBreak = !timerState.onBreak
                            val target = if (nextBreak) settings.defaultBreakMinutes else settings.defaultFocusMinutes
                            persistTimer(timerState.reset().copy(onBreak = nextBreak, targetMin = target))
                        },
                        onFinishSession = {
                            val elapsedMs = timerState.elapsedMs(nowMs)
                            val actualMinutes = (elapsedMs / 60_000L).toInt().coerceAtLeast(1)
                            val sub = subjects.firstOrNull { it.id == timerSubjectId }
                            val top = topics.firstOrNull { it.id == timerTopicId }
                            val subName = sub?.name ?: "General Study"
                            val topName = top?.title.orEmpty()
                            val now = System.currentTimeMillis()
                            val start = if (timerState.startedAt > 0) timerState.startedAt else now - elapsedMs

                            val record = StudySessionRecord(
                                subjectId = timerSubjectId,
                                topicId = timerTopicId,
                                subjectName = subName,
                                topicName = topName,
                                focusMinutes = if (!timerState.onBreak) actualMinutes else 0,
                                breakMinutes = if (timerState.onBreak) actualMinutes else 0,
                                startTimeMs = start,
                                endTimeMs = now,
                                sessionType = timerState.mode.name,
                                notes = timerSessionNotes,
                                date = today,
                                isCompleted = true
                            )
                            lastCompletedSession = record
                            persistTimer(timerState.reset())
                            currentScreen = StudySubScreen.SESSION_COMPLETE
                        },
                        onBack = { currentScreen = StudySubScreen.DASHBOARD },
                        onOpenSettings = { currentScreen = StudySubScreen.SETTINGS },
                        onCustomDuration = { showCustomTimerDialog = true }
                    )

                    StudySubScreen.SESSION_COMPLETE -> SessionCompleteContent(
                        session = lastCompletedSession,
                        onSave = { savedSession ->
                            StudyRepository.saveSession(context, savedSession)
                            val sessionTitle = if (savedSession.topicName.isNotBlank()) {
                                "${savedSession.subjectName} · ${savedSession.topicName}"
                            } else {
                                savedSession.subjectName
                            }
                            onSaveSession(sessionTitle, savedSession.focusMinutes, savedSession.notes)
                            refreshTrigger++
                            currentScreen = StudySubScreen.DASHBOARD
                        },
                        onDiscard = {
                            currentScreen = StudySubScreen.DASHBOARD
                        }
                    )

                    StudySubScreen.STUDY_PLANNER -> StudyPlannerContent(
                        calendarItems = calendarItems,
                        subjects = subjects,
                        topics = topics,
                        today = today,
                        onBack = { currentScreen = StudySubScreen.DASHBOARD },
                        onSchedule = { showScheduleDialog = true },
                        onDeleteItem = { id ->
                            val allItems = CalendarStorage.load(context).filterNot { it.id == id }
                            CalendarStorage.save(context, allItems)
                            refreshTrigger++
                        },
                        onToggleCompleted = { id, done ->
                            val allItems = CalendarStorage.load(context).map {
                                if (it.id == id) it.copy(isCompleted = done) else it
                            }
                            CalendarStorage.save(context, allItems)
                            refreshTrigger++
                        }
                    )

                    StudySubScreen.ANALYTICS -> StudyAnalyticsContent(
                        logs = logs,
                        sessions = sessions,
                        subjects = subjects,
                        onBack = { currentScreen = StudySubScreen.DASHBOARD }
                    )

                    StudySubScreen.SESSION_HISTORY -> SessionHistoryContent(
                        sessions = sessions,
                        logs = logs,
                        subjects = subjects,
                        onBack = { currentScreen = StudySubScreen.DASHBOARD },
                        onDeleteSession = { sId ->
                            StudyRepository.deleteSession(context, sId)
                            refreshTrigger++
                        }
                    )

                    StudySubScreen.FLASHCARDS -> FlashcardsQuizContent(
                        flashcards = flashcards,
                        quizzes = quizzes,
                        quizAttempts = quizAttempts,
                        subjects = subjects,
                        topics = topics,
                        onBack = { currentScreen = StudySubScreen.DASHBOARD },
                        onAddFlashcard = { showAddFlashcardDialog = true },
                        onAddQuiz = { showAddQuizDialog = true },
                        onRateFlashcard = { cardId, rating ->
                            val updated = flashcards.map {
                                if (it.id == cardId) it.copy(
                                    qualityRating = rating,
                                    lastReviewedAt = System.currentTimeMillis()
                                ) else it
                            }
                            StudyRepository.saveFlashcards(context, updated)
                            refreshTrigger++
                        },
                        onSaveQuizAttempt = { attempt ->
                            StudyRepository.saveQuizAttempt(context, attempt)
                            refreshTrigger++
                        },
                        onDeleteFlashcard = { cardId ->
                            StudyRepository.deleteFlashcard(context, cardId)
                            refreshTrigger++
                        },
                        onDeleteQuiz = { qId ->
                            StudyRepository.deleteQuiz(context, qId)
                            refreshTrigger++
                        }
                    )

                    StudySubScreen.NOTES_RESOURCES -> NotesResourcesContent(
                        resources = resources,
                        subjects = subjects,
                        topics = topics,
                        onBack = { currentScreen = StudySubScreen.DASHBOARD },
                        onAddResource = { showAddResourceDialog = true },
                        onDeleteResource = { resId ->
                            StudyRepository.deleteResource(context, resId)
                            refreshTrigger++
                        }
                    )

                    StudySubScreen.GOALS_PROGRESS -> GoalsProgressContent(
                        logs = logs,
                        subjects = subjects,
                        today = today,
                        onBack = { currentScreen = StudySubScreen.DASHBOARD },
                        onEditDailyGoal = { showGoalEditDialog = true }
                    )

                    StudySubScreen.ACHIEVEMENTS -> AchievementsContent(
                        logs = logs,
                        sessions = sessions,
                        subjects = subjects,
                        topics = topics,
                        flashcards = flashcards,
                        quizAttempts = quizAttempts,
                        onBack = { currentScreen = StudySubScreen.DASHBOARD }
                    )

                    StudySubScreen.SETTINGS -> StudySettingsContent(
                        settings = settings,
                        onBack = { currentScreen = StudySubScreen.DASHBOARD },
                        onSaveSettings = { newSettings ->
                            StudyRepository.saveSettings(context, newSettings)
                            refreshTrigger++
                        },
                        onExportData = {
                            StudyRepository.exportDataJson(context)
                        },
                        onImportData = { json ->
                            val ok = StudyRepository.importDataJson(context, json)
                            if (ok) refreshTrigger++
                            ok
                        },
                        onClearAllData = {
                            StudyRepository.clearAllData(context)
                            refreshTrigger++
                        }
                    )
                }
            }

            // Bottom Navigation Bar matching Visual Concept
            StudyBottomNavigationBar(
                activeScreen = currentScreen,
                onNavigateHome = onNavigateHome,
                onNavigateStudy = { currentScreen = StudySubScreen.DASHBOARD },
                onQuickAdd = {
                    editingSubject = null
                    currentScreen = StudySubScreen.ADD_EDIT_SUBJECT
                },
                onNavigateInsights = { onNavigate("Insights") },
                onNavigateMore = { onNavigate("More") }
            )
        }
    }

    // ==========================================
    // MODALS & DIALOGS
    // ==========================================

    if (showScheduleDialog) {
        ScheduleStudyDialog(
            subjects = subjects,
            topics = topics,
            today = today,
            onDismiss = { showScheduleDialog = false },
            onConfirm = { subName, topName, date, start, end ->
                val title = if (topName.isNotBlank()) "$subName · $topName" else subName
                val newItem = CalendarItem(
                    title = title,
                    type = ActivityType.STUDY,
                    date = date,
                    startTime = start,
                    endTime = end,
                    isCompleted = false
                )
                val all = CalendarStorage.load(context) + newItem
                CalendarStorage.save(context, all)
                refreshTrigger++
                showScheduleDialog = false
            }
        )
    }

    if (showGoalEditDialog) {
        EditDailyGoalDialog(
            currentMinutes = Goals.studyTarget(context),
            onDismiss = { showGoalEditDialog = false },
            onConfirm = { newMin ->
                Goals.save(context, Goals.waterTarget(context), newMin)
                refreshTrigger++
                showGoalEditDialog = false
            }
        )
    }

    if (showAddFlashcardDialog) {
        AddFlashcardDialog(
            subjects = subjects,
            topics = topics,
            onDismiss = { showAddFlashcardDialog = false },
            onConfirm = { card ->
                StudyRepository.saveFlashcards(context, flashcards + card)
                refreshTrigger++
                showAddFlashcardDialog = false
            }
        )
    }

    if (showAddQuizDialog) {
        AddQuizQuestionDialog(
            subjects = subjects,
            topics = topics,
            onDismiss = { showAddQuizDialog = false },
            onConfirm = { question ->
                StudyRepository.saveQuizzes(context, quizzes + question)
                refreshTrigger++
                showAddQuizDialog = false
            }
        )
    }

    if (showAddResourceDialog) {
        AddResourceDialog(
            subjects = subjects,
            topics = topics,
            today = today,
            onDismiss = { showAddResourceDialog = false },
            onConfirm = { res ->
                StudyRepository.saveResources(context, resources + res)
                refreshTrigger++
                showAddResourceDialog = false
            }
        )
    }

    if (showCustomTimerDialog) {
        CustomTimerDialog(
            onDismiss = { showCustomTimerDialog = false },
            onConfirm = { min ->
                persistTimer(timerState.reset().copy(targetMin = min))
                showCustomTimerDialog = false
            }
        )
    }
}

// ==========================================
// 3. BOTTOM NAVIGATION BAR
// ==========================================
@Composable
private fun StudyBottomNavigationBar(
    activeScreen: StudySubScreen,
    onNavigateHome: () -> Unit,
    onNavigateStudy: () -> Unit,
    onQuickAdd: () -> Unit,
    onNavigateInsights: () -> Unit,
    onNavigateMore: () -> Unit
) {
    Surface(
        color = Color(0xFF080C16),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                selected = false,
                onClick = onNavigateHome
            )

            // Study (Active)
            BottomNavItem(
                icon = Icons.Default.MenuBook,
                label = "Study",
                selected = true,
                onClick = onNavigateStudy
            )

            // Center FAB (+)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFA855F7))
                        )
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable(onClick = onQuickAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Quick Add Subject",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Insights
            BottomNavItem(
                icon = Icons.Default.BarChart,
                label = "Insights",
                selected = false,
                onClick = onNavigateInsights
            )

            // More
            BottomNavItem(
                icon = Icons.Default.GridView,
                label = "More",
                selected = false,
                onClick = onNavigateMore
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (selected) Color(0xFF2E1A47) else Color.Transparent,
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) PurpleLight else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            label,
            color = if (selected) PurpleLight else TextMuted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ==========================================
// 4. SCREEN 1: STUDY DASHBOARD CONTENT
// ==========================================
@Composable
private fun StudyDashboardContent(
    logs: List<LogEntry>,
    today: String,
    subjects: List<Subject>,
    topics: List<Topic>,
    calendarItems: List<CalendarItem>,
    selectedPeriod: StudyPeriod,
    onSelectPeriod: (StudyPeriod) -> Unit,
    customStartDate: String,
    customEndDate: String,
    onCustomStartDateChange: (String) -> Unit,
    onCustomEndDateChange: (String) -> Unit,
    onEditDailyGoal: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubjects: () -> Unit,
    onAddSubject: () -> Unit,
    onStartTimer: () -> Unit,
    onSelectSubject: (String) -> Unit,
    onViewAllSessions: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenGoals: () -> Unit
) {
    val context = LocalContext.current
    val todayStudyMinutes = logs.filter { it.category == "Study" && it.date == today }
        .sumOf { it.amount.trim().toIntOrNull() ?: 0 }
    val studyGoalMin = Goals.studyTarget(context)
    val goalFraction = (todayStudyMinutes.toFloat() / studyGoalMin.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val goalPercent = (goalFraction * 100).toInt()

    val streak = remember(logs) { StudyRepository.calculateStreak(logs) }
    val todaySessionsCount = logs.count { it.category == "Study" && it.date == today && (it.amount.trim().toIntOrNull() ?: 0) > 0 }
    val weeklyHours = remember(logs, selectedPeriod, customStartDate, customEndDate) {
        val studyByDate = logs.asSequence()
            .filter { it.category.equals("Study", ignoreCase = true) }
            .groupBy { it.date }
            .mapValues { (_, entriesForDate) -> entriesForDate.sumOf { it.amount.trim().toIntOrNull() ?: 0 } / 60f }
        fun hoursFor(date: LocalDate): Float = studyByDate[date.toString()] ?: 0f
        val currentDate = runCatching { LocalDate.parse(today) }.getOrDefault(LocalDate.now())
        when (selectedPeriod) {
            StudyPeriod.TODAY -> listOf("Today" to hoursFor(currentDate))
            StudyPeriod.WEEK -> {
                val monday = currentDate.minusDays((currentDate.dayOfWeek.value - 1).toLong())
                (0..6).map { offset ->
                    val date = monday.plusDays(offset.toLong())
                    date.format(DateTimeFormatter.ofPattern("EEE", Locale.US)) to hoursFor(date)
                }
            }
            StudyPeriod.MONTH -> {
                val first = currentDate.withDayOfMonth(1)
                val firstMonday = first.minusDays((first.dayOfWeek.value - 1).toLong())
                val last = currentDate
                val weekCount = ((java.time.temporal.ChronoUnit.DAYS.between(firstMonday, first.withDayOfMonth(first.lengthOfMonth())) / 7) + 1).toInt()
                (0 until weekCount).map { week ->
                    val from = firstMonday.plusDays(week * 7L).coerceAtLeast(first)
                    val to = firstMonday.plusDays(week * 7L + 6L).coerceAtMost(last)
                    val total = if (to.isBefore(from)) 0f else
                        (0L..java.time.temporal.ChronoUnit.DAYS.between(from, to)).sumOf { offset ->
                            (hoursFor(from.plusDays(offset)) * 60f).toInt().toLong()
                        } / 60f
                    "W${week + 1}" to total
                }
            }
            StudyPeriod.CUSTOM -> {
                val start = runCatching { LocalDate.parse(customStartDate) }.getOrDefault(currentDate.minusDays(6))
                val end = runCatching { LocalDate.parse(customEndDate) }.getOrDefault(currentDate).coerceAtLeast(start)
                val dayCount = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt().coerceIn(0, 365)
                when {
                    dayCount <= 30 -> (0..dayCount).map { offset ->
                        val date = start.plusDays(offset.toLong())
                        date.format(DateTimeFormatter.ofPattern("M/d", Locale.US)) to hoursFor(date)
                    }
                    dayCount <= 90 -> (0..(dayCount / 7)).map { bucket ->
                        val bucketStart = start.plusDays(bucket * 7L)
                        val bucketEnd = bucketStart.plusDays(6).coerceAtMost(end)
                        val minutes = (0L..java.time.temporal.ChronoUnit.DAYS.between(bucketStart, bucketEnd))
                            .sumOf { offset -> (hoursFor(bucketStart.plusDays(offset)) * 60f).toInt().toLong() }
                        "${bucketStart.format(DateTimeFormatter.ofPattern("M/d", Locale.US))}" to minutes / 60f
                    }
                    else -> {
                        val months = mutableListOf<Pair<String, Float>>()
                        var monthStart = start.withDayOfMonth(1)
                        val finalMonth = end.withDayOfMonth(1)
                        while (!monthStart.isAfter(finalMonth)) {
                            val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
                            val from = if (monthStart.isBefore(start)) start else monthStart
                            val to = if (monthEnd.isAfter(end)) end else monthEnd
                            val minutes = (0L..java.time.temporal.ChronoUnit.DAYS.between(from, to))
                                .sumOf { offset -> (hoursFor(from.plusDays(offset)) * 60f).toInt().toLong() }
                            months.add(monthStart.format(DateTimeFormatter.ofPattern("MMM", Locale.US)) to minutes / 60f)
                            monthStart = monthStart.plusMonths(1)
                        }
                        months
                    }
                }
            }
        }
    }

    val upcomingItems = calendarItems.filter { it.date >= today && !it.isCompleted }.take(3)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Life",
                            color = TextWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Track",
                            color = PurpleLight,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        "Study · Learn · Plan · Focus · Grow",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onOpenCalendar,
                        modifier = Modifier
                            .size(38.dp)
                            .background(CardInner, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar", tint = TextWhite, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(38.dp)
                            .background(CardInner, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextWhite, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // 2. Period Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StudyPeriod.values().forEach { period ->
                    val isSel = period == selectedPeriod
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) PurplePrimary else Color.Transparent)
                            .clickable { onSelectPeriod(period) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            period.label,
                            color = if (isSel) Color.White else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (selectedPeriod == StudyPeriod.CUSTOM) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val startDate = runCatching { LocalDate.parse(customStartDate) }.getOrDefault(LocalDate.now().minusDays(7))
                        val endDate = runCatching { LocalDate.parse(customEndDate) }.getOrDefault(LocalDate.now())
                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(context, { _, year, month, day ->
                                    onCustomStartDateChange(LocalDate.of(year, month + 1, day).toString())
                                }, startDate.year, startDate.monthValue - 1, startDate.dayOfMonth).apply {
                                    datePicker.maxDate = System.currentTimeMillis()
                                }.show()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) { Text("From  ${startDate.format(DateTimeFormatter.ofPattern("dd MMM", Locale.US))}", maxLines = 1) }
                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(context, { _, year, month, day ->
                                    onCustomEndDateChange(LocalDate.of(year, month + 1, day).toString())
                                }, endDate.year, endDate.monthValue - 1, endDate.dayOfMonth).apply {
                                    datePicker.maxDate = System.currentTimeMillis()
                                }.show()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) { Text("To  ${endDate.format(DateTimeFormatter.ofPattern("dd MMM", Locale.US))}", maxLines = 1) }
                    }
                }
            }
        }

        // 3. Today's Study Big Card with Circular Ring
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Today's Study", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                TextButton(onClick = onEditDailyGoal, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                    Text("Edit goal", color = PurpleLight, fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "${todayStudyMinutes / 60}h ${todayStudyMinutes % 60}m",
                                    color = TextWhite,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "of ${studyGoalMin / 60}h goal",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        // Circular Progress Ring
                        Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 7.dp.toPx()
                                drawCircle(
                                    color = Color(0xFF1E293B),
                                    style = Stroke(strokeWidth)
                                )
                                drawArc(
                                    brush = Brush.sweepGradient(listOf(PurplePrimary, CyanBrand, PurpleLight)),
                                    startAngle = -90f,
                                    sweepAngle = goalFraction * 360f,
                                    useCenter = false,
                                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$goalPercent%",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 3 Metric Pills: Streak, Sessions, Focus Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricPill(
                            icon = Icons.Default.LocalFireDepartment,
                            iconColor = AmberWarning,
                            label = "Streak",
                            value = if (streak > 0) "$streak days" else "0 days",
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            icon = Icons.Default.CheckCircle,
                            iconColor = CyanBrand,
                            label = "Sessions",
                            value = "$todaySessionsCount today",
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            icon = Icons.Default.Timer,
                            iconColor = PurpleLight,
                            label = "Focus Time",
                            value = "${todayStudyMinutes / 60}h ${todayStudyMinutes % 60}m",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 4. Quick Actions Row: Start Session, Add Subject
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartTimer,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start Session", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onAddSubject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = PurpleLight)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Subject")
                }
            }
        }

        // 5. Weekly Study Hours Bar Chart
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (selectedPeriod) {
                                StudyPeriod.TODAY -> "Today's Study Hours"
                                StudyPeriod.WEEK -> "Weekly Study Hours"
                                StudyPeriod.MONTH -> "Monthly Study Hours"
                                StudyPeriod.CUSTOM -> "Custom Study Hours"
                            },
                            color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                        Text("Goal: ${studyGoalMin / 60}h/day", color = TextMuted, fontSize = 11.sp)
                    }

                    // Bar Chart
                    val maxHours = maxOf(weeklyHours.maxOfOrNull { it.second } ?: 0f, (studyGoalMin / 60f) * 1.2f, 1f)
                    val barWidth = when {
                        weeklyHours.size > 14 -> 7.dp
                        weeklyHours.size > 8 -> 11.dp
                        else -> 18.dp
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weeklyHours.forEach { (day, hours) ->
                            val heightFraction = (hours / maxHours).coerceIn(0.04f, 1f)
                            val isAboveZero = hours > 0.05f

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isAboveZero) {
                                    Text(
                                        String.format(Locale.US, "%.1fh", hours),
                                        color = TextMuted,
                                        fontSize = 9.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .width(barWidth)
                                        .fillMaxHeight(heightFraction)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(
                                            if (isAboveZero) {
                                                Brush.verticalGradient(listOf(PurplePrimary, VioletBrand))
                                            } else {
                                                Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF141F33)))
                                            }
                                        )
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(day, color = TextMuted, fontSize = if (weeklyHours.size > 14) 7.sp else 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // 6. Upcoming Scheduled Sessions (from CalendarStorage)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Upcoming Sessions", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "See All >",
                            color = PurpleLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onOpenCalendar)
                        )
                    }

                    if (upcomingItems.isEmpty()) {
                        Text(
                            "No upcoming study sessions scheduled. Tap to plan a session in the Study Planner.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        upcomingItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardInner, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFF2E1A47), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = PurpleLight, modifier = Modifier.size(20.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${item.date} · ${item.startTime} - ${item.endTime}", color = TextMuted, fontSize = 11.sp)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // 7. User-Created Subjects Section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Your Subjects", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Manage >",
                            color = PurpleLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onOpenSubjects)
                        )
                    }

                    if (subjects.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No subjects created yet.", color = TextMuted, fontSize = 13.sp)
                            Button(
                                onClick = onAddSubject,
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ Add First Subject", fontSize = 12.sp)
                            }
                        }
                    } else {
                        subjects.take(4).forEach { sub ->
                            val subTopics = topics.filter { it.subjectId == sub.id }
                            val subMinutes = logs.filter { it.category == "Study" && (it.title.startsWith(sub.name) || it.title == sub.name) }
                                .sumOf { it.amount.trim().toIntOrNull() ?: 0 }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardInner, RoundedCornerShape(14.dp))
                                    .clickable { onSelectSubject(sub.id) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color(sub.color).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        getSubjectIcon(sub.icon),
                                        contentDescription = null,
                                        tint = Color(sub.color),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sub.name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${subMinutes / 60}h ${subMinutes % 60}m studied · ${subTopics.size} topics",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(CardInner, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = TextMuted, fontSize = 10.sp)
        }
    }
}

// ==========================================
// 5. SCREEN 2: SUBJECTS LIST CONTENT
// ==========================================
@Composable
private fun SubjectsListContent(
    subjects: List<Subject>,
    topics: List<Topic>,
    logs: List<LogEntry>,
    onBack: () -> Unit,
    onAddSubject: () -> Unit,
    onSelectSubject: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSubjects = remember(subjects, searchQuery) {
        if (searchQuery.isBlank()) subjects else subjects.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text("Subjects", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onAddSubject, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add Subject", tint = PurpleLight)
                }
            }
        },
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = onAddSubject,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("+ Add Subject", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search subjects...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedBorderColor = PurplePrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (filteredSubjects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Text("No subjects found", color = TextWhite, fontWeight = FontWeight.SemiBold)
                            Text("Tap '+ Add Subject' below to create one.", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(filteredSubjects) { sub ->
                    val subTopics = topics.filter { it.subjectId == sub.id }
                    val subMinutes = logs.filter { it.category == "Study" && (it.title.startsWith(sub.name) || it.title == sub.name) }
                        .sumOf { it.amount.trim().toIntOrNull() ?: 0 }
                    val goalMinutes = (sub.weeklyGoalHours * 60).toInt()
                    val progressFraction = if (goalMinutes > 0) (subMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f) else 0f
                    val progressPercent = (progressFraction * 100).toInt()

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSubject(sub.id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(Color(sub.color).copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        getSubjectIcon(sub.icon),
                                        contentDescription = null,
                                        tint = Color(sub.color),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sub.name, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${subMinutes / 60}h ${subMinutes % 60}m studied · ${subTopics.size} topics",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                if (goalMinutes > 0) {
                                    Text("$progressPercent%", color = PurpleLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (goalMinutes > 0) {
                                LinearProgressIndicator(
                                    progress = progressFraction,
                                    color = Color(sub.color),
                                    trackColor = Color(0xFF1E293B),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. SCREEN 3: ADD / EDIT SUBJECT CONTENT
// ==========================================
@Composable
private fun AddEditSubjectContent(
    subject: Subject?,
    onBack: () -> Unit,
    onSave: (Subject) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(subject?.name.orEmpty()) }
    var description by remember { mutableStateOf(subject?.description.orEmpty()) }
    var icon by remember { mutableStateOf(subject?.icon ?: "book") }
    var color by remember { mutableLongStateOf(subject?.color ?: 0xFFA855F7L) }
    var weeklyGoalHours by remember { mutableStateOf(if ((subject?.weeklyGoalHours ?: 0f) > 0f) subject!!.weeklyGoalHours.toString() else "") }
    var notes by remember { mutableStateOf(subject?.notes.orEmpty()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val iconOptions = listOf("book", "code", "chip", "cube", "flask", "palette", "globe", "math")
    val colorOptions = listOf(
        0xFFA855F7L, // Purple
        0xFF38BDF8L, // Blue
        0xFF06B6D4L, // Cyan
        0xFF10B981L, // Emerald
        0xFFF59E0BL, // Amber
        0xFFF97316L, // Orange
        0xFFF43F5EL, // Coral
        0xFFEC4899L  // Pink
    )

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text(if (subject == null) "Add Subject" else "Edit Subject", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val goal = weeklyGoalHours.toFloatOrNull() ?: 0f
                            val newSub = subject?.copy(
                                name = name.trim(),
                                description = description.trim(),
                                icon = icon,
                                color = color,
                                weeklyGoalHours = goal,
                                notes = notes.trim()
                            ) ?: Subject(
                                name = name.trim(),
                                description = description.trim(),
                                icon = icon,
                                color = color,
                                weeklyGoalHours = goal,
                                notes = notes.trim()
                            )
                            onSave(newSub)
                        }
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Preview Box
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(Color(color).copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                            .border(1.5.dp, Color(color), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(getSubjectIcon(icon), contentDescription = null, tint = Color(color), modifier = Modifier.size(40.dp))
                    }
                }
            }

            // Subject Name
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Subject Name *", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("e.g. ROS 2, Python, Robotics", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                            focusedBorderColor = PurplePrimary, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Description
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Description", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Short description of what you study...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                            focusedBorderColor = PurplePrimary, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Icon Selector Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Icon", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        iconOptions.forEach { icKey ->
                            val isSel = icKey == icon
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Color(color).copy(alpha = 0.3f) else CardBg)
                                    .border(1.dp, if (isSel) Color(color) else CardBorder, RoundedCornerShape(10.dp))
                                    .clickable { icon = icKey },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(getSubjectIcon(icKey), contentDescription = null, tint = if (isSel) Color(color) else TextMuted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // Color Picker Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Color Accent", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        colorOptions.forEach { cVal ->
                            val isSel = cVal == color
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(cVal))
                                    .border(if (isSel) 2.5.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { color = cVal },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Weekly Goal (Optional)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Weekly Goal (Optional)", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = weeklyGoalHours,
                        onValueChange = { weeklyGoalHours = it },
                        placeholder = { Text("e.g. 10 (hours/week)", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                            focusedBorderColor = PurplePrimary, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Notes
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Notes", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Additional notes, syllabus links, etc.", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                            focusedBorderColor = PurplePrimary, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Delete Subject if editing
            if (subject != null) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralAccent.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = CoralAccent)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Subject", color = CoralAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && subject != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Subject?", color = TextWhite) },
            text = { Text("This will remove '${subject.name}' and its associated topics. Historical study log sessions will preserve their title records.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(subject.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralAccent)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBg
        )
    }
}

// ==========================================
// 7. SCREEN 4: SUBJECT DETAILS & TOPICS
// ==========================================
@Composable
private fun SubjectDetailsContent(
    subjectId: String,
    subjects: List<Subject>,
    topics: List<Topic>,
    logs: List<LogEntry>,
    resources: List<StudyResource>,
    onBack: () -> Unit,
    onEditSubject: (Subject) -> Unit,
    onDeleteSubject: (String) -> Unit,
    onAddTopic: () -> Unit,
    onSelectTopic: (Topic) -> Unit,
    onStartTopicTimer: (String, String) -> Unit,
    onAddResource: () -> Unit
) {
    val sub = subjects.firstOrNull { it.id == subjectId }
    if (sub == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Subject not found", color = TextMuted)
        }
        return
    }

    var selectedTab by remember { mutableStateOf("Topics") }
    val subTopics = topics.filter { it.subjectId == sub.id }
    val subMinutes = logs.filter { it.category == "Study" && (it.title.startsWith(sub.name) || it.title == sub.name) }
        .sumOf { it.amount.trim().toIntOrNull() ?: 0 }
    val goalMinutes = (sub.weeklyGoalHours * 60).toInt()
    val progressFraction = if (goalMinutes > 0) (subMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f) else 0f
    val progressPercent = (progressFraction * 100).toInt()

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text("Subject Details", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { onEditSubject(sub) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Subject", tint = PurpleLight)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Subject Hero Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color(sub.color).copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(getSubjectIcon(sub.icon), contentDescription = null, tint = Color(sub.color), modifier = Modifier.size(28.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sub.name, color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("${subMinutes / 60}h ${subMinutes % 60}m studied · ${subTopics.size} topics", color = TextMuted, fontSize = 12.sp)
                            }
                            if (goalMinutes > 0) {
                                Text("$progressPercent%", color = PurpleLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (goalMinutes > 0) {
                            LinearProgressIndicator(
                                progress = progressFraction,
                                color = Color(sub.color),
                                trackColor = Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }

            // Tabs Row: Topics, Notes, Resources, Analytics
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBg, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("Topics", "Notes", "Resources", "Analytics").forEach { tab ->
                        val isSel = tab == selectedTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) PurplePrimary else Color.Transparent)
                                .clickable { selectedTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tab, color = if (isSel) Color.White else TextMuted, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }

            // Tab Content
            when (selectedTab) {
                "Topics" -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Syllabus & Topics", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = onAddTopic,
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ Add Topic", fontSize = 12.sp)
                            }
                        }
                    }

                    if (subTopics.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardBg),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("No topics defined for this subject yet.", color = TextMuted, fontSize = 13.sp)
                                    Text("Add topics to structure your syllabus.", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        items(subTopics) { top ->
                            val statusColor = when (top.status) {
                                TopicStatus.COMPLETED -> EmeraldAccent
                                TopicStatus.IN_PROGRESS -> BlueAccent
                                TopicStatus.NEEDS_REVISION -> AmberWarning
                                TopicStatus.NOT_STARTED -> TextMuted
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardBg),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTopic(top) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(statusColor.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (top.status == TopicStatus.COMPLETED) Icons.Default.Check else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(top.title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        if (top.targetDurationMinutes > 0) {
                                            Text("Target: ${top.targetDurationMinutes}m", color = TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(top.status.label, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                "Notes", "Resources" -> {
                    val subResources = resources.filter { it.subjectId == sub.id }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Learning Material", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = onAddResource,
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ Add Resource", fontSize = 12.sp)
                            }
                        }
                    }

                    if (subResources.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardBg),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("No notes or resources attached.", color = TextMuted, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        items(subResources) { res ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardBg),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(res.title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(res.contentOrUrl, color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                "Analytics" -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Total Studied: ${subMinutes / 60}h ${subMinutes % 60}m", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Weekly Goal: ${sub.weeklyGoalHours} hours", color = TextMuted, fontSize = 13.sp)
                                Text("Topics Completed: ${subTopics.count { it.status == TopicStatus.COMPLETED }} / ${subTopics.size}", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. SCREEN 5: ADD / EDIT TOPIC
// ==========================================
@Composable
private fun AddEditTopicContent(
    topic: Topic?,
    subjectId: String,
    subjects: List<Subject>,
    onBack: () -> Unit,
    onSave: (Topic) -> Unit,
    onDelete: (String) -> Unit
) {
    val sub = subjects.firstOrNull { it.id == subjectId }
    var title by remember { mutableStateOf(topic?.title.orEmpty()) }
    var description by remember { mutableStateOf(topic?.description.orEmpty()) }
    var targetDuration by remember { mutableStateOf(if ((topic?.targetDurationMinutes ?: 0) > 0) topic!!.targetDurationMinutes.toString() else "") }
    var status by remember { mutableStateOf(topic?.status ?: TopicStatus.NOT_STARTED) }
    var notes by remember { mutableStateOf(topic?.notes.orEmpty()) }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text(if (topic == null) "Add Topic" else "Edit Topic", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val dur = targetDuration.toIntOrNull() ?: 0
                            val newTop = topic?.copy(
                                title = title.trim(),
                                description = description.trim(),
                                targetDurationMinutes = dur,
                                status = status,
                                notes = notes.trim()
                            ) ?: Topic(
                                subjectId = subjectId,
                                title = title.trim(),
                                description = description.trim(),
                                targetDurationMinutes = dur,
                                status = status,
                                notes = notes.trim()
                            )
                            onSave(newTop)
                        }
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Subject Badge
            if (sub != null) {
                item {
                    Box(
                        modifier = Modifier
                            .background(CardBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Subject: ${sub.name}", color = PurpleLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Topic Title
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Topic Title *", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("e.g. Navigation 2, OOP, Calculus", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                            focusedBorderColor = PurplePrimary, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Description
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Description", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Topic syllabus description...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                            focusedBorderColor = PurplePrimary, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Status Selector Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TopicStatus.values().forEach { st ->
                            val isSel = st == status
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) PurplePrimary else CardBg)
                                    .border(1.dp, if (isSel) PurpleLight else CardBorder, RoundedCornerShape(10.dp))
                                    .clickable { status = st }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    st.label,
                                    color = if (isSel) Color.White else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Target Duration (Minutes)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Target Duration (Minutes)", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = targetDuration,
                        onValueChange = { targetDuration = it },
                        placeholder = { Text("e.g. 60", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                            focusedBorderColor = PurplePrimary, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Notes
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Notes", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Personal notes, links, reminders...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                            focusedBorderColor = PurplePrimary, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Delete Topic if editing
            if (topic != null) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onDelete(topic.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralAccent.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = CoralAccent)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Topic", color = CoralAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. SCREEN 6 & 7: STUDY SESSION (RUNNING)
// ==========================================
@Composable
private fun StudySessionContent(
    timerState: TimerState,
    nowMs: Long,
    subjects: List<Subject>,
    topics: List<Topic>,
    selectedSubjectId: String?,
    selectedTopicId: String?,
    sessionNotes: String,
    onSelectSubject: (String) -> Unit,
    onSelectTopic: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onToggleBreak: () -> Unit,
    onFinishSession: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onCustomDuration: () -> Unit
) {
    val activeSub = subjects.firstOrNull { it.id == selectedSubjectId }
    val activeTopics = topics.filter { it.subjectId == selectedSubjectId }
    val activeTop = activeTopics.firstOrNull { it.id == selectedTopicId }

    val elapsedMs = timerState.elapsedMs(nowMs)
    val remainingMs = timerState.remainingMs(nowMs)
    val hasTarget = timerState.mode != TimerMode.STOPWATCH || timerState.onBreak
    val fraction = if (hasTarget && timerState.targetMin > 0) {
        (remainingMs.toFloat() / (timerState.targetMin * 60_000L).toFloat()).coerceIn(0f, 1f)
    } else 0f

    val clockDisplay = if (hasTarget) TimerState.clock(remainingMs) else TimerState.clock(elapsedMs)

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Study Session", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextWhite)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Focus / Break Mode Toggle Pill
            item {
                Row(
                    modifier = Modifier
                        .background(CardBg, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!timerState.onBreak) PurplePrimary else Color.Transparent)
                            .clickable { if (timerState.onBreak) onToggleBreak() }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Focus", color = if (!timerState.onBreak) Color.White else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (timerState.onBreak) CyanBrand else Color.Transparent)
                            .clickable { if (!timerState.onBreak) onToggleBreak() }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Break", color = if (timerState.onBreak) Color.White else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Big Circular Timer Ring
            item {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 12.dp.toPx()
                        drawCircle(
                            color = Color(0xFF141F33),
                            style = Stroke(strokeWidth)
                        )
                        drawArc(
                            brush = Brush.sweepGradient(
                                if (!timerState.onBreak) listOf(PurplePrimary, VioletBrand, PurpleLight)
                                else listOf(CyanBrand, BlueAccent, EmeraldAccent)
                            ),
                            startAngle = -90f,
                            sweepAngle = if (hasTarget) fraction * 360f else 360f,
                            useCenter = false,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (!timerState.onBreak) Icons.Default.Spa else Icons.Default.Coffee,
                            contentDescription = null,
                            tint = if (!timerState.onBreak) PurpleLight else CyanBrand,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            clockDisplay,
                            color = TextWhite,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            if (timerState.running) (if (hasTarget) "Remaining" else "Elapsed") else if (timerState.active) "Paused" else "Ready",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Play / Pause / Finish / Reset Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    IconButton(
                        onClick = onResetTimer,
                        modifier = Modifier
                            .size(46.dp)
                            .background(CardBg, CircleShape)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = TextMuted)
                    }

                    Spacer(Modifier.width(20.dp))

                    // Primary Play / Pause Button
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(PurplePrimary, VioletBrand)
                                )
                            )
                            .clickable {
                                when {
                                    !timerState.active -> onStartTimer()
                                    timerState.running -> onPauseTimer()
                                    else -> onResumeTimer()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (timerState.running) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Start/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(Modifier.width(20.dp))

                    // Finish Session Button
                    IconButton(
                        onClick = onFinishSession,
                        enabled = timerState.active || elapsedMs > 10_000L,
                        modifier = Modifier
                            .size(46.dp)
                            .background(if (timerState.active || elapsedMs > 10_000L) EmeraldAccent.copy(alpha = 0.2f) else CardBg, CircleShape)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Finish", tint = if (timerState.active || elapsedMs > 10_000L) EmeraldAccent else TextMuted)
                    }
                }
            }

            // Preset Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 25, 45, 60).forEach { min ->
                        val isSel = timerState.targetMin == min
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) PurplePrimary else CardBg)
                                .border(1.dp, if (isSel) PurpleLight else CardBorder, RoundedCornerShape(12.dp))
                                .clickable { onSelectPreset(min) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${min}m", color = if (isSel) Color.White else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardBg)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .clickable(onClick = onCustomDuration)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Custom", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Subject & Topic Selector Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = PurpleLight, modifier = Modifier.size(18.dp))
                                Text("Linked Subject & Topic", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        // Subject Dropdown / Row
                        if (subjects.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(subjects) { sub ->
                                    val isSel = sub.id == selectedSubjectId
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSel) Color(sub.color).copy(alpha = 0.3f) else CardInner)
                                            .border(1.dp, if (isSel) Color(sub.color) else CardBorder, RoundedCornerShape(10.dp))
                                            .clickable { onSelectSubject(sub.id) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(sub.name, color = if (isSel) TextWhite else TextMuted, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }

                        // Topic Dropdown / Row
                        if (activeTopics.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(activeTopics) { top ->
                                    val isSel = top.id == selectedTopicId
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSel) PurplePrimary else CardInner)
                                            .border(1.dp, if (isSel) PurpleLight else CardBorder, RoundedCornerShape(10.dp))
                                            .clickable { onSelectTopic(top.id) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(top.title, color = if (isSel) TextWhite else TextMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Session Notes Input
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Session Notes", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        OutlinedTextField(
                            value = sessionNotes,
                            onValueChange = onUpdateNotes,
                            placeholder = { Text("What are you working on right now?...", color = TextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                                focusedBorderColor = PurplePrimary, unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                            ),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 10. SCREEN 8: SESSION COMPLETE
// ==========================================
@Composable
private fun SessionCompleteContent(
    session: StudySessionRecord?,
    onSave: (StudySessionRecord) -> Unit,
    onDiscard: () -> Unit
) {
    if (session == null) {
        onDiscard()
        return
    }

    var notes by remember { mutableStateOf(session.notes) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("hh:mm a") }
    val startStr = remember(session.startTimeMs) {
        val dt = java.time.Instant.ofEpochMilli(session.startTimeMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        dt.format(timeFmt)
    }
    val endStr = remember(session.endTimeMs) {
        val dt = java.time.Instant.ofEpochMilli(session.endTimeMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        dt.format(timeFmt)
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Session Complete", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Celebration Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF2E1A47), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎉", fontSize = 38.sp)
                }

                Text("Great Job!", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)

                // Focus Time Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Focus Time", color = TextMuted, fontSize = 12.sp)
                        Text("${session.focusMinutes} min", color = TextWhite, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Details Table
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailRow("Subject", session.subjectName)
                        if (session.topicName.isNotBlank()) {
                            DetailRow("Topic", session.topicName)
                        }
                        DetailRow("Started At", startStr)
                        DetailRow("Ended At", endStr)
                    }
                }

                // Add Notes Field
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Session Summary Notes", color = TextMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("What did you accomplish?", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                            focusedBorderColor = PurplePrimary, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Buttons: Save & Discard
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onSave(session.copy(notes = notes.trim())) },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Save Session", fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Discard", color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ==========================================
// 11. SCREEN 9: STUDY PLANNER (CALENDAR INTEGRATION)
// ==========================================
@Composable
private fun StudyPlannerContent(
    calendarItems: List<CalendarItem>,
    subjects: List<Subject>,
    topics: List<Topic>,
    today: String,
    onBack: () -> Unit,
    onSchedule: () -> Unit,
    onDeleteItem: (String) -> Unit,
    onToggleCompleted: (String, Boolean) -> Unit
) {
    var selectedDayOffset by remember { mutableIntStateOf(0) }
    val todayDate = remember { LocalDate.now() }
    val selectedDate = remember(selectedDayOffset) { todayDate.plusDays(selectedDayOffset.toLong()) }
    val selectedDateStr = selectedDate.toString()

    val dayItems = calendarItems.filter { it.date == selectedDateStr }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text("Study Planner", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onSchedule, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Schedule", tint = PurpleLight)
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onSchedule,
                containerColor = PurplePrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Plan Session")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month Header
            item {
                Text(
                    selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Horizontal Date Strip (Mon..Sun around today)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (-3..3).forEach { offset ->
                        val date = todayDate.plusDays(offset.toLong())
                        val isSel = offset == selectedDayOffset
                        val hasItems = calendarItems.any { it.date == date.toString() }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) PurplePrimary else CardBg)
                                .clickable { selectedDayOffset = offset }
                                .padding(vertical = 10.dp, horizontal = 10.dp)
                        ) {
                            Text(
                                date.format(DateTimeFormatter.ofPattern("EEE")),
                                color = if (isSel) Color.White else TextMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                date.dayOfMonth.toString(),
                                color = if (isSel) Color.White else TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (hasItems) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(if (isSel) Color.White else CyanBrand, CircleShape)
                                )
                            } else {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }

            // Scheduled Sessions for Selected Day
            item {
                Text(
                    "${selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))} (${dayItems.size} items)",
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (dayItems.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No study sessions planned for this date.", color = TextMuted, fontSize = 13.sp)
                            Button(
                                onClick = onSchedule,
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ Schedule Study Session", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                items(dayItems) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isCompleted,
                                onCheckedChange = { onToggleCompleted(item.id, it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = EmeraldAccent,
                                    uncheckedColor = TextMuted
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    color = if (item.isCompleted) TextMuted else TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${item.startTime} - ${item.endTime}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { onDeleteItem(item.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 12. SCREEN 10: STUDY ANALYTICS
// ==========================================
@Composable
private fun StudyAnalyticsContent(
    logs: List<LogEntry>,
    sessions: List<StudySessionRecord>,
    subjects: List<Subject>,
    onBack: () -> Unit
) {
    val totalMinutes = logs.filter { it.category == "Study" }.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
    val trend = remember(logs) { StudyRepository.calculateDailyTrend(logs, 7) }
    val distribution = remember(logs, sessions, subjects) { StudyRepository.calculateSubjectDistribution(logs, sessions, subjects) }
    val avgMinutesPerDay = if (trend.isNotEmpty()) totalMinutes / 7 else 0

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Study Analytics", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Metrics Row: Total Time, Daily Avg
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Total Study Time", color = TextMuted, fontSize = 11.sp)
                            Text("${totalMinutes / 60}h ${totalMinutes % 60}m", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Daily Average", color = TextMuted, fontSize = 11.sp)
                            Text("${avgMinutesPerDay / 60}h ${avgMinutesPerDay % 60}m", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Daily Study Trend Bar Chart
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Daily Study Trend", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                        val maxHours = (trend.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            trend.forEach { (day, hours) ->
                                val heightFraction = (hours / maxHours).coerceIn(0.04f, 1f)
                                val isAboveZero = hours > 0.05f

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isAboveZero) {
                                        Text(String.format(Locale.US, "%.1fh", hours), color = TextMuted, fontSize = 9.sp)
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .fillMaxHeight(heightFraction)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(
                                                if (isAboveZero) Brush.verticalGradient(listOf(PurplePrimary, CyanBrand))
                                                else Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF141F33)))
                                            )
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(day, color = TextMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Subject Distribution Donut Chart
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Subject Distribution", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                        if (distribution.isEmpty()) {
                            Text("No study records recorded yet.", color = TextMuted, fontSize = 12.sp)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Donut Chart Canvas
                                Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeWidth = 14.dp.toPx()
                                        var startAngle = -90f
                                        distribution.forEach { item ->
                                            val sweep = item.percentage * 360f
                                            drawArc(
                                                color = Color(item.color),
                                                startAngle = startAngle,
                                                sweepAngle = sweep,
                                                useCenter = false,
                                                style = Stroke(strokeWidth)
                                            )
                                            startAngle += sweep
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${totalMinutes / 60}h", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("Total", color = TextMuted, fontSize = 9.sp)
                                    }
                                }

                                // Legend
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    distribution.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(modifier = Modifier.size(8.dp).background(Color(item.color), CircleShape))
                                                Text(item.subjectName, color = TextWhite, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text("${(item.percentage * 100).toInt()}%", color = TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 13. SCREEN 11: SESSION HISTORY
// ==========================================
@Composable
private fun SessionHistoryContent(
    sessions: List<StudySessionRecord>,
    logs: List<LogEntry>,
    subjects: List<Subject>,
    onBack: () -> Unit,
    onDeleteSession: (String) -> Unit
) {
    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Session History", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        val studyLogs = logs.filter { it.category == "Study" && (it.amount.trim().toIntOrNull() ?: 0) > 0 }

        if (studyLogs.isEmpty() && sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No study sessions recorded yet.", color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(studyLogs) { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFF2E1A47), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = PurpleLight, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(entry.date, color = TextMuted, fontSize = 11.sp)
                                if (entry.note.isNotBlank()) {
                                    Text(entry.note, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .background(PurplePrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("${entry.amount}m", color = PurpleLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 14. SCREEN 12: FLASHCARDS & QUIZ
// ==========================================
@Composable
private fun FlashcardsQuizContent(
    flashcards: List<Flashcard>,
    quizzes: List<QuizQuestion>,
    quizAttempts: List<QuizAttempt>,
    subjects: List<Subject>,
    topics: List<Topic>,
    onBack: () -> Unit,
    onAddFlashcard: () -> Unit,
    onAddQuiz: () -> Unit,
    onRateFlashcard: (String, Int) -> Unit,
    onSaveQuizAttempt: (QuizAttempt) -> Unit,
    onDeleteFlashcard: (String) -> Unit,
    onDeleteQuiz: (String) -> Unit
) {
    var activeTab by remember { mutableStateOf("Cards") }
    var currentCardIndex by remember { mutableIntStateOf(0) }
    var isRevealed by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text("Active Recall", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { if (activeTab == "Cards") onAddFlashcard() else onAddQuiz() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = PurpleLight)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tab Pill: Cards, Quizzes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Cards", "Quizzes").forEach { tab ->
                    val isSel = tab == activeTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) PurplePrimary else Color.Transparent)
                            .clickable { activeTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tab, color = if (isSel) Color.White else TextMuted, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }

            if (activeTab == "Cards") {
                if (flashcards.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("No flashcards created yet.", color = TextMuted)
                            Button(
                                onClick = onAddFlashcard,
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                            ) {
                                Text("+ Add Flashcard")
                            }
                        }
                    }
                } else {
                    val safeIdx = currentCardIndex.coerceIn(0, flashcards.size - 1)
                    val card = flashcards[safeIdx]

                    // Counter Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${safeIdx + 1} / ${flashcards.size}", color = TextMuted, fontSize = 13.sp)
                        Row {
                            IconButton(
                                onClick = {
                                    if (currentCardIndex > 0) {
                                        currentCardIndex--
                                        isRevealed = false
                                    }
                                },
                                enabled = currentCardIndex > 0
                            ) {
                                Icon(Icons.Default.ArrowBackIos, contentDescription = "Prev", tint = if (currentCardIndex > 0) TextWhite else TextMuted, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (currentCardIndex < flashcards.size - 1) {
                                        currentCardIndex++
                                        isRevealed = false
                                    }
                                },
                                enabled = currentCardIndex < flashcards.size - 1
                            ) {
                                Icon(Icons.Default.ArrowForwardIos, contentDescription = "Next", tint = if (currentCardIndex < flashcards.size - 1) TextWhite else TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Flip Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.5.dp, if (isRevealed) PurplePrimary else CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clickable { isRevealed = !isRevealed }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                if (isRevealed) "ANSWER" else "QUESTION",
                                color = if (isRevealed) EmeraldAccent else PurpleLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                if (isRevealed) card.answer else card.question,
                                color = TextWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            if (isRevealed && card.explanation.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(card.explanation, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.height(18.dp))
                            Text(
                                if (isRevealed) "Tap to flip back" else "Tap to reveal answer",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // 4 SRS Quality Rating Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RatingBtn("Again", CoralAccent, Modifier.weight(1f)) {
                            onRateFlashcard(card.id, 1)
                            if (currentCardIndex < flashcards.size - 1) currentCardIndex++
                            isRevealed = false
                        }
                        RatingBtn("Hard", AmberWarning, Modifier.weight(1f)) {
                            onRateFlashcard(card.id, 2)
                            if (currentCardIndex < flashcards.size - 1) currentCardIndex++
                            isRevealed = false
                        }
                        RatingBtn("Good", BlueAccent, Modifier.weight(1f)) {
                            onRateFlashcard(card.id, 3)
                            if (currentCardIndex < flashcards.size - 1) currentCardIndex++
                            isRevealed = false
                        }
                        RatingBtn("Easy", EmeraldAccent, Modifier.weight(1f)) {
                            onRateFlashcard(card.id, 4)
                            if (currentCardIndex < flashcards.size - 1) currentCardIndex++
                            isRevealed = false
                        }
                    }
                }
            } else {
                // Quizzes List
                if (quizzes.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("No quiz questions created yet.", color = TextMuted)
                            Button(onClick = onAddQuiz, colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)) {
                                Text("+ Add Quiz Question")
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(quizzes) { q ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardBg),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(q.question, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    q.options.forEachIndexed { i, opt ->
                                        Text(
                                            "${('A' + i)}. $opt",
                                            color = if (i == q.correctOptionIndex) EmeraldAccent else TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingBtn(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ==========================================
// 15. SCREEN 13: NOTES & RESOURCES
// ==========================================
@Composable
private fun NotesResourcesContent(
    resources: List<StudyResource>,
    subjects: List<Subject>,
    topics: List<Topic>,
    onBack: () -> Unit,
    onAddResource: () -> Unit,
    onDeleteResource: (String) -> Unit
) {
    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text("Notes & Resources", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onAddResource, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = PurpleLight)
                }
            }
        }
    ) { padding ->
        if (resources.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No notes or resources saved yet.", color = TextMuted)
                    Button(onClick = onAddResource, colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)) {
                        Text("+ Add Note / Resource")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(resources) { res ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFF2E1A47), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    when (res.type) {
                                        StudyResourceType.LINK -> Icons.Default.Link
                                        StudyResourceType.BOOKMARK -> Icons.Default.Bookmark
                                        StudyResourceType.DOCUMENT -> Icons.Default.Description
                                        else -> Icons.Default.Notes
                                    },
                                    contentDescription = null,
                                    tint = PurpleLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(res.title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(res.contentOrUrl, color = TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onDeleteResource(res.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 16. SCREEN 14: GOALS & PROGRESS
// ==========================================
@Composable
private fun GoalsProgressContent(
    logs: List<LogEntry>,
    subjects: List<Subject>,
    today: String,
    onBack: () -> Unit,
    onEditDailyGoal: () -> Unit
) {
    val context = LocalContext.current
    val dailyGoalMin = Goals.studyTarget(context)
    val todayStudyMin = logs.filter { it.category == "Study" && it.date == today }.sumOf { it.amount.trim().toIntOrNull() ?: 0 }
    val dailyFraction = (todayStudyMin.toFloat() / dailyGoalMin.toFloat()).coerceIn(0f, 1f)

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Goals & Progress", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Daily Goal Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Daily Study Goal", color = TextMuted, fontSize = 12.sp)
                                Text("${dailyGoalMin / 60}h ${dailyGoalMin % 60}m", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onEditDailyGoal,
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Edit", fontSize = 12.sp)
                            }
                        }

                        LinearProgressIndicator(
                            progress = dailyFraction,
                            color = PurplePrimary,
                            trackColor = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )

                        Text(
                            "${todayStudyMin / 60}h ${todayStudyMin % 60}m / ${dailyGoalMin / 60}h ${dailyGoalMin % 60}m (${(dailyFraction * 100).toInt()}%)",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Subject-Specific Targets
            item {
                Text("Subject Goals", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            if (subjects.isEmpty()) {
                item {
                    Text("No subjects created yet. Subject goals can be configured when editing a subject.", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                items(subjects) { sub ->
                    val subMinutes = logs.filter { it.category == "Study" && (it.title.startsWith(sub.name) || it.title == sub.name) }
                        .sumOf { it.amount.trim().toIntOrNull() ?: 0 }
                    val goalMin = (sub.weeklyGoalHours * 60).toInt()
                    val fraction = if (goalMin > 0) (subMinutes.toFloat() / goalMin).coerceIn(0f, 1f) else 0f

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sub.name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(if (goalMin > 0) "${sub.weeklyGoalHours}h/week" else "No target", color = TextMuted, fontSize = 11.sp)
                            }
                            if (goalMin > 0) {
                                LinearProgressIndicator(
                                    progress = fraction,
                                    color = Color(sub.color),
                                    trackColor = Color(0xFF1E293B),
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                )
                                Text("${subMinutes / 60}h ${subMinutes % 60}m (${(fraction * 100).toInt()}%)", color = TextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 17. SCREEN 15: ACHIEVEMENTS
// ==========================================
@Composable
private fun AchievementsContent(
    logs: List<LogEntry>,
    sessions: List<StudySessionRecord>,
    subjects: List<Subject>,
    topics: List<Topic>,
    flashcards: List<Flashcard>,
    quizAttempts: List<QuizAttempt>,
    onBack: () -> Unit
) {
    val streak = remember(logs) { StudyRepository.calculateStreak(logs) }
    val achievements = remember(logs, sessions, subjects, topics, flashcards, quizAttempts) {
        StudyRepository.calculateAchievements(logs, sessions, subjects, topics, flashcards, quizAttempts)
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Achievements", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Flame Banner: Current Streak
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1A47)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, PurpleLight.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(AmberWarning.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(32.dp))
                        }
                        Column {
                            Text("Current Streak", color = TextMuted, fontSize = 12.sp)
                            Text("$streak Days", color = TextWhite, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // Milestone Cards
            items(achievements) { ach ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(if (ach.isCompleted) EmeraldAccent.copy(alpha = 0.2f) else CardInner, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (ach.isCompleted) Icons.Default.Check else Icons.Default.Star,
                                contentDescription = null,
                                tint = if (ach.isCompleted) EmeraldAccent else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(ach.title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(ach.description, color = TextMuted, fontSize = 11.sp)
                            LinearProgressIndicator(
                                progress = ach.progressFraction,
                                color = if (ach.isCompleted) EmeraldAccent else PurplePrimary,
                                trackColor = Color(0xFF1E293B),
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                            )
                        }
                        Text(ach.progressText, color = if (ach.isCompleted) EmeraldAccent else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ==========================================
// 18. SCREEN 16: STUDY SETTINGS
// ==========================================
@Composable
private fun StudySettingsContent(
    settings: StudySettings,
    onBack: () -> Unit,
    onSaveSettings: (StudySettings) -> Unit,
    onExportData: () -> String,
    onImportData: (String) -> Boolean,
    onClearAllData: () -> Unit
) {
    val context = LocalContext.current
    var studyReminder by remember { mutableStateOf(settings.studyReminderEnabled) }
    var breakReminder by remember { mutableStateOf(settings.breakReminderEnabled) }
    var goalReminder by remember { mutableStateOf(settings.dailyGoalReminderEnabled) }
    var autoBreak by remember { mutableStateOf(settings.autoStartBreak) }
    var focusDuration by remember { mutableIntStateOf(settings.defaultFocusMinutes.coerceIn(5, 180)) }
    var breakDuration by remember { mutableIntStateOf(settings.defaultBreakMinutes.coerceIn(1, 60)) }
    var reminderHour by remember { mutableIntStateOf(settings.reminderHour.coerceIn(0, 23)) }
    var reminderMinute by remember { mutableIntStateOf(settings.reminderMinute.coerceIn(0, 59)) }

    var showClearConfirm by remember { mutableStateOf(false) }
    var showExportedDialog by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                }
                Text("Study Settings", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Reminders Section
            item {
                Text("Reminders & Notifications", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingSwitchRow("Daily Study Reminder", "Notify when it's time to study", studyReminder) {
                            studyReminder = it
                            onSaveSettings(settings.copy(studyReminderEnabled = it))
                        }
                        Divider(color = CardBorder)
                        SettingSwitchRow("Break Alert", "Remind to take healthy breaks", breakReminder) {
                            breakReminder = it
                            onSaveSettings(settings.copy(breakReminderEnabled = it))
                        }
                        Divider(color = CardBorder)
                        SettingSwitchRow("Daily Goal Reminder", "Alert when close to daily study target", goalReminder) {
                            goalReminder = it
                            onSaveSettings(settings.copy(dailyGoalReminderEnabled = it))
                        }
                        Divider(color = CardBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Daily reminder time", color = TextWhite, fontSize = 13.sp)
                                Text("Choose when your daily reminder runs", color = TextMuted, fontSize = 11.sp)
                            }
                            OutlinedButton(onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        reminderHour = hour
                                        reminderMinute = minute
                                        onSaveSettings(settings.copy(reminderHour = hour, reminderMinute = minute))
                                    },
                                    reminderHour,
                                    reminderMinute,
                                    false
                                ).show()
                            }) {
                                Text(String.format(Locale.getDefault(), "%02d:%02d", reminderHour, reminderMinute), color = PurpleLight)
                            }
                        }
                    }
                }
            }

            // Timer Preferences
            item {
                Text("Timer Preferences", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Default Focus Duration", color = TextWhite, fontSize = 13.sp)
                            Text("$focusDuration min", color = PurpleLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = focusDuration.toFloat(),
                            onValueChange = { focusDuration = (it / 5f).roundToInt() * 5 },
                            valueRange = 5f..180f,
                            steps = 34,
                            onValueChangeFinished = {
                                onSaveSettings(settings.copy(defaultFocusMinutes = focusDuration, defaultBreakMinutes = breakDuration))
                            }
                        )
                        Divider(color = CardBorder)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Default Break Duration", color = TextWhite, fontSize = 13.sp)
                            Text("$breakDuration min", color = CyanBrand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = breakDuration.toFloat(),
                            onValueChange = { breakDuration = it.roundToInt() },
                            valueRange = 1f..60f,
                            steps = 58,
                            onValueChangeFinished = {
                                onSaveSettings(settings.copy(defaultFocusMinutes = focusDuration, defaultBreakMinutes = breakDuration))
                            }
                        )
                        Divider(color = CardBorder)
                        SettingSwitchRow("Auto-Start Break", "Automatically start break timer when focus completes", autoBreak) {
                            autoBreak = it
                            onSaveSettings(settings.copy(autoStartBreak = it))
                        }
                    }
                }
            }

            // Data Management Section
            item {
                Text("Data Management", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showExportedDialog = onExportData() }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Export Study Data", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Save study subjects, topics, and records as JSON", color = TextMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.Download, contentDescription = null, tint = PurpleLight)
                        }
                        Divider(color = CardBorder)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showImportDialog = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Import Study Data", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Restore study data from JSON", color = TextMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.Upload, contentDescription = null, tint = CyanBrand)
                        }
                        Divider(color = CardBorder)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showClearConfirm = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Delete All Study Data", color = CoralAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Reset all study subjects, topics, and sessions", color = TextMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CoralAccent)
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Delete All Study Data?", color = TextWhite) },
            text = { Text("This will permanently remove all subjects, topics, flashcards, and study history. This action cannot be undone.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralAccent)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBg
        )
    }

    if (showExportedDialog != null) {
        AlertDialog(
            onDismissRequest = { showExportedDialog = null },
            title = { Text("Export Successful", color = TextWhite) },
            text = { Text("Study data successfully compiled. You can copy the export or backup your progress safely.", color = TextMuted) },
            confirmButton = {
                Button(onClick = { showExportedDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)) {
                    Text("Done")
                }
            },
            containerColor = CardBg
        )
    }

    if (showImportDialog) {
        var importInput by remember { mutableStateOf("") }
        var importError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Study Data", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste your exported JSON data below to merge or restore:", color = TextMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = importInput,
                        onValueChange = { importInput = it; importError = null },
                        placeholder = { Text("Paste JSON here...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (importError != null) {
                        Text(importError!!, color = CoralAccent, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onImportData(importInput)) {
                            showImportDialog = false
                        } else {
                            importError = "Invalid JSON format. Please check your data."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBg
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PurplePrimary,
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
}

// ==========================================
// 19. DIALOGS & HELPER COMPOSABLES
// ==========================================

@Composable
private fun ScheduleStudyDialog(
    subjects: List<Subject>,
    topics: List<Topic>,
    today: String,
    onDismiss: () -> Unit,
    onConfirm: (subName: String, topName: String, date: String, start: String, end: String) -> Unit
) {
    var selectedSub by remember { mutableStateOf(subjects.firstOrNull()?.name ?: "General Study") }
    var selectedTop by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var startTime by remember { mutableStateOf("04:00 PM") }
    var endTime by remember { mutableStateOf("05:00 PM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Study Session", color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Subject: $selectedSub", color = PurpleLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start Time") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End Time") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedSub, selectedTop, date, startTime, endTime) },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
        containerColor = CardBg
    )
}

@Composable
private fun EditDailyGoalDialog(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hours by remember { mutableStateOf((currentMinutes / 60).toString()) }
    var minutes by remember { mutableStateOf((currentMinutes % 60).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Study Goal", color = TextWhite) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text("Hours") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it },
                    label = { Text("Minutes") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = hours.toIntOrNull() ?: 0
                    val m = minutes.toIntOrNull() ?: 0
                    val total = (h * 60 + m).coerceIn(15, 1440)
                    onConfirm(total)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
        containerColor = CardBg
    )
}

@Composable
private fun AddFlashcardDialog(
    subjects: List<Subject>,
    topics: List<Topic>,
    onDismiss: () -> Unit,
    onConfirm: (Flashcard) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var explanation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Flashcard", color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question *") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Answer *") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("Explanation (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardInner, unfocusedContainerColor = CardInner,
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (question.isNotBlank() && answer.isNotBlank()) {
                        onConfirm(Flashcard(question = question.trim(), answer = answer.trim(), explanation = explanation.trim()))
                    }
                },
                enabled = question.isNotBlank() && answer.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
        containerColor = CardBg
    )
}

@Composable
private fun AddQuizQuestionDialog(
    subjects: List<Subject>,
    topics: List<Topic>,
    onDismiss: () -> Unit,
    onConfirm: (QuizQuestion) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var opt1 by remember { mutableStateOf("") }
    var opt2 by remember { mutableStateOf("") }
    var opt3 by remember { mutableStateOf("") }
    var opt4 by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Quiz Question", color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question *") },
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardInner, unfocusedContainerColor = CardInner, focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = opt1, onValueChange = { opt1 = it }, label = { Text("Option A (Correct)") }, colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardInner, unfocusedContainerColor = CardInner, focusedTextColor = TextWhite, unfocusedTextColor = TextWhite), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = opt2, onValueChange = { opt2 = it }, label = { Text("Option B") }, colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardInner, unfocusedContainerColor = CardInner, focusedTextColor = TextWhite, unfocusedTextColor = TextWhite), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = opt3, onValueChange = { opt3 = it }, label = { Text("Option C") }, colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardInner, unfocusedContainerColor = CardInner, focusedTextColor = TextWhite, unfocusedTextColor = TextWhite), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = opt4, onValueChange = { opt4 = it }, label = { Text("Option D") }, colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardInner, unfocusedContainerColor = CardInner, focusedTextColor = TextWhite, unfocusedTextColor = TextWhite), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val opts = listOf(opt1.trim(), opt2.trim(), opt3.trim(), opt4.trim()).filter { it.isNotBlank() }
                    if (question.isNotBlank() && opts.size >= 2) {
                        onConfirm(QuizQuestion(question = question.trim(), options = opts, correctOptionIndex = 0))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
        containerColor = CardBg
    )
}

@Composable
private fun AddResourceDialog(
    subjects: List<Subject>,
    topics: List<Topic>,
    today: String,
    onDismiss: () -> Unit,
    onConfirm: (StudyResource) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(StudyResourceType.NOTE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Learning Resource", color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardInner, unfocusedContainerColor = CardInner, focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content or URL *") },
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardInner, unfocusedContainerColor = CardInner, focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onConfirm(StudyResource(title = title.trim(), contentOrUrl = content.trim(), type = type, date = today))
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
        containerColor = CardBg
    )
}

@Composable
private fun CustomTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var minutes by remember { mutableStateOf("35") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Duration", color = TextWhite) },
        text = {
            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it },
                label = { Text("Minutes (1..720)") },
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardInner, unfocusedContainerColor = CardInner, focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = minutes.toIntOrNull()?.coerceIn(1, 720) ?: 25
                    onConfirm(m)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        },
        containerColor = CardBg
    )
}

private fun getSubjectIcon(name: String): ImageVector {
    return when (name.lowercase()) {
        "code" -> Icons.Default.Code
        "chip" -> Icons.Default.Memory
        "cube" -> Icons.Default.ViewInAr
        "flask" -> Icons.Default.Science
        "palette" -> Icons.Default.Palette
        "globe" -> Icons.Default.Public
        "math" -> Icons.Default.Calculate
        else -> Icons.Default.MenuBook
    }
}
