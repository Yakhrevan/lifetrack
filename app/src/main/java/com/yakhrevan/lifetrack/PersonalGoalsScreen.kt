package com.yakhrevan.lifetrack

import android.content.Context
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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

private data class GoalIconItem(val name: String, val icon: ImageVector)

private val GOAL_ICONS = listOf(
    GoalIconItem("fitness", Icons.Default.FitnessCenter),
    GoalIconItem("running", Icons.Default.DirectionsRun),
    GoalIconItem("book", Icons.Default.MenuBook),
    GoalIconItem("savings", Icons.Default.Savings),
    GoalIconItem("music", Icons.Default.MusicNote),
    GoalIconItem("water", Icons.Default.WaterDrop),
    GoalIconItem("heart", Icons.Default.Favorite),
    GoalIconItem("leaf", Icons.Default.Eco),
    GoalIconItem("trophy", Icons.Default.EmojiEvents),
    GoalIconItem("star", Icons.Default.Star),
    GoalIconItem("target", Icons.Default.TrackChanges),
    GoalIconItem("code", Icons.Default.Code),
    GoalIconItem("bedtime", Icons.Default.Bedtime)
)

private val GOAL_COLORS = listOf(
    "#A855F7", "#06B6D4", "#3B82F6", "#10B981", "#EAB308", "#F97316", "#F43F5E", "#EC4899"
)

// ==========================================
// 2. MAIN PERSONAL GOALS SCREEN
// ==========================================

@Composable
fun PersonalGoalsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val goals = remember(refreshTrigger) { PersonalGoalsRepository.getGoals(context) }
    var editingGoal by remember { mutableStateOf<PersonalGoal?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var deletingGoal by remember { mutableStateOf<PersonalGoal?>(null) }
    var quickProgressGoal by remember { mutableStateOf<PersonalGoal?>(null) }

    Surface(color = DarkBackground, modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text("Personal Goals", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = { isAddingNew = true },
                    modifier = Modifier
                        .size(38.dp)
                        .background(PurpleBrand, CircleShape)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Goal", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            if (goals.isEmpty()) {
                // Screen 4: Empty State
                PersonalGoalsEmptyState(onAddGoal = { isAddingNew = true }, modifier = Modifier.weight(1f))
            } else {
                // Screen 6: Personal Goals List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(goals, key = { it.id }) { goal ->
                        PersonalGoalCard(
                            goal = goal,
                            onEdit = { editingGoal = goal },
                            onDelete = { deletingGoal = goal },
                            onIncrement = {
                                PersonalGoalsRepository.incrementProgress(context, goal.id, 1.0)
                                refreshTrigger++
                            },
                            onQuickUpdate = { quickProgressGoal = goal }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Goal Dialog
    if (isAddingNew || editingGoal != null) {
        val target = editingGoal
        AddEditGoalDialog(
            initialGoal = target,
            onSave = { saved ->
                PersonalGoalsRepository.saveGoal(context, saved)
                editingGoal = null
                isAddingNew = false
                refreshTrigger++
            },
            onDismiss = {
                editingGoal = null
                isAddingNew = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (deletingGoal != null) {
        AlertDialog(
            onDismissRequest = { deletingGoal = null },
            containerColor = CardBackground,
            title = { Text("Delete Goal?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${deletingGoal?.title}'? This action cannot be undone.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        deletingGoal?.let { PersonalGoalsRepository.deleteGoal(context, it.id) }
                        deletingGoal = null
                        refreshTrigger++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralBrand)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingGoal = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Quick Update Progress Dialog
    if (quickProgressGoal != null) {
        QuickProgressDialog(
            goal = quickProgressGoal!!,
            onSave = { updatedProgress ->
                PersonalGoalsRepository.updateProgress(context, quickProgressGoal!!.id, updatedProgress)
                quickProgressGoal = null
                refreshTrigger++
            },
            onDismiss = { quickProgressGoal = null }
        )
    }
}

// ==========================================
// 3. SCREEN 4: EMPTY STATE
// ==========================================

@Composable
private fun PersonalGoalsEmptyState(
    onAddGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Target Dartboard Vector Graphic with Purple Aura
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        Brush.radialGradient(listOf(PurpleBrand.copy(alpha = 0.35f), Color.Transparent)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .background(CardBackground, CircleShape)
                        .border(1.5.dp, PurpleBrand.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.TrackChanges,
                        contentDescription = "Goals",
                        tint = PurpleBrand,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Text("No goals yet", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Text(
                "Create your own goals to stay motivated and track your progress.",
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onAddGoal,
                colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Add Goal", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 4. SCREEN 6: GOAL CARD ITEM
// ==========================================

@Composable
private fun PersonalGoalCard(
    goal: PersonalGoal,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onIncrement: () -> Unit,
    onQuickUpdate: () -> Unit
) {
    val goalColor = runCatching { Color(android.graphics.Color.parseColor(goal.colorHex)) }.getOrDefault(PurpleBrand)
    val iconItem = GOAL_ICONS.find { it.name == goal.iconName } ?: GOAL_ICONS.first()

    val currentFormatted = if (goal.currentAmount % 1.0 == 0.0) goal.currentAmount.toInt().toString() else String.format(Locale.US, "%.1f", goal.currentAmount)
    val targetFormatted = if (goal.targetAmount % 1.0 == 0.0) goal.targetAmount.toInt().toString() else String.format(Locale.US, "%.1f", goal.targetAmount)

    Surface(
        color = CardBackground,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Icon, Title & Subtitle, Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Colored Icon Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(goalColor.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconItem.icon, contentDescription = null, tint = goalColor, modifier = Modifier.size(24.dp))
                }

                // Title & Description
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        goal.title,
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (goal.description.isNotBlank()) {
                        Text(
                            goal.description,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Edit & Delete Buttons
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CoralBrand, modifier = Modifier.size(16.dp))
                }
            }

            // Row 2: Progress Numbers & Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val progressText = if (goal.unit.contains("₹") || goal.unit.contains("$")) {
                    "${goal.unit}$currentFormatted / ${goal.unit}$targetFormatted"
                } else {
                    "$currentFormatted / $targetFormatted ${goal.unit}"
                }
                Text(
                    progressText,
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onQuickUpdate)
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${goal.progressPercentage}%",
                        color = goalColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Quick Increment Button
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(goalColor.copy(alpha = 0.25f))
                            .clickable(onClick = onIncrement),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "+1", tint = goalColor, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Row 3: Linear Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(goal.progressFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(goalColor)
                )
            }
        }
    }
}

// ==========================================
// 5. SCREEN 5: ADD / EDIT GOAL DIALOG
// ==========================================

@Composable
fun AddEditGoalDialog(
    initialGoal: PersonalGoal?,
    onSave: (PersonalGoal) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialGoal?.title ?: "") }
    var description by remember { mutableStateOf(initialGoal?.description ?: "") }
    var selectedIcon by remember { mutableStateOf(initialGoal?.iconName ?: "fitness") }
    var selectedColor by remember { mutableStateOf(initialGoal?.colorHex ?: "#A855F7") }
    var targetType by remember { mutableStateOf(initialGoal?.targetType ?: "Custom Target") }
    var targetAmountInput by remember { mutableStateOf(initialGoal?.let { if (it.targetAmount % 1.0 == 0.0) it.targetAmount.toInt().toString() else it.targetAmount.toString() } ?: "10") }
    var unit by remember { mutableStateOf(initialGoal?.unit ?: "sessions") }
    var startDate by remember { mutableStateOf(initialGoal?.startDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var targetDate by remember { mutableStateOf(initialGoal?.targetDate ?: "") }

    val isValid = title.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (initialGoal == null) "Add Goal" else "Edit Goal",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        val parsedAmount = targetAmountInput.toDoubleOrNull() ?: 1.0
                        val goal = initialGoal?.copy(
                            title = title.trim(),
                            description = description.trim(),
                            iconName = selectedIcon,
                            colorHex = selectedColor,
                            targetType = targetType,
                            targetAmount = parsedAmount.coerceAtLeast(1.0),
                            unit = unit.trim().ifEmpty { "sessions" },
                            startDate = startDate,
                            targetDate = targetDate
                        ) ?: PersonalGoal(
                            title = title.trim(),
                            description = description.trim(),
                            iconName = selectedIcon,
                            colorHex = selectedColor,
                            targetType = targetType,
                            currentAmount = 0.0,
                            targetAmount = parsedAmount.coerceAtLeast(1.0),
                            unit = unit.trim().ifEmpty { "sessions" },
                            startDate = startDate,
                            targetDate = targetDate
                        )
                        onSave(goal)
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Goal Title
                item {
                    Text("Goal Title *", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your goal title", color = TextMuted) },
                        singleLine = true,
                        colors = outlinedColors()
                    )
                }

                // Description
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Description (Optional)", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${description.length}/200", color = TextMuted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { if (it.length <= 200) description = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp),
                        placeholder = { Text("Add more details about your goal", color = TextMuted) },
                        colors = outlinedColors()
                    )
                }

                // Icon Picker
                item {
                    Text("Icon", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GOAL_ICONS) { iconItem ->
                            val isSelected = selectedIcon == iconItem.name
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) PurpleBrand else CardInner)
                                    .border(1.dp, if (isSelected) Color.White else CardBorder, RoundedCornerShape(10.dp))
                                    .clickable { selectedIcon = iconItem.name },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    iconItem.icon,
                                    contentDescription = iconItem.name,
                                    tint = if (isSelected) Color.White else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Color Picker
                item {
                    Text("Color", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GOAL_COLORS) { colorHex ->
                            val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(PurpleBrand)
                            val isSelected = selectedColor.equals(colorHex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorHex }
                            )
                        }
                    }
                }

                // Target Amount and Unit (Side-by-side)
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Target Amount", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = targetAmountInput,
                                onValueChange = { targetAmountInput = it.filter { c -> c.isDigit() || c == '.' } },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("e.g. 30", color = TextMuted) },
                                singleLine = true,
                                colors = outlinedColors()
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Unit (Optional)", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("sessions", color = TextMuted) },
                                singleLine = true,
                                colors = outlinedColors()
                            )
                        }
                    }
                }

                // Start Date
                item {
                    Text("Start Date (Optional)", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("YYYY-MM-DD", color = TextMuted) },
                        trailingIcon = { Icon(Icons.Default.CalendarToday, null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        colors = outlinedColors()
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun QuickProgressDialog(
    goal: PersonalGoal,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var progressInput by remember { mutableStateOf(if (goal.currentAmount % 1.0 == 0.0) goal.currentAmount.toInt().toString() else goal.currentAmount.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = { Text("Update Progress", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(goal.title, color = PurpleBrand, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Current target: ${goal.targetAmount} ${goal.unit}", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = progressInput,
                    onValueChange = { progressInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Logged amount (${goal.unit})") },
                    singleLine = true,
                    colors = outlinedColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = progressInput.toDoubleOrNull() ?: goal.currentAmount
                    onSave(p)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand)
            ) {
                Text("Update", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PurpleBrand,
    unfocusedBorderColor = CardBorder,
    focusedLabelColor = PurpleBrand,
    unfocusedLabelColor = TextMuted,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    cursorColor = PurpleBrand
)
