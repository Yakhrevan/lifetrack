package com.yakhrevan.lifetrack

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ==========================================
// 1. ACTION REGISTRY
// ==========================================

data class QuickAddAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val categoryOrRoute: String,
    val isPrimaryRadial: Boolean = true
)

object QuickAddActionRegistry {
    val PRIMARY_ACTIONS = listOf(
        QuickAddAction(
            id = "task",
            title = "Task",
            subtitle = "Add to-do item",
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF8B5CF6),
            categoryOrRoute = "Tasks"
        ),
        QuickAddAction(
            id = "note",
            title = "Note",
            subtitle = "Capture ideas",
            icon = Icons.Default.Note,
            color = Color(0xFFA855F7),
            categoryOrRoute = "Notes"
        ),
        QuickAddAction(
            id = "money",
            title = "Money",
            subtitle = "Log transaction",
            icon = Icons.Default.AccountBalanceWallet,
            color = Color(0xFFF43F5E),
            categoryOrRoute = "Expense"
        ),
        QuickAddAction(
            id = "water",
            title = "Water",
            subtitle = "Quick hydration",
            icon = Icons.Default.WaterDrop,
            color = Color(0xFF00B4D8),
            categoryOrRoute = "Water"
        ),
        QuickAddAction(
            id = "focus",
            title = "Focus",
            subtitle = "Start session",
            icon = Icons.Default.Timer,
            color = Color(0xFF6366F1),
            categoryOrRoute = "Study"
        ),
        QuickAddAction(
            id = "habit",
            title = "Habit",
            subtitle = "Complete routine",
            icon = Icons.Default.Favorite,
            color = Color(0xFFEC4899),
            categoryOrRoute = "Habit"
        ),
        QuickAddAction(
            id = "calendar",
            title = "Event",
            subtitle = "Calendar schedule",
            icon = Icons.Default.CalendarMonth,
            color = Color(0xFF38BDF8),
            categoryOrRoute = "Calendar"
        )
    )

    val SECONDARY_ACTIONS = listOf(
        QuickAddAction("study_session", "Study", "Study session", Icons.Default.MenuBook, Color(0xFF00E5A3), "Study", false),
        QuickAddAction("webling", "Webling", "Companion pet", Icons.Default.Pets, Color(0xFF10B981), "Webling", false),
        QuickAddAction("more_tools", "Explore All", "All LifeTrack tools", Icons.Default.AutoAwesome, Color(0xFF8B5CF6), "More", false)
    )

    fun getAllActions(): List<QuickAddAction> = PRIMARY_ACTIONS + SECONDARY_ACTIONS
}

// ==========================================
// 2. CIRCULAR QUICK ADD MENU
// ==========================================

@Composable
fun QuickAddMenu(
    visible: Boolean,
    onSelect: (QuickAddAction) -> Unit,
    onDismiss: () -> Unit
) {
    var showMoreActionsSheet by remember { mutableStateOf(false) }

    val animProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.78f, // smooth organic easing, non-oscillating
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "circular_quick_add_expand"
    )

    // Close secondary sheet if menu closed
    LaunchedEffect(visible) {
        if (!visible) showMoreActionsSheet = false
    }

    if (visible || animProgress > 0.01f) {
        val density = LocalDensity.current
        // Arrange quick actions around a complete circle, keeping the menu above bottom navigation.
        val radiusPx = with(density) { 112.dp.toPx() } * animProgress
        val primaryActions = QuickAddActionRegistry.PRIMARY_ACTIONS
        val stepAngle = 360.0 / primaryActions.size

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f * animProgress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Circular action cluster is lifted clear of the bottom navigation.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 230.dp),
                contentAlignment = Alignment.Center
            ) {
                // Subtle spoke connector lines
                Canvas(
                    modifier = Modifier.size(280.dp)
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    primaryActions.forEachIndexed { index, action ->
                        val angleDeg = -90.0 + (index * stepAngle)
                        val rad = angleDeg * (PI / 180.0)
                        val end = Offset(
                            (center.x + radiusPx * cos(rad)).toFloat(),
                            (center.y + radiusPx * sin(rad)).toFloat()
                        )
                        drawLine(
                            color = action.color.copy(alpha = 0.35f * animProgress),
                            start = center,
                            end = end,
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }

                // Primary Circular Action Items
                primaryActions.forEachIndexed { index, action ->
                    val angleDeg = -90.0 + (index * stepAngle)
                    val rad = angleDeg * (PI / 180.0)
                    val offsetX = (radiusPx * cos(rad)).roundToInt()
                    val offsetY = (radiusPx * sin(rad)).roundToInt()
                    val itemRotation = (1f - animProgress) * -25f

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset { IntOffset(offsetX, offsetY) }
                            .scale(animProgress)
                            .alpha(animProgress.coerceIn(0f, 1f))
                            .graphicsLayer { rotationZ = itemRotation }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onSelect(action)
                            }
                            .padding(4.dp)
                    ) {
                        // Colored circular icon button
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(action.color, action.color.copy(alpha = 0.8f))
                                    )
                                )
                                .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                action.icon,
                                contentDescription = action.title,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.height(3.dp))

                        // High contrast pill label
                        Surface(
                            color = Color(0xFF0F172A).copy(alpha = 0.95f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Text(
                                action.title,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Center hub: "More Actions"
                Surface(
                    color = Color(0xFF1E1B4B).copy(alpha = 0.95f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .scale(animProgress)
                        .alpha(animProgress.coerceIn(0f, 1f))
                        .offset(y = (-18).dp)
                        .clickable { showMoreActionsSheet = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = "More actions",
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "More",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Secondary Actions Sheet (when "More" is tapped)
            if (showMoreActionsSheet) {
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 70.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "More Quick Actions",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showMoreActionsSheet = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            QuickAddActionRegistry.SECONDARY_ACTIONS.forEach { action ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            showMoreActionsSheet = false
                                            onSelect(action)
                                        }
                                        .padding(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(action.color.copy(alpha = 0.2f))
                                            .border(1.dp, action.color.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(action.icon, null, tint = action.color, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        action.title,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
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
