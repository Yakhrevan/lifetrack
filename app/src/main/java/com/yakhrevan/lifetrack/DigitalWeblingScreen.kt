package com.yakhrevan.lifetrack

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Digital Webling is a lightweight virtual companion. XP and interactions are saved locally;
 * missions reflect real LifeTrack entries and never create fake tracker records.
 */
@Composable
fun DigitalWeblingScreen(logs: List<LogEntry>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lifetrack_webling", Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    var bonusXp by remember(refresh) { mutableIntStateOf(prefs.getInt("bonus_xp", 0)) }
    var petMood by remember(refresh) { mutableStateOf(prefs.getString("pet_mood", "Happy") ?: "Happy") }
    var petName by remember(refresh) { mutableStateOf(prefs.getString("pet_name", "Webling") ?: "Webling") }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val todays = logs.filter { it.date == today }
    val studyMinutes = todays.filter { it.category == "Study" }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val waterMl = todays.filter { it.category == "Water" }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val expenses = todays.count { it.category == "Expense" }
    val habits = todays.count { it.category == "Care" || it.category == "Face routine" }
    val earnedFromLogs = (logs.size * 10).coerceAtLeast(0)
    val xp = earnedFromLogs + bonusXp
    val level = xp / 500 + 1
    val levelProgress = (xp % 500) / 500f

    fun interact(action: String, xpReward: Int) {
        val nextXp = bonusXp + xpReward
        prefs.edit().putInt("bonus_xp", nextXp).putString("pet_mood", action).apply()
        refresh++
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Digital Webling", color = LifeTrackColors.TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("A tiny companion that grows with your real-life progress.", color = LifeTrackColors.TextSecondary, fontSize = 13.sp)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2FF)),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("YOUR COMPANION", color = Color(0xFF315CFF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                        Text("LV. $level", color = Color(0xFF315CFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.fillMaxWidth().height(150.dp).background(
                            Color(0xFFD9E9FF), RoundedCornerShape(22.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🐱", fontSize = 88.sp)
                        Text(
                            when (petMood) { "Fed" -> "✨"; "Played" -> "🎉"; else -> "💙" },
                            fontSize = 26.sp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(18.dp)
                        )
                    }
                    Text(petName, color = LifeTrackColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (petMood) { "Fed" -> "Your Webling feels cared for."; "Played" -> "Your Webling had fun with you!"; else -> "Small steps make a happier Webling." },
                        color = LifeTrackColors.TextSecondary, fontSize = 12.sp
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { interact("Fed", 5) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF315CFF)),
                            shape = RoundedCornerShape(14.dp)
                        ) { androidx.compose.material3.Icon(Icons.Default.Restaurant, null); Spacer(Modifier.width(6.dp)); Text("Feed +5 XP") }
                        OutlinedButton(
                            onClick = { interact("Played", 5) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) { androidx.compose.material3.Icon(Icons.Default.SportsEsports, null); Spacer(Modifier.width(6.dp)); Text("Play +5 XP") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$xp XP", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${500 - (xp % 500)} XP to next level", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                    }
                    LinearProgressIndicator(
                        progress = { levelProgress },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = Color(0xFF315CFF),
                        trackColor = Color(0xFFD2DFFF)
                    )
                }
            }
        }
        item {
            Text("Today's missions", color = LifeTrackColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Missions are calculated from your saved LifeTrack entries.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
        }
        item {
            val missions = listOf(
                Triple("Complete a study session", studyMinutes > 0, "+20 XP"),
                Triple("Log your water intake", waterMl > 0, "+10 XP"),
                Triple("Record an expense", expenses > 0, "+10 XP"),
                Triple("Complete a habit or routine", habits > 0, "+15 XP")
            )
            Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    missions.forEach { (title, done, reward) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            androidx.compose.material3.Icon(
                                if (done) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (done) LifeTrackColors.Green else LifeTrackColors.Purple
                            )
                            Column(Modifier.weight(1f)) {
                                Text(title, color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(if (done) "Completed from today's activity" else "Log an entry to complete this mission", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                            }
                            Text(if (done) "Done" else reward, color = if (done) LifeTrackColors.Green else LifeTrackColors.Purple, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            Text("Your daily impact", color = LifeTrackColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeblingStat("Study", if (studyMinutes >= 60) "${studyMinutes / 60}h ${studyMinutes % 60}m" else "${studyMinutes}m", Color(0xFF315CFF), Modifier.weight(1f))
                WeblingStat("Water", "${(waterMl / 250)} glasses", Color(0xFF2498F3), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeblingStat("Habits", "$habits done", Color(0xFF20B982), Modifier.weight(1f))
                WeblingStat("Entries", "${todays.size} today", Color(0xFF8B5CF6), Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Icon(Icons.Default.Pets, null, tint = LifeTrackColors.Purple)
                    Column(Modifier.weight(1f)) {
                        Text("How Webling works", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Your saved entries drive mission status. Feeding and playing save locally. No cloud account required.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeblingStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.Icon(Icons.Default.Favorite, null, tint = tint, modifier = Modifier.size(16.dp))
                Text(label, color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
            }
            Text(value, color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}
