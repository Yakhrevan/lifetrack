package com.yakhrevan.lifetrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import java.time.LocalDate

@Composable
internal fun DailyTargetsBriefing(logs: List<LogEntry>, studyGoal: Int, waterGoal: Int, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val todayRange = AnalyticsEngine.DateRange(today, today)
    val yesterdayRange = AnalyticsEngine.DateRange(today.minusDays(1), today.minusDays(1))
    val todayLogs = logs.filter { AnalyticsEngine.parseDate(it.date) == today }
    val study = AnalyticsEngine.aggregate(logs, todayRange, "Study").total.toInt()
    val water = AnalyticsEngine.aggregate(logs, todayRange, "Water").total.toInt()
    val yesterdayStudy = AnalyticsEngine.aggregate(logs, yesterdayRange, "Study").total
    val yesterdayWater = AnalyticsEngine.aggregate(logs, yesterdayRange, "Water").total
    val studyTarget = studyGoal.coerceAtLeast(1)
    val waterTarget = waterGoal.coerceAtLeast(1)

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Daily briefing", color = LifeTrackColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(if (todayLogs.isEmpty()) "No entries saved today yet. Your targets are ready when you are." else "${todayLogs.size} saved entr${if (todayLogs.size == 1) "y" else "ies"} today.", color = LifeTrackColors.TextSecondary, fontSize = 12.sp)
            TargetRow("Study", "${study} / ${studyTarget} min", if (study >= studyTarget) "Target complete" else "${studyTarget - study} min left", study, studyTarget, LifeTrackColors.Blue)
            TargetRow("Water", "${water} / ${waterTarget} ml", if (water >= waterTarget) "Target complete" else "${waterTarget - water} ml left", water, waterTarget, LifeTrackColors.Primary)
            Text("Yesterday comparison", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Study", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                    Text(changeLabel(study.toDouble(), yesterdayStudy, "min"), color = LifeTrackColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Water", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                    Text(changeLabel(water.toDouble(), yesterdayWater, "ml"), color = LifeTrackColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TargetRow(title: String, value: String, remaining: String, current: Int, target: Int, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = LifeTrackColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(remaining, color = if (current >= target) LifeTrackColors.Primary else LifeTrackColors.TextSecondary, fontSize = 11.sp)
        }
        LinearProgressIndicator(progress = { (current.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = tint, trackColor = tint.copy(alpha = .14f))
        Text(value, color = LifeTrackColors.TextSecondary, fontSize = 10.sp)
    }
}

private fun changeLabel(current: Double, previous: Double, unit: String): String {
    val change = current - previous
    val formatted = if (change % 1.0 == 0.0) change.toInt().toString() else String.format(java.util.Locale.US, "%.1f", change)
    return when {
        previous == 0.0 && current == 0.0 -> "No activity"
        previous == 0.0 -> "+$formatted $unit · no baseline"
        change > 0 -> "+$formatted $unit"
        change < 0 -> "$formatted $unit"
        else -> "No change"
    }
}
