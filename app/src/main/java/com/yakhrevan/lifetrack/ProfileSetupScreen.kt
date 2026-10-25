package com.yakhrevan.lifetrack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors

internal data class LifeTrackProfile(
    val name: String,
    val age: Int,
    val heightCm: Int,
    val weightKg: Int,
    val waterGoalMl: Int,
    val studyGoalMinutes: Int,
    val remindersEnabled: Boolean,
    val reminderIntervalMinutes: Int
)

@Composable
internal fun ProfileSetupScreen(
    initialName: String = "",
    initialAge: Int? = null,
    initialHeightCm: Int? = null,
    initialWeightKg: Int? = null,
    initialWaterGoalMl: Int? = null,
    initialStudyGoalMinutes: Int = Goals.DEFAULT_STUDY_MIN,
    initialRemindersEnabled: Boolean = false,
    initialReminderIntervalMinutes: Int = 60,
    onComplete: (LifeTrackProfile) -> Unit,
    onSkip: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(initialName) }
    var age by remember { mutableStateOf(initialAge?.toString() ?: "") }
    var height by remember { mutableStateOf(initialHeightCm?.toString() ?: "") }
    var weight by remember { mutableStateOf(initialWeightKg?.toString() ?: "") }
    var waterGoalInput by remember { mutableStateOf(initialWaterGoalMl?.toString() ?: "") }
    var studyGoal by remember { mutableStateOf(initialStudyGoalMinutes.toString()) }
    var reminders by remember { mutableStateOf(initialRemindersEnabled) }
    var reminderInterval by remember { mutableIntStateOf(initialReminderIntervalMinutes.coerceIn(15, 240)) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    val parsedAge = age.toIntOrNull()?.takeIf { it in 13..120 }
    val parsedHeight = height.toIntOrNull()?.takeIf { it in 100..250 }
    val parsedWeight = weight.toIntOrNull()?.takeIf { it in 20..300 }
    val parsedStudyGoal = Goals.parseStudy(studyGoal)
    val suggestedWaterGoal = parsedWeight?.let { (it * 35).coerceIn(1500, 5000) }
    val parsedWaterGoal = Goals.parseWater(waterGoalInput) ?: suggestedWaterGoal
    val waterGoalValid = (waterGoalInput.isBlank() && suggestedWaterGoal != null) ||
        Goals.parseWater(waterGoalInput) != null

    val pageTitles = listOf("Your profile", "Body measurements", "Daily targets")
    val pageSubtitles = listOf(
        "Let's make LifeTrack feel like yours.",
        "These details personalise your wellness tracking.",
        "Choose realistic targets. You can change them any time."
    )

    Surface(color = LifeTrackColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(42.dp).background(
                                LifeTrackColors.Primary.copy(alpha = 0.14f), RoundedCornerShape(14.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = LifeTrackColors.Primary)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("LifeTrack", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            Text("PERSONAL SETUP", color = LifeTrackColors.TextSecondary, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = onSkip) { Text("Skip for now", color = LifeTrackColors.TextSecondary, fontSize = 12.sp) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier.weight(1f).height(5.dp).background(
                                if (index <= step) LifeTrackColors.Primary else LifeTrackColors.SurfaceRaised,
                                CircleShape
                            )
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("STEP ${step + 1} OF 3", color = LifeTrackColors.Primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.6.sp)
                    Text(pageTitles[step], color = LifeTrackColors.TextPrimary, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Text(pageSubtitles[step], color = LifeTrackColors.TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
                }

                when (step) {
                    0 -> {
                        SetupSectionCard {
                            SetupField("Your name", name, { name = it; validationMessage = null }, KeyboardType.Text, hint = "What should we call you?")
                            SetupField("Age", age, { age = it.filter(Char::isDigit); validationMessage = null }, KeyboardType.Number, hint = "13–120 years")
                            Text("Age is used only where relevant to personal wellness insights.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                        SetupInfoStrip(
                            icon = Icons.Default.Lock,
                            title = "Your data stays yours",
                            message = "Your profile is stored locally on this device. You can edit it later."
                        )
                    }
                    1 -> {
                        SetupSectionCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SetupMetricIcon(Icons.Default.Straighten)
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("Height", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Centimetres (cm)", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                                }
                            }
                            SetupField("Height (cm)", height, { height = it.filter(Char::isDigit); validationMessage = null }, KeyboardType.Number, hint = "100–250")
                            HorizontalDivider(color = LifeTrackColors.Border)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SetupMetricIcon(Icons.Default.FitnessCenter)
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("Weight", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Kilograms (kg)", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                                }
                            }
                            SetupField("Weight (kg)", weight, { weight = it.filter(Char::isDigit); validationMessage = null }, KeyboardType.Number, hint = "20–300")
                        }
                        SetupInfoStrip(
                            icon = Icons.Default.Insights,
                            title = "Personalised, not prescriptive",
                            message = "Measurements help with relevant tracking. You can update them whenever they change."
                        )
                    }
                    else -> {
                        SetupSectionCard {
                            Text("DAILY TARGETS", color = LifeTrackColors.Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SetupMetricIcon(Icons.Default.WaterDrop)
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("Water intake", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        suggestedWaterGoal?.let { "Suggested target: ${Goals.litres(it)} per day" } ?: "Add weight to estimate a starting target.",
                                        color = LifeTrackColors.TextSecondary, fontSize = 11.sp
                                    )
                                }
                            }
                            SetupField("Water goal (ml)", waterGoalInput, { waterGoalInput = it.filter(Char::isDigit); validationMessage = null }, KeyboardType.Number, hint = "500–10000 · optional for suggestion")
                            Text("The suggested amount is a starting estimate, not medical advice. Adjust it to your needs.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                            HorizontalDivider(color = LifeTrackColors.Border)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SetupMetricIcon(Icons.Default.MenuBook)
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("Study & focus", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("A daily focus target in minutes", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                                }
                            }
                            SetupField("Study goal (minutes/day)", studyGoal, { studyGoal = it.filter(Char::isDigit); validationMessage = null }, KeyboardType.Number, hint = "15–1440")
                        }
                        SetupSectionCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Water reminders", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Optional nudges throughout your day", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                                }
                                Switch(checked = reminders, onCheckedChange = { reminders = it })
                            }
                            if (reminders) {
                                Text("Remind me every", color = LifeTrackColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf(30, 60, 90, 120).forEach { interval ->
                                        FilterChip(
                                            selected = reminderInterval == interval,
                                            onClick = { reminderInterval = interval },
                                            label = { Text(if (interval < 60) "$interval min" else "${interval / 60} hr", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                validationMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(4.dp))
            }

            Surface(color = LifeTrackColors.Background, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val error = when (step) {
                                0 -> when {
                                    name.isBlank() -> "Enter your name to continue."
                                    parsedAge == null -> "Enter an age between 13 and 120."
                                    else -> null
                                }
                                1 -> when {
                                    parsedHeight == null -> "Enter a height between 100 and 250 cm."
                                    parsedWeight == null -> "Enter a weight between 20 and 300 kg."
                                    else -> null
                                }
                                else -> when {
                                    parsedStudyGoal == null -> "Enter a study goal between 15 and 1440 minutes."
                                    !waterGoalValid -> "Enter a water target between 500 and 10000 ml, or leave it blank for a suggestion."
                                    else -> null
                                }
                            }
                            validationMessage = error
                            if (error == null) {
                                if (step < 2) {
                                    step += 1
                                } else {
                                    onComplete(
                                        LifeTrackProfile(
                                            name = name.trim(),
                                            age = parsedAge ?: return@Button,
                                            heightCm = parsedHeight ?: return@Button,
                                            weightKg = parsedWeight ?: return@Button,
                                            waterGoalMl = parsedWaterGoal ?: Goals.DEFAULT_WATER_ML,
                                            studyGoalMinutes = parsedStudyGoal ?: return@Button,
                                            remindersEnabled = reminders,
                                            reminderIntervalMinutes = reminderInterval
                                        )
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LifeTrackColors.Primary, contentColor = Color.White)
                    ) {
                        Text(if (step == 2) "Complete profile" else "Continue", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(if (step == 2) Icons.Default.CheckCircle else Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    if (step > 0) {
                        TextButton(onClick = { step -= 1; validationMessage = null }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Back", color = LifeTrackColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, LifeTrackColors.Border)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
            content = content
        )
    }
}

@Composable
private fun SetupMetricIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier.size(42.dp).background(LifeTrackColors.Primary.copy(alpha = 0.13f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = LifeTrackColors.Primary)
    }
}

@Composable
private fun SetupInfoStrip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(LifeTrackColors.SurfaceRaised, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Icon(icon, contentDescription = null, tint = LifeTrackColors.Primary, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(message, color = LifeTrackColors.TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SetupField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    hint: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        placeholder = { hint?.let { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LifeTrackColors.Primary,
            focusedLabelColor = LifeTrackColors.Primary,
            cursorColor = LifeTrackColors.Primary,
            focusedTextColor = LifeTrackColors.TextPrimary,
            unfocusedTextColor = LifeTrackColors.TextPrimary,
            focusedContainerColor = LifeTrackColors.Surface,
            unfocusedContainerColor = LifeTrackColors.Surface
        )
    )
}
