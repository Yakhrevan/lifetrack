package com.yakhrevan.lifetrack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun GoalsTargetsScreen(
    logs: List<LogEntry>,
    studyGoal: Int,
    waterGoal: Int,
    onEditGoals: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val budgetPreferences = remember { context.getSharedPreferences("lifetrack_money", android.content.Context.MODE_PRIVATE) }
    val monthlyBudget = budgetPreferences.getString("monthly_expense_budget", "")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val daily = logs.filter { it.date == today }
    val study = daily.filter { it.category == "Study" }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val water = daily.filter { it.category == "Water" }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val spent = daily.filter { it.category == "Expense" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    val monthExpenses = logs.filter { it.category == "Expense" && it.date.startsWith(month) }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val monthStudy = logs.filter { it.category == "Study" && it.date.startsWith(month) }.sumOf { it.amount.toIntOrNull() ?: 0 }
    val daysInCurrentMonth = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
    val monthlyStudyTarget = (studyGoal.coerceAtLeast(1) * daysInCurrentMonth).toFloat()
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Goals & Targets", color = LifeTrackColors.TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Progress comes from your saved entries.", color = LifeTrackColors.TextSecondary, fontSize = 13.sp)
        GoalProgressCard("Daily study", if (study >= 60) "${study / 60}h ${study % 60}m" else "${study} min", "$studyGoal min target", study.toFloat(), studyGoal.toFloat(), Color(0xFF315CFF), Icons.Default.MenuBook)
        GoalProgressCard("Daily water", "${(water / 1000f).let { String.format(Locale.US, "%.1f", it) }} L", "$waterGoal ml target", water.toFloat(), waterGoal.toFloat(), Color(0xFF2498F3), Icons.Default.WaterDrop)
        Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Remaining today", color = LifeTrackColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (study >= studyGoal) "Study target complete" else "${(studyGoal - study).coerceAtLeast(0)} min left", color = if (study >= studyGoal) Color(0xFF20B982) else LifeTrackColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Study", color = LifeTrackColors.TextSecondary, fontSize = 10.sp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (water >= waterGoal) "Water target complete" else "${(waterGoal - water).coerceAtLeast(0)} ml left", color = if (water >= waterGoal) Color(0xFF20B982) else LifeTrackColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Water", color = LifeTrackColors.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
        GoalProgressCard("Monthly study", "${monthStudy / 60}h ${monthStudy % 60}m", "${studyGoal} min/day × $daysInCurrentMonth days", monthStudy.toFloat(), monthlyStudyTarget, Color(0xFF8B5CF6), Icons.Default.MenuBook)
        if (monthlyBudget != null) {
            GoalProgressCard("Monthly spending", "₹${String.format(Locale.US, "%.0f", monthExpenses)}", "of ₹${String.format(Locale.US, "%.0f", monthlyBudget)} budget", monthExpenses.toFloat(), monthlyBudget.toFloat(), Color(0xFFFF9B43), Icons.Default.Flag)
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Monthly spending", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("₹${String.format(Locale.US, "%.0f", monthExpenses)} spent this month", color = LifeTrackColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Set a real monthly budget in Money Management to track progress.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Today's consistency", color = LifeTrackColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("${daily.size} saved tracker entries today.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
                Text("Today's expenses: ₹${String.format(Locale.US, "%.0f", spent)}", color = LifeTrackColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Progress is calculated from your saved activity and configured budget.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
            }
        }
        val activeDates = logs.map { it.date }.toSet()
        val currentStreak = run {
            val cal = Calendar.getInstance()
            if (SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time) !in activeDates) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            var count = 0
            while (count < 366) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                if (date !in activeDates) break
                count++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            count
        }
        val activeDaysThisWeek = (0..6).count { offset ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time) in activeDates
        }
        Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Consistency & streaks", color = LifeTrackColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$currentStreak day" + (if (currentStreak == 1) "" else "s"), color = LifeTrackColors.Primary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Current activity streak", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$activeDaysThisWeek / 7", color = LifeTrackColors.Blue, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Active days this week", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (6 downTo 0).forEach { offset ->
                        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                        val active = date in activeDates
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.fillMaxWidth().height(25.dp).background(if (active) LifeTrackColors.Primary else LifeTrackColors.TextSecondary.copy(alpha = .12f), RoundedCornerShape(6.dp)))
                            Text(SimpleDateFormat("EE", Locale.US).format(cal.time), color = LifeTrackColors.TextSecondary, fontSize = 9.sp)
                        }
                    }
                }
                Text("Activity is calculated from dates with at least one saved entry.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
            }
        }
        OutlinedButton(onClick = onEditGoals, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Edit daily study and water targets") }
    }
}

@Composable
private fun GoalProgressCard(title: String, value: String, target: String, current: Float, goal: Float, tint: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(icon, null, tint = tint)
                Text(title, color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                GoalRing(progress = if (goal > 0) current / goal else 0f, label = "${if (goal > 0) (current / goal * 100).toInt().coerceAtMost(100) else 0}%", tint = tint)
            }
            Text(value, color = LifeTrackColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { if (goal > 0) (current / goal).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(7.dp), color = tint, trackColor = tint.copy(alpha = .12f))
            Text(target, color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GoalRing(progress: Float, label: String, tint: Color) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            drawArc(LifeTrackColors.TextSecondary.copy(alpha = .14f), -90f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawArc(tint, -90f, 360f * progress.coerceAtLeast(0f).coerceAtMost(1f), false, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
