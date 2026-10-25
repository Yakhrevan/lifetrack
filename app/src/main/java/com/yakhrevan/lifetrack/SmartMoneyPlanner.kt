package com.yakhrevan.lifetrack

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private const val MONEY_PLANNER_PREFS = "lifetrack_money_planner"

@Composable
internal fun MonthlyStatementCard(logs: List<LogEntry>, modifier: Modifier = Modifier) {
    val month = YearMonth.now()
    val range = AnalyticsEngine.DateRange(month.atDay(1), month.atEndOfMonth())
    val income = AnalyticsEngine.aggregate(logs, range, "Income").total
    val expenses = AnalyticsEngine.aggregate(logs, range, "Expense").total
    val net = income - expenses
    val rate = if (income > 0.0) (net / income * 100.0).coerceIn(-100.0, 100.0) else null
    val currency = { value: Double -> "₹" + String.format(Locale.US, "%,.0f", value) }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = LifeTrackColors.Blue)
                Text("MONTHLY STATEMENT · ${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}", color = LifeTrackColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatementMetric("Income", currency(income), LifeTrackColors.Blue, Modifier.weight(1f))
                StatementMetric("Expenses", currency(expenses), LifeTrackColors.Orange, Modifier.weight(1f))
                StatementMetric("Net", currency(net), if (net >= 0) LifeTrackColors.Primary else LifeTrackColors.Orange, Modifier.weight(1f))
            }
            Text("Savings rate", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
            LinearProgressIndicator(
                progress = { ((rate ?: 0.0).coerceAtLeast(0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if ((rate ?: 0.0) >= 0) LifeTrackColors.Primary else LifeTrackColors.Orange,
                trackColor = LifeTrackColors.Background
            )
            Text(rate?.let { String.format(Locale.US, "%.1f%% of income retained", it) } ?: "Add income transactions to calculate the savings rate.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
            Text("Six-month savings-rate trend", color = LifeTrackColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            val trendMonths = (5 downTo 0).map { monthOffset ->
                val monthItem = month.minusMonths(monthOffset.toLong())
                val monthRange = AnalyticsEngine.DateRange(monthItem.atDay(1), monthItem.atEndOfMonth())
                val monthIncome = AnalyticsEngine.aggregate(logs, monthRange, "Income").total
                val monthExpense = AnalyticsEngine.aggregate(logs, monthRange, "Expense").total
                val monthRate = if (monthIncome > 0.0) ((monthIncome - monthExpense) / monthIncome * 100.0).coerceIn(-100.0, 100.0) else null
                Triple(monthItem, monthRate, monthIncome)
            }
            SavingsRateTrendChart(rates = trendMonths.map { it.second })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                trendMonths.forEach { (monthItem, monthRate, _) ->
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(monthItem.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, color = LifeTrackColors.TextSecondary, fontSize = 9.sp)
                        Text(monthRate?.let { String.format(Locale.US, "%.0f%%", it) } ?: "—", color = LifeTrackColors.TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("Calculated from saved transactions. A missing income baseline is shown as —, not 0%.", color = LifeTrackColors.TextSecondary, fontSize = 10.sp)
            Text("Calculated from saved transactions dated in the current calendar month.", color = LifeTrackColors.TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun StatementMetric(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = LifeTrackColors.TextSecondary, fontSize = 10.sp)
        Text(value, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SmartMoneyPlanner(logs: List<LogEntry>, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(MONEY_PLANNER_PREFS, Context.MODE_PRIVATE) }
    var refresh by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Expense") }
    var frequency by remember { mutableStateOf("Monthly") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedGoalIndex by remember { mutableStateOf(-1) }
    val recurring = remember(refresh) { readRows(prefs.getString("recurring", "[]")) }
    val bills = remember(refresh) { readRows(prefs.getString("bills", "[]")) }
    val goals = remember(refresh) { readRows(prefs.getString("savings_goals", "[]")) }
    val currency = { value: Double -> "₹" + String.format(Locale.US, "%,.0f", value) }

    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = LifeTrackColors.Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("SMART MONEY PLANNER", color = LifeTrackColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Saved on this device. Recurring items are planning templates; they do not automatically create transactions.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = { dialog = "recurring"; title = ""; amount = ""; detail = ""; error = null }) {
                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp)); Text("Recurring", fontSize = 11.sp)
                }
                Button(onClick = { dialog = "bill"; title = ""; amount = ""; detail = LocalDate.now().toString(); error = null }) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp)); Text("Add bill", fontSize = 11.sp)
                }
                Button(onClick = { dialog = "goal"; title = ""; amount = ""; detail = "0"; error = null }) {
                    Icon(Icons.Default.Savings, contentDescription = null, modifier = Modifier.size(16.dp)); Text("Savings goal", fontSize = 11.sp)
                }
            }
            Text("Recurring income & expenses", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (recurring.isEmpty()) Text("No recurring items added yet.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
            recurring.forEachIndexed { index, row ->
                PlannerRow("${row.optString("title")} · ${row.optString("kind")}", "${currency(row.optDouble("amount"))} / ${row.optString("frequency")}", onDelete = {
                    val next = JSONArray()
                    recurring.forEachIndexed { i, item -> if (i != index) next.put(item) }
                    prefs.edit().putString("recurring", next.toString()).apply(); refresh = refresh + 1
                })
            }
            Text("Upcoming bills", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            val today = LocalDate.now()
            val upcoming = bills.sortedBy { it.optString("dueDate") }
            if (upcoming.isEmpty()) Text("No bills or due dates saved.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
            upcoming.forEachIndexed { index, row ->
                val due = runCatching { LocalDate.parse(row.optString("dueDate")) }.getOrNull()
                val status = when {
                    due == null -> "Invalid due date"
                    due.isBefore(today) -> "Overdue"
                    due == today -> "Due today"
                    due <= today.plusDays(7) -> "Due in ${java.time.temporal.ChronoUnit.DAYS.between(today, due)} days"
                    else -> "Due ${due}"
                }
                PlannerRow(row.optString("title"), "${currency(row.optDouble("amount"))} · $status", alert = due != null && !due.isAfter(today.plusDays(7)), onDelete = {
                    val next = JSONArray()
                    bills.forEachIndexed { i, item -> if (i != index) next.put(item) }
                    prefs.edit().putString("bills", next.toString()).apply(); refresh = refresh + 1
                })
            }
            Text("Savings goals", color = LifeTrackColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (goals.isEmpty()) Text("Create a goal to track how much you have saved.", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
            goals.forEachIndexed { index, row ->
                val target = row.optDouble("target").coerceAtLeast(1.0)
                val saved = row.optDouble("saved").coerceAtLeast(0.0)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(row.optString("title"), color = LifeTrackColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${(saved / target * 100).toInt().coerceIn(0, 100)}%", color = LifeTrackColors.Primary, fontSize = 11.sp)
                    }
                    LinearProgressIndicator(progress = { (saved / target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = LifeTrackColors.Primary, trackColor = LifeTrackColors.Background)
                    Text("${currency(saved)} saved of ${currency(target)}", color = LifeTrackColors.TextSecondary, fontSize = 11.sp)
                    Row {
                        TextButton(onClick = { dialog = "update_goal"; title = row.optString("title"); amount = target.toString(); detail = saved.toString(); error = null; selectedGoalIndex = index }) { Text("Update saved") }
                        TextButton(onClick = {
                            val next = JSONArray(); goals.forEachIndexed { i, item -> if (i != index) next.put(item) }
                            prefs.edit().putString("savings_goals", next.toString()).apply(); refresh = refresh + 1
                        }) { Text("Remove") }
                    }
                }
            }
        }
    }

    if (dialog.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { dialog = ""; error = null },
            title = { Text(when(dialog) { "recurring" -> "Recurring income or expense"; "bill" -> "Upcoming bill"; "goal" -> "Savings goal"; else -> "Update savings progress" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it; error = null }, label = { Text(if (dialog == "goal" || dialog == "update_goal") "Goal name" else "Name / description") }, singleLine = true)
                    if (dialog == "recurring") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Income", "Expense").forEach { option -> FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Weekly", "Monthly", "Yearly").forEach { option -> FilterChip(selected = frequency == option, onClick = { frequency = option }, label = { Text(option) }) }
                        }
                    }
                    OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' }; error = null }, label = { Text(if (dialog == "goal" || dialog == "update_goal") "Target amount (₹)" else "Amount (₹)") }, singleLine = true)
                    if (dialog == "bill") OutlinedTextField(value = detail, onValueChange = { detail = it.take(10); error = null }, label = { Text("Due date · YYYY-MM-DD") }, singleLine = true)
                    if (dialog == "goal" || dialog == "update_goal") OutlinedTextField(value = detail, onValueChange = { detail = it.filter { c -> c.isDigit() || c == '.' }; error = null }, label = { Text("Already saved (₹)") }, singleLine = true)
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val numeric = amount.toDoubleOrNull()
                    if (title.isBlank()) { error = "Enter a name."; return@Button }
                    if (numeric == null || !numeric.isFinite() || numeric <= 0.0 || numeric > MoneyAmounts.MAX_ENTRY_AMOUNT) { error = "Enter a valid positive amount."; return@Button }
                    when(dialog) {
                        "recurring" -> {
                            val next = JSONArray(prefs.getString("recurring", "[]"))
                            next.put(JSONObject().put("title", title.trim()).put("amount", numeric).put("kind", kind).put("frequency", frequency))
                            prefs.edit().putString("recurring", next.toString()).apply()
                        }
                        "bill" -> {
                            val due = runCatching { LocalDate.parse(detail) }.getOrNull()
                            if (due == null) { error = "Use a valid YYYY-MM-DD due date."; return@Button }
                            val next = JSONArray(prefs.getString("bills", "[]"))
                            next.put(JSONObject().put("title", title.trim()).put("amount", numeric).put("dueDate", due.toString()))
                            prefs.edit().putString("bills", next.toString()).apply()
                        }
                        "goal" -> {
                            val saved = detail.toDoubleOrNull() ?: 0.0
                            if (!saved.isFinite() || saved < 0.0 || saved > numeric) { error = "Saved amount must be between ₹0 and the target."; return@Button }
                            val next = JSONArray(prefs.getString("savings_goals", "[]"))
                            next.put(JSONObject().put("title", title.trim()).put("target", numeric).put("saved", saved))
                            prefs.edit().putString("savings_goals", next.toString()).apply()
                        }
                        "update_goal" -> {
                            val saved = detail.toDoubleOrNull()
                            if (saved == null || !saved.isFinite() || saved < 0.0 || saved > numeric) { error = "Saved amount must be between ₹0 and the target."; return@Button }
                            val next = JSONArray()
                            goals.forEachIndexed { i, item -> next.put(if (i == selectedGoalIndex) JSONObject(item.toString()).put("saved", saved) else item) }
                            prefs.edit().putString("savings_goals", next.toString()).apply()
                        }
                    }
                    dialog = ""; error = null; refresh = refresh + 1
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { dialog = ""; error = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PlannerRow(title: String, subtitle: String, alert: Boolean = false, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = LifeTrackColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = if (alert) LifeTrackColors.Orange else LifeTrackColors.TextSecondary, fontSize = 11.sp)
        }
        TextButton(onClick = onDelete) { Text("Remove") }
    }
}

private fun readRows(raw: String?): List<JSONObject> {
    return runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
    }.getOrDefault(emptyList())
}

@Composable
private fun SavingsRateTrendChart(rates: List<Double?>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(112.dp)) {
        if (rates.isEmpty()) return@Canvas
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val zeroY = top + (bottom - top) / 2f
        drawLine(LifeTrackColors.TextSecondary.copy(alpha = 0.35f), Offset(left, zeroY), Offset(right, zeroY), strokeWidth = 1.dp.toPx())
        val step = if (rates.size <= 1) 0f else (right - left) / (rates.size - 1)
        val points = rates.mapIndexed { index, rate ->
            rate?.let { Offset(left + step * index, bottom - (((it.coerceIn(-100.0, 100.0) + 100.0) / 200.0).toFloat() * (bottom - top))) }
        }
        var segment = Path()
        var hasPoint = false
        points.forEach { point ->
            if (point == null) {
                if (hasPoint) drawPath(segment, LifeTrackColors.Primary, style = Stroke(width = 2.5.dp.toPx()))
                segment = Path(); hasPoint = false
            } else {
                if (!hasPoint) { segment.moveTo(point.x, point.y); hasPoint = true } else segment.lineTo(point.x, point.y)
                drawCircle(LifeTrackColors.Primary, radius = 3.dp.toPx(), center = point)
            }
        }
        if (hasPoint) drawPath(segment, LifeTrackColors.Primary, style = Stroke(width = 2.5.dp.toPx()))
    }
}
