package com.yakhrevan.lifetrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale

@Composable
internal fun WeeklyComparisonCard(logs: List<LogEntry>, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val thisWeek = AnalyticsEngine.DateRange(thisWeekStart, today)
    val previousWeek = AnalyticsEngine.DateRange(thisWeekStart.minusDays(7), thisWeekStart.minusDays(1))
    val metrics = listOf("Study", "Water")
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("This week vs last week", color = LifeTrackColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("${thisWeekStart} – ${today}", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
            metrics.forEach { category ->
                val current = AnalyticsEngine.aggregate(logs, thisWeek, category).total
                val previous = AnalyticsEngine.aggregate(logs, previousWeek, category).total
                val diff = current - previous
                val unit = if (category == "Study") "min" else "ml"
                val label = when {
                    previous == 0.0 && current == 0.0 -> "No activity recorded"
                    previous == 0.0 -> "${formatValue(current, unit)} · no previous baseline"
                    else -> "${if (diff > 0) "+" else ""}${formatValue(diff, unit)} (${AnalyticsEngine.formatPercent(AnalyticsEngine.compare(current, previous).percentageChange)})"
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(category, color = LifeTrackColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(label, color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                    }
                    Text(formatValue(current, unit), color = LifeTrackColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Current week is Monday through today; last week uses the matching full seven-day period.", color = LifeTrackColors.TextSecondary, fontSize = 10.sp)
        }
    }
}

private fun formatValue(value: Double, unit: String): String {
    val number = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
    return if (unit == "₹") "₹$number" else "$number $unit"
}
