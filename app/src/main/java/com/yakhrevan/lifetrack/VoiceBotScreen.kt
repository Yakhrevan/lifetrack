package com.yakhrevan.lifetrack

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// 1. DESIGN TOKENS
// ==========================================
private val DarkBackground = Color(0xFF090D1A)
private val CardBackground = Color(0xFF121929)
private val CardInner      = Color(0xFF1B2438)
private val CardBorder     = Color(0x1FFFFFFF)
private val PurpleBrand    = Color(0xFFA855F7)
private val CyanBrand      = Color(0xFF06B6D4)
private val EmeraldBrand   = Color(0xFF10B981)
private val AmberBrand     = Color(0xFFF59E0B)
private val CoralBrand     = Color(0xFFF43F5E)
private val TextWhite      = Color(0xFFF1F5F9)
private val TextMuted      = Color(0xFF94A3B8)

// ==========================================
// 2. MAIN VOICE BOT SCREEN
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceBotScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onNavigateToPage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(VoiceBotState.IDLE) }
    var selectedLanguage by remember { mutableStateOf("en-IN") } // "en-IN", "ta-IN", "ta-Tanglish", "hi-IN"
    var transcriptInput by remember { mutableStateOf("") }
    var partialTranscript by remember { mutableStateOf("") }
    var currentPlan by remember { mutableStateOf<VoiceActionPlan?>(null) }
    var executionResultText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var rmsValue by remember { mutableFloatStateOf(0f) }
    var isTtsMuted by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var historyList by remember { mutableStateOf(VoiceBotHistoryRepository.getHistory(context)) }

    // Audio Engine
    val audioEngine = remember {
        VoiceBotAudioEngine(
            context = context,
            onTranscriptResult = { recognizedText ->
                transcriptInput = recognizedText
                partialTranscript = ""
                state = VoiceBotState.PROCESSING
                scope.launch {
                    val plan = LocalVoiceIntentParser.parse(recognizedText)
                    currentPlan = plan
                    if (plan.requiresConfirmation) {
                        state = VoiceBotState.CONFIRMATION
                    } else if (!plan.isFullyResolved) {
                        state = VoiceBotState.CLARIFICATION
                    } else {
                        // Auto-execute single low-risk actions
                        state = VoiceBotState.EXECUTING
                        val (executedPlan, result) = VoiceBotActionExecutor.executePlan(context, plan, onNavigateToPage)
                        currentPlan = executedPlan
                        executionResultText = result
                        state = VoiceBotState.COMPLETED
                        historyList = VoiceBotHistoryRepository.getHistory(context)
                    }
                }
            },
            onPartialTranscript = { partial ->
                partialTranscript = partial
            },
            onError = { err ->
                state = VoiceBotState.FAILED
                errorMessage = err
            },
            onRmsChangedCallback = { rms ->
                rmsValue = rms
            }
        )
    }

    LaunchedEffect(isTtsMuted) {
        audioEngine.isTtsEnabled = !isTtsMuted
    }

    DisposableEffect(Unit) {
        onDispose {
            audioEngine.release()
        }
    }

    // Permission launcher for recording audio
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            state = VoiceBotState.LISTENING
            errorMessage = null
            audioEngine.startListening(selectedLanguage)
        } else {
            state = VoiceBotState.FAILED
            errorMessage = "Microphone permission is required to use push-to-talk speech recognition."
        }
    }

    // Android System Speech Dialog Fallback launcher
    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                transcriptInput = text
                state = VoiceBotState.PROCESSING
                scope.launch {
                    val plan = LocalVoiceIntentParser.parse(text)
                    currentPlan = plan
                    if (plan.requiresConfirmation) {
                        state = VoiceBotState.CONFIRMATION
                    } else if (!plan.isFullyResolved) {
                        state = VoiceBotState.CLARIFICATION
                    } else {
                        state = VoiceBotState.EXECUTING
                        val (executedPlan, res) = VoiceBotActionExecutor.executePlan(context, plan, onNavigateToPage)
                        currentPlan = executedPlan
                        executionResultText = res
                        state = VoiceBotState.COMPLETED
                        historyList = VoiceBotHistoryRepository.getHistory(context)
                    }
                }
            }
        }
    }

    fun startVoiceInput() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            state = VoiceBotState.LISTENING
            errorMessage = null
            partialTranscript = ""
            val started = audioEngine.startListening(selectedLanguage)
            if (!started) {
                // Fallback to system dialog
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command (e.g. 500ml water, ₹150 for lunch)")
                }
                try {
                    systemSpeechLauncher.launch(intent)
                } catch (e: Exception) {
                    state = VoiceBotState.FAILED
                    errorMessage = "No speech service found on this device. You can type your command below."
                }
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun submitTextCommand(cmd: String) {
        if (cmd.isBlank()) return
        audioEngine.stopListening()
        state = VoiceBotState.PROCESSING
        errorMessage = null
        scope.launch {
            val plan = LocalVoiceIntentParser.parse(cmd)
            currentPlan = plan
            if (plan.requiresConfirmation) {
                state = VoiceBotState.CONFIRMATION
            } else if (!plan.isFullyResolved) {
                state = VoiceBotState.CLARIFICATION
            } else {
                state = VoiceBotState.EXECUTING
                val (executedPlan, res) = VoiceBotActionExecutor.executePlan(context, plan, onNavigateToPage)
                currentPlan = executedPlan
                executionResultText = res
                state = VoiceBotState.COMPLETED
                historyList = VoiceBotHistoryRepository.getHistory(context)
            }
        }
    }

    fun confirmAndExecuteCurrentPlan() {
        val plan = currentPlan ?: return
        state = VoiceBotState.EXECUTING
        scope.launch {
            val (executedPlan, res) = VoiceBotActionExecutor.executePlan(context, plan, onNavigateToPage)
            currentPlan = executedPlan
            executionResultText = res
            state = VoiceBotState.COMPLETED
            historyList = VoiceBotHistoryRepository.getHistory(context)
        }
    }

    Surface(color = DarkBackground, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // 1. Top Bar: Back | Title & Agent Badge | TTS Mute Toggle | History Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Voice Bot", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Surface(
                                color = PurpleBrand.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, PurpleBrand.copy(alpha = 0.4f))
                            ) {
                                Text("AGENT", color = PurpleBrand, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text("Natural language personal assistant", color = TextMuted, fontSize = 11.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { isTtsMuted = !isTtsMuted }) {
                        Icon(
                            if (isTtsMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Voice Output",
                            tint = if (isTtsMuted) TextMuted else CyanBrand
                        )
                    }
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(Icons.Default.History, contentDescription = "History", tint = TextWhite)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 2. Language Selection Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "English (IN)" to "en-IN",
                    "தமிழ் (Tamil)" to "ta-IN",
                    "Tanglish" to "ta-Tanglish",
                    "हिंदी (Hindi)" to "hi-IN"
                ).forEach { (label, code) ->
                    val isSelected = selectedLanguage == code
                    Surface(
                        color = if (isSelected) PurpleBrand.copy(alpha = 0.25f) else CardBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isSelected) PurpleBrand else CardBorder),
                        modifier = Modifier.clickable { selectedLanguage = code }
                    ) {
                        Text(
                            label,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 3. Central Content: Dynamic State Area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Agent Status Pill
                item {
                    AgentStatusBadge(state = state)
                }

                // Proposed Action Card (When plan exists)
                if (currentPlan != null && currentPlan!!.actions.isNotEmpty()) {
                    item {
                        ActionPlanPreviewCard(
                            plan = currentPlan!!,
                            state = state,
                            onConfirm = { confirmAndExecuteCurrentPlan() },
                            onCancel = {
                                state = VoiceBotState.CANCELLED
                                currentPlan = null
                            },
                            onProvideClarification = { clarificationInput ->
                                transcriptInput = "$transcriptInput $clarificationInput".trim()
                                submitTextCommand(transcriptInput)
                            }
                        )
                    }
                }

                // Execution Outcome Banner
                if (state == VoiceBotState.COMPLETED && executionResultText.isNotBlank()) {
                    item {
                        Surface(
                            color = EmeraldBrand.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, EmeraldBrand.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(EmeraldBrand.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldBrand, modifier = Modifier.size(20.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Action Completed", color = EmeraldBrand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(executionResultText, color = TextWhite, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Error Banner
                if (errorMessage != null) {
                    item {
                        Surface(
                            color = CoralBrand.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, CoralBrand.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CoralBrand)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Issue Encountered", color = CoralBrand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(errorMessage!!, color = TextWhite, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Quick Prompt Ideas
                item {
                    Text("Try saying or typing:", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "💧 Drank 500 ml water" to "I drank 500 ml water",
                            "💰 Spent ₹150 for lunch" to "Spent 150 for lunch",
                            "📝 Task submit project by tomorrow" to "Add task submit project by tomorrow",
                            "⏱️ Studied 45 mins of Robotics" to "Studied 45 mins of Robotics",
                            "🗣️ 250 ruba biryani sapten" to "250 ruba biryani sapten",
                            "📅 Tomorrow team sync at 4 PM calendar-la add pannu" to "Tomorrow team sync at 4 PM calendar-la add pannu"
                        ).forEach { (display, cmd) ->
                            Surface(
                                color = CardBackground,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        transcriptInput = cmd
                                        submitTextCommand(cmd)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(display, color = TextWhite, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 4. Live Wave / Partial Transcript Indicator
            if (state == VoiceBotState.LISTENING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (partialTranscript.isNotBlank()) "“$partialTranscript...”" else "Listening... speak now",
                        color = CyanBrand,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // 5. Input Bar: Text Fallback with Mic Trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBackground)
                    .border(1.dp, if (state == VoiceBotState.LISTENING) CyanBrand else CardBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = transcriptInput,
                    onValueChange = { transcriptInput = it },
                    placeholder = { Text("Speak or type a command...", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                if (transcriptInput.isNotBlank()) {
                    IconButton(
                        onClick = {
                            submitTextCommand(transcriptInput)
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = PurpleBrand)
                    }
                }

                // Central Push-To-Talk Mic Button
                PulsingMicButton(
                    isListening = state == VoiceBotState.LISTENING,
                    rmsValue = rmsValue,
                    onClick = {
                        if (state == VoiceBotState.LISTENING) {
                            audioEngine.stopListening()
                            state = VoiceBotState.IDLE
                        } else {
                            startVoiceInput()
                        }
                    }
                )
            }
        }
    }

    // History Sheet
    if (showHistorySheet) {
        VoiceBotHistoryModal(
            history = historyList,
            onDismiss = { showHistorySheet = false },
            onUndo = { item ->
                val undone = VoiceBotActionExecutor.undoAction(context, item)
                if (undone) {
                    historyList = VoiceBotHistoryRepository.getHistory(context)
                }
            },
            onClearAll = {
                VoiceBotHistoryRepository.clearHistory(context)
                historyList = emptyList()
            }
        )
    }
}

// ==========================================
// 3. ACTION PLAN PREVIEW CARD
// ==========================================

@Composable
private fun ActionPlanPreviewCard(
    plan: VoiceActionPlan,
    state: VoiceBotState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onProvideClarification: (String) -> Unit
) {
    Surface(
        color = CardBackground,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (state == VoiceBotState.CONFIRMATION) AmberBrand.copy(alpha = 0.5f) else CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Action Count & Intent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = PurpleBrand, modifier = Modifier.size(16.dp))
                    Text(
                        if (plan.isMultiStep) "Compound Plan (${plan.actions.size} steps)" else "Proposed Action",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = if (plan.isFullyResolved) EmeraldBrand.copy(alpha = 0.15f) else AmberBrand.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (plan.isFullyResolved) "Resolved" else "Needs Info",
                        color = if (plan.isFullyResolved) EmeraldBrand else AmberBrand,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Action Items
            plan.actions.forEachIndexed { index, action ->
                Surface(
                    color = CardInner,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${index + 1}.", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Icon(
                                    when (action.module) {
                                        VoiceModule.WATER -> Icons.Default.WaterDrop
                                        VoiceModule.EXPENSE -> Icons.Default.Receipt
                                        VoiceModule.INCOME -> Icons.Default.AccountBalanceWallet
                                        VoiceModule.TASK -> Icons.Default.CheckCircle
                                        VoiceModule.NOTE -> Icons.Default.Description
                                        VoiceModule.STUDY -> Icons.Default.Timer
                                        VoiceModule.CALENDAR -> Icons.Default.Event
                                        else -> Icons.Default.Check
                                    },
                                    contentDescription = null,
                                    tint = CyanBrand,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(action.module.label, color = CyanBrand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(action.actionType.label, color = TextMuted, fontSize = 11.sp)
                        }

                        // Details
                        Text(action.title.ifBlank { action.rawText }, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (action.amount != null) {
                                Text("Value: ${if (action.unit == "INR") "₹" else ""}${action.amount} ${if (action.unit != "INR") action.unit.orEmpty() else ""}", color = TextMuted, fontSize = 11.sp)
                            }
                            if (action.durationMinutes != null) {
                                Text("Duration: ${action.durationMinutes} mins", color = TextMuted, fontSize = 11.sp)
                            }
                            if (action.date != null) {
                                Text("Date: ${action.date}", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        // Clarification prompt if missing fields
                        if (!action.isCompleteAndValid && action.clarificationPrompt != null) {
                            Surface(
                                color = AmberBrand.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = AmberBrand, modifier = Modifier.size(14.dp))
                                    Text(action.clarificationPrompt, color = AmberBrand, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Confirmation or Cancel Controls
            if (state == VoiceBotState.CONFIRMATION || state == VoiceBotState.CLARIFICATION) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onConfirm,
                        enabled = plan.isFullyResolved,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (plan.isMultiStep) "Confirm All" else "Confirm & Execute", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. ANIMATED PULSING MIC BUTTON
// ==========================================

@Composable
private fun PulsingMicButton(
    isListening: Boolean,
    rmsValue: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .scale(pulseScale)
                    .background(CyanBrand.copy(alpha = 0.25f), CircleShape)
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isListening) Brush.linearGradient(listOf(CyanBrand, EmeraldBrand))
                    else Brush.linearGradient(listOf(PurpleBrand, Color(0xFF6366F1)))
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                contentDescription = "Push to Talk",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==========================================
// 5. AGENT STATUS BADGE
// ==========================================

@Composable
private fun AgentStatusBadge(state: VoiceBotState) {
    val (color, text, icon) = when (state) {
        VoiceBotState.IDLE -> Triple(TextMuted, "Ready. Tap microphone or type a command.", Icons.Default.SmartToy)
        VoiceBotState.LISTENING -> Triple(CyanBrand, "Listening to speech...", Icons.Default.GraphicEq)
        VoiceBotState.PROCESSING -> Triple(PurpleBrand, "Analyzing intent & resolving entities...", Icons.Default.HourglassTop)
        VoiceBotState.CLARIFICATION -> Triple(AmberBrand, "Clarification needed to proceed safely.", Icons.Default.Help)
        VoiceBotState.CONFIRMATION -> Triple(AmberBrand, "Awaiting your confirmation to execute.", Icons.Default.Security)
        VoiceBotState.EXECUTING -> Triple(CyanBrand, "Executing repository operations...", Icons.Default.Sync)
        VoiceBotState.COMPLETED -> Triple(EmeraldBrand, "Command executed successfully.", Icons.Default.CheckCircle)
        VoiceBotState.FAILED -> Triple(CoralBrand, "Execution encountered an error.", Icons.Default.Error)
        VoiceBotState.CANCELLED -> Triple(TextMuted, "Action cancelled.", Icons.Default.Close)
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ==========================================
// 6. HISTORY MODAL SHEET
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceBotHistoryModal(
    history: List<VoiceHistoryItem>,
    onDismiss: () -> Unit,
    onUndo: (VoiceHistoryItem) -> Unit,
    onClearAll: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Voice Action History", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear All", color = CoralBrand, fontSize = 12.sp)
                    }
                }
            }

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No voice actions logged yet.", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(history, key = { it.id }) { item ->
                        Surface(
                            color = CardBackground,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(item.module.label, color = CyanBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("•", color = TextMuted, fontSize = 10.sp)
                                        Text(SimpleDateFormat("hh:mm a", Locale.US).format(Date(item.timestamp)), color = TextMuted, fontSize = 10.sp)
                                    }
                                    Text("“${item.commandText}”", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(item.actionSummary, color = if (item.isUndone) TextMuted else EmeraldBrand, fontSize = 11.sp)
                                }

                                if (item.canUndo && !item.isUndone) {
                                    OutlinedButton(
                                        onClick = { onUndo(item) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Undo, contentDescription = "Undo", modifier = Modifier.size(12.dp), tint = AmberBrand)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Undo", color = AmberBrand, fontSize = 11.sp)
                                    }
                                } else if (item.isUndone) {
                                    Text("Undone", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
