package com.yakhrevan.lifetrack

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors

data class LauncherItem(
    val label: String,
    val icon: ImageVector,
    val bgColor: Color,
    val destination: String
)

@Composable
fun AppLauncherPanel(
    visible: Boolean,
    currentPage: String,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val launcherPrefs = remember { context.getSharedPreferences("lifetrack_launcher", Context.MODE_PRIVATE) }
    var isEditing by remember { mutableStateOf(false) }
    var hiddenLabels by remember {
        mutableStateOf(launcherPrefs.getStringSet("hidden_shortcuts", emptySet())?.toSet() ?: emptySet())
    }

    val items = remember {
        listOf(
            // Row 1
            LauncherItem("Habits",      Icons.Default.DateRange,            Color(0xFF00C896), "Habits"),
            LauncherItem("Routines",    Icons.Default.WbSunny,              Color(0xFFF59E0B), "Routines"),
            LauncherItem("Screen Time", Icons.Default.PhoneAndroid,         Color(0xFF3B82F6), "Digital Wellbeing"),
            LauncherItem("Notes",       Icons.Default.Note,                 Color(0xFF8B5CF6), "Notes"),

            // Row 2
            LauncherItem("Goals",       Icons.Default.Flag,                 Color(0xFFEC4899), "Goals"),
            LauncherItem("Health",      Icons.Default.Favorite,             Color(0xFFEF4444), "Habits & Routines"),
            LauncherItem("Tasks",       Icons.Default.CheckCircle,          Color(0xFF10B981), "Tasks"),
            LauncherItem("Calendar",    Icons.Default.CalendarMonth,        Color(0xFFF97316), "Calendar"),

            // Row 3
            LauncherItem("Study",       Icons.Default.MenuBook,             Color(0xFF14B8A6), "Study"),
            LauncherItem("Water",       Icons.Default.WaterDrop,            Color(0xFF06B6D4), "Water"),

            // Row 4
            LauncherItem("Insights",    Icons.Default.BarChart,             Color(0xFF38BDF8), "Insights"),
            LauncherItem("Money",       Icons.Default.AccountBalanceWallet, Color(0xFFE11D48), "Money"),
            LauncherItem("Profile",     Icons.Default.Person,               Color(0xFFA855F7), "Profile"),
            LauncherItem("Voice Bot",   Icons.Default.Mic,                  Color(0xFF6366F1), "Voice Bot"),

            // Row 5
            LauncherItem("Backup",      Icons.Default.CloudSync,            Color(0xFF06B6D4), "Backup & Sync"),
            LauncherItem("Settings",    Icons.Default.Settings,             Color(0xFF64748B), "Settings")
        )
    }

    val displayedItems = if (isEditing) items else items.filterNot { it.label in hiddenLabels }

    // Scrim
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss)
        )
    }

    // Launcher Panel (Slide up from bottom-right)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(240)) { it / 3 } + scaleIn(tween(240), initialScale = 0.9f),
        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 3 } + scaleOut(tween(180), targetScale = 0.9f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 86.dp, end = 12.dp, start = 12.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .clip(RoundedCornerShape(24.dp)),
                color = Color(0xF20F172A),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shadowElevation = 18.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top Drag Handle
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.25f), CircleShape)
                    )

                    // 4-Column Grid of Apps
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        items(displayedItems) { item ->
                            val isSelected = item.destination == currentPage
                            val isHidden = item.label in hiddenLabels
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (isEditing) {
                                            val updated = if (isHidden) hiddenLabels - item.label else hiddenLabels + item.label
                                            hiddenLabels = updated
                                            launcherPrefs.edit().putStringSet("hidden_shortcuts", updated).apply()
                                        } else {
                                            onNavigate(item.destination)
                                            onDismiss()
                                        }
                                    }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(
                                            item.bgColor.copy(alpha = if (isHidden) 0.05f else if (isSelected) 0.35f else 0.18f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isEditing) (if (isHidden) Color(0xFF64748B) else item.bgColor) else if (isSelected) item.bgColor else Color.Transparent,
                                            RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        item.icon,
                                        contentDescription = item.label,
                                        tint = if (isHidden) Color(0xFF64748B) else item.bgColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Text(
                                    item.label + if (isEditing && isHidden) " · hidden" else "",
                                    color = if (isHidden) Color(0xFF94A3B8) else Color(0xFFF1F5F9),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Edit mode lets the user show/hide launcher shortcuts. Changes save immediately.
                    // Bottom Edit shortcut row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable {
                                if (isEditing) {
                                    isEditing = false
                                } else {
                                    isEditing = true
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(if (isEditing) "Done" else "Edit", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}
