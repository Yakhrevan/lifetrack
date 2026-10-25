package com.yakhrevan.lifetrack

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

// Design Tokens matching Reference Screens
private val DarkBg         = Color(0xFF070B14)
private val CardBg         = Color(0xFF0E1626)
private val CardInner      = Color(0xFF141F33)
private val CardBorder     = Color(0x1AFFFFFF)
private val TextWhite      = Color(0xFFF1F5F9)
private val TextMuted      = Color(0xFF8896AB)
private val CyanBrand      = Color(0xFF38BDF8)
private val EmeraldIncome  = Color(0xFF10B981)
private val CoralExpense   = Color(0xFFEF4444)
private val PurpleAccent   = Color(0xFFA855F7)
private val VioletTab      = Color(0xFF6366F1)
private val GoldAccent     = Color(0xFFF59E0B)

@Composable
fun MoneyScreen(
    logs: List<LogEntry>,
    onSaveTransaction: (LogEntry) -> Unit = {},
    onDeleteTransaction: (LogEntry) -> Unit = {},
    onAdd: (String) -> Unit = {},
    onEdit: (LogEntry) -> Unit = {},
    onDelete: (LogEntry) -> Unit = {},
    onNavigateHome: () -> Unit = {},
    initialTab: Int = 0,
    initialShowCategories: Boolean = false,
    initialQuickAddType: String? = null,
    onResetQuickAddType: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        MoneyCategory.load(context)
    }

    // Main navigation tabs: 0 = Overview, 1 = Analytics, 2 = Budgets
    var selectedMainTab by remember { mutableIntStateOf(initialTab) }

    // Sub-screens & Dialog states
    var showQuickAdd by remember { mutableStateOf(initialQuickAddType != null) }
    var quickAddType by remember { mutableStateOf(initialQuickAddType ?: "Expense") }

    LaunchedEffect(initialQuickAddType) {
        if (initialQuickAddType != null) {
            quickAddType = initialQuickAddType
            showQuickAdd = true
        }
    }
    var editingTransaction by remember { mutableStateOf<LogEntry?>(null) }
    var showCategoryManagement by remember { mutableStateOf(initialShowCategories) }
    var showAddEditCategory by remember { mutableStateOf(false) }
    var editingCategoryName by remember { mutableStateOf<String?>(null) }
    var selectedCategoryForDetail by remember { mutableStateOf<String?>(null) }
    var showTransactionHistory by remember { mutableStateOf(false) }

    // Filter states. Overview intentionally starts at Overall for every new screen session.
    val currentMonthKey = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }
    var selectedMonth by remember { mutableStateOf(currentMonthKey) }
    var budgetRefreshKey by remember { mutableIntStateOf(0) }
    var overviewPeriodFilter by remember { mutableStateOf("Overall") } // Overall, This Week, This Month, Last Month
    var analyticsTypeFilter by remember { mutableStateOf("Expense") } // "Expense", "Income", "Savings"
    var timeComparisonTab by remember { mutableStateOf("Week") } // "Week", "Month", "Year", "Custom"
    var budgetsSubTab by remember { mutableStateOf("Category Budgets") } // "Category Budgets", "Overall Budget"

    // SharedPreferences for budgets
    val budgetPrefs = remember { context.getSharedPreferences("lifetrack_money", Context.MODE_PRIVATE) }
    var overallBudgetAmount by remember {
        mutableDoubleStateOf(
            budgetPrefs.getString("monthly_expense_budget", "0")?.toDoubleOrNull() ?: 0.0
        )
    }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf(if (overallBudgetAmount > 0.0) String.format(Locale.US, "%.0f", overallBudgetAmount) else "") }
    var showCategoryBudgetDialog by remember { mutableStateOf(false) }
    var categoryForBudgetEdit by remember { mutableStateOf<String?>(null) }
    var categoryBudgetAmountInput by remember { mutableStateOf("") }

    // Handlers for transactions
    fun handleSave(entry: LogEntry) {
        onSaveTransaction(entry)
        showQuickAdd = false
        editingTransaction = null
        onResetQuickAddType()
    }

    fun handleDelete(entry: LogEntry) {
        onDeleteTransaction(entry)
        showQuickAdd = false
        editingTransaction = null
        onResetQuickAddType()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Main Top Bar
            MoneyTopBar(
                selectedTab = selectedMainTab,
                onTabSelect = { selectedMainTab = it },
                selectedMonth = selectedMonth,
                onMonthChange = { selectedMonth = it },
                onOpenAnalytics = { selectedMainTab = 1 },
                onOpenCategories = { showCategoryManagement = true },
                onOpenHistory = { showTransactionHistory = true }
            )

            // Content based on Selected Tab
            when (selectedMainTab) {
                0 -> MoneyOverviewTab(
                    logs = logs,
                    selectedMonth = selectedMonth,
                    periodFilter = overviewPeriodFilter,
                    onPeriodFilterChange = { overviewPeriodFilter = it },
                    overallBudget = overallBudgetAmount,
                    onAddExpense = {
                        quickAddType = "Expense"
                        editingTransaction = null
                        showQuickAdd = true
                    },
                    onAddIncome = {
                        quickAddType = "Income"
                        editingTransaction = null
                        showQuickAdd = true
                    },
                    onOpenAnalytics = { selectedMainTab = 1 },
                    onOpenBudgets = { selectedMainTab = 2 },
                    onOpenCategories = { showCategoryManagement = true },
                    onSelectCategory = { selectedCategoryForDetail = it },
                    onSelectTransaction = {
                        editingTransaction = it
                        quickAddType = it.category
                        showQuickAdd = true
                    },
                    onSeeAllTransactions = { showTransactionHistory = true }
                )
                1 -> MoneyAnalyticsTab(
                    logs = logs,
                    selectedMonth = selectedMonth,
                    analyticsType = analyticsTypeFilter,
                    onAnalyticsTypeChange = { analyticsTypeFilter = it },
                    timeComparisonTab = timeComparisonTab,
                    onTimeComparisonTabChange = { timeComparisonTab = it },
                    onSelectCategory = { selectedCategoryForDetail = it }
                )
                2 -> MoneyBudgetsTab(
                    logs = logs,
                    selectedMonth = selectedMonth,
                    refreshKey = budgetRefreshKey,
                    budgetsSubTab = budgetsSubTab,
                    onBudgetsSubTabChange = { budgetsSubTab = it },
                    overallBudget = overallBudgetAmount,
                    onEditOverallBudget = {
                        budgetInput = if (overallBudgetAmount > 0.0) String.format(Locale.US, "%.0f", overallBudgetAmount) else ""
                        showBudgetDialog = true
                    },
                    onAddCategoryBudget = {
                        categoryForBudgetEdit = null
                        categoryBudgetAmountInput = ""
                        showCategoryBudgetDialog = true
                    },
                    onEditCategoryBudget = { cat ->
                        categoryForBudgetEdit = cat
                        val curr = MoneyCategory.categoryBudgetForMonth(cat, selectedMonth) ?: 0.0
                        categoryBudgetAmountInput = if (curr > 0.0) String.format(Locale.US, "%.0f", curr) else ""
                        showCategoryBudgetDialog = true
                    }
                )
            }
        }

        // Sub-screen Overlays

        // 1. Quick Add / Edit Transaction Screen (Keypad, Category Grid, Payment method)
        if (showQuickAdd || editingTransaction != null) {
            QuickAddTransactionOverlay(
                initialType = editingTransaction?.category ?: quickAddType,
                existingEntry = editingTransaction,
                onSave = { handleSave(it) },
                onDelete = { editingTransaction?.let { handleDelete(it) } },
                onDismiss = {
                    showQuickAdd = false
                    editingTransaction = null
                    onResetQuickAddType()
                },
                onOpenCategoryManagement = { showCategoryManagement = true },
                onOpenHistory = {
                    showQuickAdd = false
                    editingTransaction = null
                    showTransactionHistory = true
                }
            )
        }

        // 2. Category Management Screen
        if (showCategoryManagement) {
            CategoryManagementOverlay(
                logs = logs,
                onDismiss = {
                    showCategoryManagement = false
                    if (initialShowCategories) onNavigateHome()
                },
                onAddCategory = {
                    editingCategoryName = null
                    showAddEditCategory = true
                },
                onEditCategory = { cat ->
                    editingCategoryName = cat
                    showAddEditCategory = true
                }
            )
        }

        // 3. Add / Edit Category Screen (Icon Library & Color Picker)
        if (showAddEditCategory) {
            AddEditCategoryOverlay(
                editingName = editingCategoryName,
                onDismiss = { showAddEditCategory = false },
                onSaved = {
                    showAddEditCategory = false
                    editingCategoryName = null
                }
            )
        }

        // 4. Category Detail & Spending Insights Screen
        selectedCategoryForDetail?.let { cat ->
            CategoryDetailOverlay(
                categoryName = cat,
                logs = logs,
                selectedMonth = selectedMonth,
                onDismiss = { selectedCategoryForDetail = null },
                onEditTransaction = {
                    editingTransaction = it
                    quickAddType = it.category
                    showQuickAdd = true
                }
            )
        }

        // 5. Transaction History Screen (Filters, Search, Sorting)
        if (showTransactionHistory) {
            TransactionHistoryOverlay(
                logs = logs,
                onDismiss = { showTransactionHistory = false },
                onEditTransaction = {
                    editingTransaction = it
                    quickAddType = it.category
                    showQuickAdd = true
                },
                onDeleteTransaction = { handleDelete(it) }
            )
        }

        // 6. Overall Budget Edit Dialog
        if (showBudgetDialog) {
            AlertDialog(
                onDismissRequest = { showBudgetDialog = false },
                containerColor = Color(0xFF0F172A),
                titleContentColor = Color.White,
                textContentColor = Color(0xFF94A3B8),
                title = { Text("Set Overall Monthly Budget", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter your spending budget limit for the month:", fontSize = 12.sp, color = TextMuted)
                        OutlinedTextField(
                            value = budgetInput,
                            onValueChange = { budgetInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Monthly Budget (₹)") },
                            prefix = { Text("₹ ", color = CyanBrand) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanBrand,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val v = budgetInput.toDoubleOrNull()
                            if (v != null && v > 0.0) {
                                overallBudgetAmount = v
                                budgetPrefs.edit().putString("monthly_expense_budget", v.toString()).apply()
                                budgetRefreshKey++
                            }
                            showBudgetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanBrand)
                    ) {
                        Text("Save Budget", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBudgetDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            )
        }

        // 7. Category Budget Edit / Add Dialog
        if (showCategoryBudgetDialog) {
            val allExpenseCats = remember { (MoneyCategory.expenses + MoneyCategory.customExpenses).distinct() }
            var selectedCat by remember(showCategoryBudgetDialog, categoryForBudgetEdit) {
                mutableStateOf(categoryForBudgetEdit ?: allExpenseCats.firstOrNull() ?: "Food")
            }

            AlertDialog(
                onDismissRequest = { showCategoryBudgetDialog = false },
                containerColor = Color(0xFF0F172A),
                titleContentColor = Color.White,
                textContentColor = Color(0xFF94A3B8),
                title = {
                    Text(
                        if (categoryForBudgetEdit != null) "Edit Budget: $categoryForBudgetEdit" else "Set Category Budget",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (categoryForBudgetEdit == null) {
                            Text("Select an expense category:", fontSize = 12.sp, color = TextMuted)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(allExpenseCats) { cat ->
                                    val isSelected = selectedCat == cat
                                    val tint = MoneyCategory.colorFor(cat)
                                    Surface(
                                        color = if (isSelected) tint.copy(alpha = 0.16f) else Color(0xFF1E293B),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, if (isSelected) tint else Color(0xFF334155)),
                                        modifier = Modifier.fillMaxWidth().clickable { selectedCat = cat }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(MoneyCategory.iconFor(cat), null, tint = tint, modifier = Modifier.size(21.dp))
                                            Text(
                                                cat,
                                                color = if (isSelected) tint else Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text("Monthly spending limit · $selectedMonth", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            "This category budget is saved for $selectedMonth only. Changing another month will not overwrite this month's limit.",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        OutlinedTextField(
                            value = categoryBudgetAmountInput,
                            onValueChange = { categoryBudgetAmountInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Budget Limit (₹)") },
                            prefix = { Text("₹ ", color = CyanBrand) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanBrand,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val v = categoryBudgetAmountInput.toDoubleOrNull()
                            val targetCat = categoryForBudgetEdit ?: selectedCat
                            if (v != null && v > 0.0) {
                                MoneyCategory.setCategoryBudget(context, targetCat, v, selectedMonth)
                                budgetRefreshKey++
                            }
                            showCategoryBudgetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanBrand)
                    ) {
                        Text("Save Budget", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (categoryForBudgetEdit != null && MoneyCategory.categoryBudgetForMonth(categoryForBudgetEdit!!, selectedMonth) != null) {
                            TextButton(
                                onClick = {
                                    MoneyCategory.setCategoryBudget(context, categoryForBudgetEdit!!, null, selectedMonth)
                                    budgetRefreshKey++
                                    showCategoryBudgetDialog = false
                                }
                            ) {
                                Text("Remove", color = CoralExpense)
                            }
                        }
                        TextButton(onClick = { showCategoryBudgetDialog = false }) {
                            Text("Cancel", color = TextMuted)
                        }
                    }
                }
            )
        }
    }
}

// ==========================================
// TOP BAR & TAB SELECTOR
// ==========================================
@Composable
private fun MoneyTopBar(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    selectedMonth: String,
    onMonthChange: (String) -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    fun showMonthPicker() {
        val initial = runCatching {
            SimpleDateFormat("yyyy-MM", Locale.US).parse(selectedMonth)
        }.getOrNull() ?: Date()
        val cal = Calendar.getInstance().apply { time = initial }
        android.app.DatePickerDialog(
            context,
            { _, year, month, _ ->
                onMonthChange(String.format(Locale.US, "%04d-%02d", year, month + 1))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            1
        ).show()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBg)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // App title & actions row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Money", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Month selector badge
                Surface(
                    color = CardInner,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.clickable { showMonthPicker() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, null, tint = CyanBrand, modifier = Modifier.size(13.dp))
                        val formattedMonth = remember(selectedMonth) {
                            try {
                                val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(selectedMonth)
                                SimpleDateFormat("MMM yyyy", Locale.US).format(d ?: Date())
                            } catch (_: Exception) { selectedMonth }
                        }
                        Text(formattedMonth, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }

                // Bar chart shortcut
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CardInner)
                        .clickable(onClick = onOpenAnalytics),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.BarChart, "Analytics", tint = CyanBrand, modifier = Modifier.size(18.dp))
                }

                // Categories manager shortcut
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CardInner)
                        .clickable(onClick = onOpenCategories),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Category, "Categories", tint = TextWhite, modifier = Modifier.size(17.dp))
                }

                // History shortcut
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CardInner)
                        .clickable(onClick = onOpenHistory),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ReceiptLong, "Transaction history", tint = TextWhite, modifier = Modifier.size(17.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 3 Main Tabs: Overview | Analytics | Budgets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardInner, RoundedCornerShape(14.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val tabs = listOf("Overview", "Analytics", "Budgets")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) Brush.horizontalGradient(
                                listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            ) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable { onTabSelect(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        color = if (isSelected) Color.White else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ==========================================
// 1. OVERVIEW TAB
// ==========================================
@Composable
private fun MoneyOverviewTab(
    logs: List<LogEntry>,
    selectedMonth: String,
    periodFilter: String,
    onPeriodFilterChange: (String) -> Unit,
    overallBudget: Double,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenCategories: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectTransaction: (LogEntry) -> Unit,
    onSeeAllTransactions: () -> Unit
) {
    val today = remember { java.time.LocalDate.now() }
    val selectedYearMonth = remember(selectedMonth) {
        runCatching { java.time.YearMonth.parse(selectedMonth) }.getOrElse { java.time.YearMonth.now() }
    }

    // Every component below uses this same period-filtered source of truth.
    val activeLogs = remember(logs, selectedMonth, periodFilter, today) {
        val periodRange: Pair<java.time.LocalDate, java.time.LocalDate>? = when (periodFilter) {
            "This Week" -> {
                val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
                start to today
            }
            "This Month" -> selectedYearMonth.atDay(1) to selectedYearMonth.atEndOfMonth()
            "Last Month" -> {
                val previous = selectedYearMonth.minusMonths(1)
                previous.atDay(1) to previous.atEndOfMonth()
            }
            "Custom Range" -> null // No custom range is offered until its picker is wired.
            else -> null // Overall: all eligible saved transactions, not just the selected month.
        }
        logs.filter { entry ->
            (entry.category == "Income" || entry.category == "Expense") &&
                (periodRange == null || runCatching {
                    val date = java.time.LocalDate.parse(entry.date)
                    !date.isBefore(periodRange.first) && !date.isAfter(periodRange.second)
                }.getOrDefault(false))
        }
    }

    val totalIncome = remember(activeLogs) {
        activeLogs.filter { it.category == "Income" }.sumOf { MoneyAmounts.parse(it.amount) ?: 0.0 }
    }
    val totalExpense = remember(activeLogs) {
        activeLogs.filter { it.category == "Expense" }.sumOf { MoneyAmounts.parse(it.amount) ?: 0.0 }
    }
    val totalBalance = totalIncome - totalExpense

    // Category breakdown and recent rows are derived from the same eligible transactions.
    val categoryBreakdown = remember(activeLogs) {
        activeLogs.filter { it.category == "Expense" }
            .groupBy { it.moneyCategory.ifBlank { "Other" } }
            .mapValues { (_, entries) -> entries.sumOf { MoneyAmounts.parse(it.amount) ?: 0.0 } }
            .filterValues { it > 0.0 }
    }

    val recentTransactions = remember(activeLogs) {
        activeLogs.sortedWith(
            compareByDescending<LogEntry> { AnalyticsEngine.parseDate(it.date) }
                .thenByDescending { it.id }
        ).take(6)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Total Balance Card (with glowing background wave)
        item {
            TotalBalanceCard(
                balance = totalBalance,
                income = totalIncome,
                expense = totalExpense,
                savings = totalBalance
            )
        }

        // A monthly budget is only comparable with the selected calendar month's expenses.
        if (overallBudget > 0.0 && periodFilter == "This Month" && selectedYearMonth == java.time.YearMonth.now()) item {
            val progress = (totalExpense / overallBudget).toFloat().coerceAtLeast(0f)
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenBudgets),
                color = CardBg,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Monthly budget", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${(progress * 100).toInt()}% used", color = if (progress >= 1f) CoralExpense else CyanBrand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)),
                        color = if (progress >= 1f) CoralExpense else CyanBrand,
                        trackColor = CardInner
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Spent ₹${String.format(Locale.US, "%,.0f", totalExpense)}", color = TextMuted, fontSize = 11.sp)
                        Text(if (totalExpense <= overallBudget) "₹${String.format(Locale.US, "%,.0f", overallBudget - totalExpense)} left" else "₹${String.format(Locale.US, "%,.0f", totalExpense - overallBudget)} over budget", color = if (totalExpense <= overallBudget) EmeraldIncome else CoralExpense, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 2. Quick Action Buttons (+ Add Expense / + Add Income)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Add Expense Button
                Button(
                    onClick = onAddExpense,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralExpense),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Expense", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // Add Income Button
                Button(
                    onClick = onAddIncome,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldIncome),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Income", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Time Filter Pills (This Week | This Month | Last Month)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Overall", "This Week", "This Month", "Last Month").forEach { filter ->
                    val isSelected = periodFilter == filter
                    Surface(
                        color = if (isSelected) VioletTab else CardInner,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isSelected) VioletTab else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onPeriodFilterChange(filter) }
                    ) {
                        Text(
                            filter,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // 4. Income vs Expenses Grouped Bar Chart
        item {
            IncomeVsExpensesChartCard(
                logs = activeLogs,
                totalIncome = totalIncome,
                totalExpense = totalExpense
            )
        }

        // 5. Expense Breakdown Donut Chart
        item {
            ExpenseBreakdownDonutCard(
                categoryBreakdown = categoryBreakdown,
                totalExpense = totalExpense,
                onSelectCategory = onSelectCategory
            )
        }

        // 6. Recent Transactions List
        item {
            RecentTransactionsSection(
                transactions = recentTransactions,
                onSelect = onSelectTransaction,
                onSeeAll = onSeeAllTransactions
            )
        }
    }
}

// ==========================================
// TOTAL BALANCE CARD COMPONENT
// ==========================================
@Composable
private fun TotalBalanceCard(
    balance: Double,
    income: Double,
    expense: Double,
    savings: Double
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Subtle background sparkline
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .align(Alignment.BottomCenter)
            ) {
                val path = Path().apply {
                    moveTo(0f, size.height * 0.7f)
                    cubicTo(
                        size.width * 0.25f, size.height * 0.9f,
                        size.width * 0.5f, size.height * 0.3f,
                        size.width * 0.75f, size.height * 0.5f
                    )
                    cubicTo(
                        size.width * 0.85f, size.height * 0.6f,
                        size.width * 0.95f, size.height * 0.2f,
                        size.width, size.height * 0.35f
                    )
                }
                drawPath(
                    path = path,
                    color = EmeraldIncome.copy(alpha = 0.25f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Balance amount & Badge
                Column {
                    Text("Recorded Net Balance", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "₹ ${String.format(Locale.US, "%,.0f", balance)}",
                            color = TextWhite,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (balance >= 0) "Available" else "Overspent",
                            color = if (balance >= 0) EmeraldIncome else CoralExpense,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(
                                    (if (balance >= 0) EmeraldIncome else CoralExpense).copy(alpha = 0.14f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // 3 Stats: Income | Expenses | Savings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BalanceMetricItem(
                        label = "Income",
                        amount = "₹ ${String.format(Locale.US, "%,.0f", income)}",
                        color = EmeraldIncome,
                        icon = Icons.Default.ArrowDownward
                    )
                    BalanceMetricItem(
                        label = "Expenses",
                        amount = "₹ ${String.format(Locale.US, "%,.0f", expense)}",
                        color = CoralExpense,
                        icon = Icons.Default.ArrowUpward
                    )
                    BalanceMetricItem(
                        label = "Net balance",
                        amount = "₹ ${String.format(Locale.US, "%,.0f", savings)}",
                        color = CyanBrand,
                        icon = Icons.Default.AccountBalanceWallet
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceMetricItem(
    label: String,
    amount: String,
    color: Color,
    icon: ImageVector
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Text(amount, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ==========================================
// INCOME VS EXPENSES GROUPED BAR CHART
// ==========================================
@Composable
private fun IncomeVsExpensesChartCard(
    logs: List<LogEntry>,
    totalIncome: Double,
    totalExpense: Double
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Income vs Expenses", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                // Legend
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(EmeraldIncome, CircleShape))
                        Text("Income", color = TextMuted, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(CoralExpense, CircleShape))
                        Text("Expenses", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }

            // Custom Canvas Grouped Bar Chart
            val hasRealData = remember(logs) { logs.any { it.category == "Expense" || it.category == "Income" } }
            val (incomeHeights, expenseHeights) = remember(logs, hasRealData) {
                if (hasRealData) {
                    val incBins = DoubleArray(7)
                    val expBins = DoubleArray(7)
                    logs.forEach { entry ->
                        val day = entry.date.substringAfterLast("-", "1").toIntOrNull() ?: 1
                        val bin = ((day - 1) * 7 / 31).coerceIn(0, 6)
                        val v = MoneyAmounts.value(entry.amount)
                        if (entry.category == "Income") incBins[bin] += v
                        else if (entry.category == "Expense") expBins[bin] += v
                    }
                    val maxVal = maxOf(incBins.maxOrNull() ?: 0.0, expBins.maxOrNull() ?: 0.0).coerceAtLeast(10.0)
                    val incH = incBins.map { (it / maxVal).toFloat().coerceIn(0.02f, 1f) }
                    val expH = expBins.map { (it / maxVal).toFloat().coerceIn(0.02f, 1f) }
                    incH to expH
                } else {
                    List(7) { 0f } to List(7) { 0f }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height - 24.dp.toPx()
                    val bottomY = h

                    // Draw 3 subtle horizontal gridlines
                    for (i in 0..3) {
                        val y = bottomY * (i / 3f)
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(24.dp.toPx(), y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 7 intervals: days 1, 5, 10, 15, 20, 25, 30
                    val days = listOf("1", "5", "10", "15", "20", "25", "30")

                    val startX = 30.dp.toPx()
                    val usableW = w - startX - 10.dp.toPx()
                    val groupSpacing = usableW / days.size
                    val barW = 7.dp.toPx()

                    days.forEachIndexed { i, _ ->
                        val groupCenterX = startX + (i * groupSpacing) + (groupSpacing / 2f)

                        // Income Bar (Green)
                        val incH = bottomY * incomeHeights[i]
                        drawRoundRect(
                            color = EmeraldIncome,
                            topLeft = Offset(groupCenterX - barW - 1.5.dp.toPx(), bottomY - incH),
                            size = Size(barW, incH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )

                        // Expense Bar (Coral)
                        val expH = bottomY * expenseHeights[i]
                        drawRoundRect(
                            color = CoralExpense,
                            topLeft = Offset(groupCenterX + 1.5.dp.toPx(), bottomY - expH),
                            size = Size(barW, expH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                    }
                }

                // X-axis day labels below chart
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 28.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("1", "5", "10", "15", "20", "25", "30").forEach { d ->
                        Text(d, color = TextMuted, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// EXPENSE BREAKDOWN DONUT CHART CARD
// ==========================================
@Composable
private fun ExpenseBreakdownDonutCard(
    categoryBreakdown: Map<String, Double>,
    totalExpense: Double,
    onSelectCategory: (String) -> Unit
) {
    var showAllCategories by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Expense Breakdown", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (showAllCategories) "Show less" else "See all >",
                    color = CyanBrand,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { showAllCategories = !showAllCategories }
                )
            }

            if (categoryBreakdown.isEmpty() || totalExpense <= 0.0) {
                Text(
                    "No expense category data for this month yet. Add an expense to populate this chart.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Donut Chart
                Box(
                    modifier = Modifier.size(115.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val strokeW = 14.dp.toPx()
                        var currentAngle = -90f
                        val total = if (totalExpense > 0.0) totalExpense else 1.0

                        categoryBreakdown.entries.take(7).forEach { (cat, amount) ->
                            val sweep = ((amount / total) * 360f).toFloat()
                            val color = MoneyCategory.colorFor(cat)
                            drawArc(
                                color = color,
                                startAngle = currentAngle,
                                sweepAngle = sweep.coerceAtLeast(4f),
                                useCenter = false,
                                style = Stroke(strokeW)
                            )
                            currentAngle += sweep
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "₹ ${String.format(Locale.US, "%,.0f", totalExpense)}",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text("This month", color = TextMuted, fontSize = 8.sp)
                    }
                }

                // Category Legend List
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val total = if (totalExpense > 0.0) totalExpense else 1.0
                    (if (showAllCategories) categoryBreakdown.entries else categoryBreakdown.entries.take(6)).forEach { (cat, amount) ->
                        val pct = ((amount / total) * 100).toInt()
                        val color = MoneyCategory.colorFor(cat)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCategory(cat) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(8.dp).background(color, CircleShape))
                                Text(cat, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("$pct%", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// RECENT TRANSACTIONS COMPONENT
// ==========================================
@Composable
private fun RecentTransactionsSection(
    transactions: List<LogEntry>,
    onSelect: (LogEntry) -> Unit,
    onSeeAll: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Transactions", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "See all >",
                    color = CyanBrand,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onSeeAll)
                )
            }

            transactions.forEach { entry ->
                val isExpense = entry.category == "Expense"
                val catName = entry.moneyCategory.ifBlank { if (isExpense) "Food" else "Salary" }
                val icon = MoneyCategory.iconFor(catName)
                val color = MoneyCategory.colorFor(catName)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardInner)
                        .clickable { onSelect(entry) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Category icon tile
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(color.copy(alpha = 0.18f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }

                    // Title & Note/Payment
                    Column(Modifier.weight(1f)) {
                        Text(entry.title.ifBlank { catName }, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        val subtitle = if (entry.note.isNotBlank()) "${entry.date} · ${entry.note}" else entry.date
                        Text(subtitle, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    // Amount with +/-
                    Text(
                        "${if (isExpense) "-₹" else "+₹"}${entry.amount}",
                        color = if (isExpense) CoralExpense else EmeraldIncome,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. ANALYTICS TAB (TRENDS & COMPARISONS)
// ==========================================
@Composable
private fun MoneyAnalyticsTab(
    logs: List<LogEntry>,
    selectedMonth: String,
    analyticsType: String,
    onAnalyticsTypeChange: (String) -> Unit,
    timeComparisonTab: String,
    onTimeComparisonTabChange: (String) -> Unit,
    onSelectCategory: (String) -> Unit
) {
    val monthDate = remember(selectedMonth) {
        runCatching { SimpleDateFormat("yyyy-MM", Locale.US).parse(selectedMonth) }.getOrNull() ?: Date()
    }
    val monthCalendar = remember(monthDate) { Calendar.getInstance().apply { time = monthDate } }
    val monthDays = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val currentCalendar = Calendar.getInstance()
    val isCurrentMonth = currentCalendar.get(Calendar.YEAR) == monthCalendar.get(Calendar.YEAR) &&
        currentCalendar.get(Calendar.MONTH) == monthCalendar.get(Calendar.MONTH)
    val elapsedDays = if (isCurrentMonth) currentCalendar.get(Calendar.DAY_OF_MONTH) else monthDays
    val prevMonth = remember(selectedMonth) {
        SimpleDateFormat("yyyy-MM", Locale.US).format(
            Calendar.getInstance().apply { time = monthDate; add(Calendar.MONTH, -1) }.time
        )
    }
    val comparableDays = remember(selectedMonth) {
        val selected = runCatching { java.time.YearMonth.parse(selectedMonth) }.getOrNull()
            ?: java.time.YearMonth.from(java.time.LocalDate.now())
        if (selected == java.time.YearMonth.from(java.time.LocalDate.now())) {
            java.time.LocalDate.now().dayOfMonth
        } else {
            selected.lengthOfMonth()
        }
    }
    fun totalForMonth(month: String, type: String, throughDay: Int): Double {
        val monthLogs = logs.filter { entry ->
            if (!entry.date.startsWith(month)) false
            else (entry.date.substringAfterLast("-", "0").toIntOrNull() ?: Int.MAX_VALUE) <= throughDay
        }
        val income = monthLogs.filter { it.category.equals("Income", true) }.sumOf { MoneyAmounts.value(it.amount) }
        val expenses = monthLogs.filter { it.category.equals("Expense", true) }.sumOf { MoneyAmounts.value(it.amount) }
        return when (type) {
            "Income" -> income
            "Savings" -> income - expenses
            else -> expenses
        }
    }
    val previousMonthDays = remember(prevMonth, comparableDays) {
        val length = runCatching { java.time.YearMonth.parse(prevMonth).lengthOfMonth() }.getOrDefault(comparableDays)
        comparableDays.coerceAtMost(length)
    }
    val currentTotal = remember(logs, selectedMonth, analyticsType, comparableDays) {
        totalForMonth(selectedMonth, analyticsType, comparableDays)
    }
    val previousTotal = remember(logs, prevMonth, analyticsType, previousMonthDays) {
        totalForMonth(prevMonth, analyticsType, previousMonthDays)
    }
    val dailyAverage = currentTotal / comparableDays.coerceAtLeast(1)
    val totalLabel = when (analyticsType) { "Income" -> "Total Income"; "Savings" -> "Net Savings"; else -> "Total Expenses" }
    val dailyLabel = when (analyticsType) { "Income" -> "Avg. Daily Income"; "Savings" -> "Avg. Daily Net"; else -> "Avg. Daily Spend" }
    val deltaLabel = if (previousTotal == 0.0) "No baseline" else {
        val percent = ((currentTotal - previousTotal) / kotlin.math.abs(previousTotal) * 100).toInt()
        "${if (percent > 0) "+" else ""}$percent% vs last month"
    }
    val deltaColor = when {
        previousTotal == 0.0 -> TextMuted
        analyticsType == "Expense" && currentTotal > previousTotal -> CoralExpense
        analyticsType != "Expense" && currentTotal > previousTotal -> EmeraldIncome
        else -> CyanBrand
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().background(CardInner, RoundedCornerShape(14.dp)).padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Expense", "Income", "Savings").forEach { type ->
                    val selected = analyticsType == type
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) VioletTab else Color.Transparent)
                            .clickable { onAnalyticsTypeChange(type) }.padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(type, color = if (selected) Color.White else TextMuted, fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(totalLabel to currentTotal, dailyLabel to dailyAverage).forEach { (label, value) ->
                    Surface(
                        color = CardBg, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(label, color = TextMuted, fontSize = 11.sp)
                            Text("₹ ${String.format(Locale.US, "%,.0f", value)}", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(deltaLabel, color = deltaColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (analyticsType == "Expense") {
            item { SpendingTrendCard(logs = logs, selectedMonth = selectedMonth) }
            item { CategoryComparisonChartCard(logs = logs, selectedMonth = selectedMonth, onSelectCategory = onSelectCategory) }
            item { TopSpendingCategoriesCard(logs = logs, selectedMonth = selectedMonth, onSelectCategory = onSelectCategory) }
        } else {
            item {
                MoneyFlowAnalyticsCard(
                    logs = logs,
                    selectedMonth = selectedMonth,
                    previousMonth = prevMonth,
                    analyticsType = analyticsType,
                    onSelectCategory = onSelectCategory
                )
            }
        }
        item {
            TimeComparisonSection(
                logs = logs,
                selectedMonth = selectedMonth,
                currentTab = timeComparisonTab,
                onTabSelect = onTimeComparisonTabChange,
                onSelectCategory = onSelectCategory
            )
        }
    }
}

@Composable
private fun MoneyFlowAnalyticsCard(
    logs: List<LogEntry>,
    selectedMonth: String,
    previousMonth: String,
    analyticsType: String,
    onSelectCategory: (String) -> Unit
) {
    val title = if (analyticsType == "Income") "Income Trend & Categories" else "Savings / Net Cash Flow"
    val days = remember(selectedMonth) {
        val parsed = runCatching { SimpleDateFormat("yyyy-MM", Locale.US).parse(selectedMonth) }.getOrNull() ?: Date()
        val cal = Calendar.getInstance().apply { time = parsed }
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val bucketCount = 6
    fun amount(entry: LogEntry): Double = MoneyAmounts.value(entry.amount)
    val currentBuckets = remember(logs, selectedMonth, analyticsType, days) {
        (0 until bucketCount).map { index ->
            val first = (index * days / bucketCount) + 1
            val last = (((index + 1) * days / bucketCount)).coerceAtLeast(first)
            val bucketLogs = logs.filter { entry ->
                entry.date.startsWith(selectedMonth) && (entry.date.substringAfterLast("-", "0").toIntOrNull() ?: 0) in first..last
            }
            val income = bucketLogs.filter { it.category.equals("Income", true) }.sumOf(::amount)
            val expenses = bucketLogs.filter { it.category.equals("Expense", true) }.sumOf(::amount)
            if (analyticsType == "Income") income else income - expenses
        }
    }
    val categoryRows = remember(logs, selectedMonth, analyticsType) {
        val monthLogs = logs.filter { it.date.startsWith(selectedMonth) }
        val grouped = if (analyticsType == "Income") {
            monthLogs.filter { it.category.equals("Income", true) }
                .groupBy { it.moneyCategory.ifBlank { "Other" } }
                .mapValues { (_, entries) -> entries.sumOf(::amount) }
        } else {
            val incomeByCategory = monthLogs.filter { it.category.equals("Income", true) }
                .groupBy { it.moneyCategory.ifBlank { "Other" } }.mapValues { (_, entries) -> entries.sumOf(::amount) }
            val expenseByCategory = monthLogs.filter { it.category.equals("Expense", true) }
                .groupBy { it.moneyCategory.ifBlank { "Other" } }.mapValues { (_, entries) -> entries.sumOf(::amount) }
            (incomeByCategory.keys + expenseByCategory.keys).distinct().associateWith { (incomeByCategory[it] ?: 0.0) - (expenseByCategory[it] ?: 0.0) }
        }
        grouped.entries.sortedByDescending { it.value }.take(8)
    }
    Surface(Modifier.fillMaxWidth(), color = CardBg, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, CardBorder)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (logs.none { it.date.startsWith(selectedMonth) && (it.category.equals("Income", true) || it.category.equals("Expense", true)) }) {
                Text("No income or expense records for $selectedMonth yet. Add a transaction to populate this chart.", color = TextMuted, fontSize = 12.sp)
            } else {
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    val maxAbs = currentBuckets.maxOfOrNull { kotlin.math.abs(it) }?.toFloat()?.coerceAtLeast(1f) ?: 1f
                    val slot = size.width / currentBuckets.size
                    val baseline = if (analyticsType == "Savings") size.height / 2f else size.height - 4.dp.toPx()
                    drawLine(TextMuted.copy(alpha = 0.35f), Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
                    currentBuckets.forEachIndexed { index, value ->
                        val barHeight = (kotlin.math.abs(value.toFloat()) / maxAbs) * (size.height * if (analyticsType == "Savings") 0.42f else 0.88f)
                        val top = if (analyticsType == "Savings" && value < 0) baseline else baseline - barHeight
                        drawRoundRect(
                            color = if (value < 0) CoralExpense else if (analyticsType == "Income") EmeraldIncome else CyanBrand,
                            topLeft = Offset(index * slot + slot * 0.2f, top),
                            size = Size(slot * 0.6f, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                    }
                }
                Text("Six date-range buckets · actual saved transactions", color = TextMuted, fontSize = 10.sp)
                Text("Category breakdown", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                categoryRows.forEach { (category, value) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelectCategory(category) }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(MoneyCategory.iconFor(category), null, tint = MoneyCategory.colorFor(category), modifier = Modifier.size(22.dp))
                        Text(category, color = TextWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("₹ ${String.format(Locale.US, "%,.0f", value)}", color = if (value < 0) CoralExpense else EmeraldIncome, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ==========================================
// SPENDING TREND CHART CARD
// ==========================================
@Composable
private fun SpendingTrendCard(
    logs: List<LogEntry>,
    selectedMonth: String
) {
    val prevMonth = remember(selectedMonth) {
        try {
            val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(selectedMonth)
            val c = Calendar.getInstance().apply { time = d ?: Date(); add(Calendar.MONTH, -1) }
            SimpleDateFormat("yyyy-MM", Locale.US).format(c.time)
        } catch (_: Exception) { "" }
    }

    val hasRealData = remember(logs, selectedMonth, prevMonth) {
        logs.any { it.category.equals("Expense", true) && (it.date.startsWith(selectedMonth) || it.date.startsWith(prevMonth)) }
    }
    val (thisMonthPoints, lastMonthPoints) = remember(logs, selectedMonth, prevMonth, hasRealData) {
        if (hasRealData) {
            val selectedMonthDays = runCatching {
                java.time.YearMonth.parse(selectedMonth).lengthOfMonth()
            }.getOrDefault(30)
            val days = (0..5).map { index ->
                if (index == 0) 1 else (index * selectedMonthDays / 5).coerceAtLeast(1)
            }
            val curPoints = FloatArray(6)
            val prevPoints = FloatArray(6)

            val curEntries = logs.filter { it.category == "Expense" && it.date.startsWith(selectedMonth) }
            val prevEntries = logs.filter { it.category == "Expense" && it.date.startsWith(prevMonth) }

            days.forEachIndexed { idx, targetDay ->
                val startDay = if (idx == 0) 1 else days[idx - 1] + 1
                val curSum = curEntries.filter {
                    val d = it.date.substringAfterLast("-", "0").toIntOrNull() ?: 0
                    d in startDay..targetDay
                }.sumOf { MoneyAmounts.value(it.amount) }

                val prevSum = prevEntries.filter {
                    val d = it.date.substringAfterLast("-", "0").toIntOrNull() ?: 0
                    d in startDay..targetDay
                }.sumOf { MoneyAmounts.value(it.amount) }

                curPoints[idx] = curSum.toFloat()
                prevPoints[idx] = prevSum.toFloat()
            }

            val maxSpend = maxOf(curPoints.maxOrNull() ?: 0f, prevPoints.maxOrNull() ?: 0f).coerceAtLeast(100f)
            val p1 = curPoints.map { (1f - (it / maxSpend) * 0.75f).coerceIn(0.15f, 1f) }
            val p2 = prevPoints.map { (1f - (it / maxSpend) * 0.75f).coerceIn(0.15f, 1f) }
            p1 to p2
        } else {
            emptyList<Float>() to emptyList<Float>()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Spending Trend", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(PurpleAccent, CircleShape))
                        Text("This Month", color = TextMuted, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(CyanBrand, CircleShape))
                        Text("Last Month", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }

            // Trend Canvas with Beziers & Gradient Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height - 20.dp.toPx()

                    // Grid lines
                    for (i in 0..3) {
                        val y = h * (i / 3f)
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Path 1: This Month (Purple with gradient fill)
                    if (hasRealData && thisMonthPoints.size == 6) {
                    val p1 = Path().apply {
                        moveTo(0f, h * thisMonthPoints[0])
                        cubicTo(w * 0.2f, h * thisMonthPoints[1], w * 0.35f, h * thisMonthPoints[2], w * 0.5f, h * thisMonthPoints[3])
                        cubicTo(w * 0.65f, h * thisMonthPoints[3], w * 0.75f, h * thisMonthPoints[4], w, h * thisMonthPoints[5])
                    }

                    val fillPath = Path().apply {
                        addPath(p1)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(PurpleAccent.copy(alpha = 0.35f), Color.Transparent)
                        )
                    )

                    drawPath(
                        path = p1,
                        color = PurpleAccent,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Path 2: Last Month (Cyan line)
                    val p2 = Path().apply {
                        moveTo(0f, h * lastMonthPoints[0])
                        cubicTo(w * 0.25f, h * lastMonthPoints[1], w * 0.45f, h * lastMonthPoints[2], w * 0.6f, h * lastMonthPoints[3])
                        cubicTo(w * 0.75f, h * lastMonthPoints[4], w * 0.9f, h * lastMonthPoints[5], w, h * lastMonthPoints[5])
                    }

                    drawPath(
                        path = p2,
                        color = CyanBrand.copy(alpha = 0.7f),
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    }
                }

                if (!hasRealData) {
                    Text(
                        "No expense records for this month or the previous month.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                // X-axis day points
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val axisDays = remember(selectedMonth) {
                        val count = runCatching { java.time.YearMonth.parse(selectedMonth).lengthOfMonth() }.getOrDefault(30)
                        (0..5).map { index -> if (index == 0) 1 else (index * count / 5).coerceAtLeast(1) }
                    }
                    axisDays.forEach { day ->
                        Text(day.toString(), color = TextMuted, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// CATEGORY COMPARISON CHART CARD
// ==========================================
@Composable
private fun CategoryComparisonChartCard(
    logs: List<LogEntry>,
    selectedMonth: String,
    onSelectCategory: (String) -> Unit
) {
    val prevMonth = remember(selectedMonth) {
        try {
            val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(selectedMonth)
            val c = Calendar.getInstance().apply { time = d ?: Date(); add(Calendar.MONTH, -1) }
            SimpleDateFormat("yyyy-MM", Locale.US).format(c.time)
        } catch (_: Exception) { "" }
    }

    val hasRealData = remember(logs, selectedMonth, prevMonth) {
        logs.any { it.category.equals("Expense", true) && (it.date.startsWith(selectedMonth) || it.date.startsWith(prevMonth)) }
    }
    val (cats, thisMonthHeights, lastMonthHeights) = remember(logs, selectedMonth, prevMonth, hasRealData) {
        if (hasRealData) {
            val cur = logs.filter { it.category == "Expense" && it.date.startsWith(selectedMonth) }
                .groupBy { it.moneyCategory.ifBlank { "Other" } }
                .mapValues { (_, v) -> v.sumOf { MoneyAmounts.value(it.amount) } }
            val prev = logs.filter { it.category == "Expense" && it.date.startsWith(prevMonth) }
                .groupBy { it.moneyCategory.ifBlank { "Other" } }
                .mapValues { (_, v) -> v.sumOf { MoneyAmounts.value(it.amount) } }

            val topCats = (cur.keys + prev.keys).distinct().sortedByDescending { maxOf(cur[it] ?: 0.0, prev[it] ?: 0.0) }.take(6)
            val maxVal = maxOf(cur.values.maxOrNull() ?: 0.0, prev.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            val b1 = topCats.map { ((cur[it] ?: 0.0) / maxVal).toFloat().coerceIn(0f, 1f) }
            val b2 = topCats.map { ((prev[it] ?: 0.0) / maxVal).toFloat().coerceIn(0f, 1f) }
            Triple(topCats, b1, b2)
        } else {
            Triple(emptyList<String>(), emptyList<Float>(), emptyList<Float>())
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Category Comparison", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(CyanBrand, CircleShape))
                        Text("This Month", color = TextMuted, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(PurpleAccent, CircleShape))
                        Text("Last Month", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }

            // Dual Grouped Bar Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height - 18.dp.toPx()

                    val groupW = w / cats.size
                    val barW = 8.dp.toPx()

                    cats.forEachIndexed { i, _ ->
                        val centerX = (i * groupW) + (groupW / 2f)

                        // This month (Cyan)
                        val b1H = h * thisMonthHeights[i]
                        drawRoundRect(
                            color = CyanBrand,
                            topLeft = Offset(centerX - barW - 1.5.dp.toPx(), h - b1H),
                            size = Size(barW, b1H),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                        )

                        // Last month (Purple)
                        val b2H = h * lastMonthHeights[i]
                        drawRoundRect(
                            color = PurpleAccent,
                            topLeft = Offset(centerX + 1.5.dp.toPx(), h - b2H),
                            size = Size(barW, b2H),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                        )
                    }
                }

                if (cats.isEmpty()) {
                    Text(
                        "No category spending records for this month or last month.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    cats.forEach { c ->
                        Text(
                            c.take(5),
                            color = TextMuted,
                            fontSize = 9.sp,
                            modifier = Modifier.clickable { onSelectCategory(c) }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// TOP SPENDING CATEGORIES CARD
// ==========================================
@Composable
private fun TopSpendingCategoriesCard(
    logs: List<LogEntry>,
    selectedMonth: String,
    onSelectCategory: (String) -> Unit
) {
    val prevMonth = remember(selectedMonth) {
        try {
            val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(selectedMonth)
            val c = Calendar.getInstance().apply { time = d ?: Date(); add(Calendar.MONTH, -1) }
            SimpleDateFormat("yyyy-MM", Locale.US).format(c.time)
        } catch (_: Exception) { "" }
    }

    val hasRealData = remember(logs, selectedMonth, prevMonth) {
        logs.any { it.category.equals("Expense", true) && (it.date.startsWith(selectedMonth) || it.date.startsWith(prevMonth)) }
    }
    val items = remember(logs, selectedMonth, prevMonth, hasRealData) {
        if (hasRealData) {
            val cur = logs.filter { it.category == "Expense" && it.date.startsWith(selectedMonth) }
                .groupBy { it.moneyCategory.ifBlank { "Other" } }
                .mapValues { (_, v) -> v.sumOf { MoneyAmounts.value(it.amount) } }
            val prev = logs.filter { it.category == "Expense" && it.date.startsWith(prevMonth) }
                .groupBy { it.moneyCategory.ifBlank { "Other" } }
                .mapValues { (_, v) -> v.sumOf { MoneyAmounts.value(it.amount) } }

            val sorted = cur.entries.sortedByDescending { it.value }.take(6)
            if (sorted.isNotEmpty()) {
                sorted.map { (cat, amount) ->
                    val prevAmt = prev[cat] ?: 0.0
                    val changeStr = if (prevAmt <= 0.0) "No baseline" else {
                        val pctChange = ((amount - prevAmt) / prevAmt * 100).toInt()
                        "${if (pctChange > 0) "+" else ""}$pctChange%"
                    }
                    Triple(cat, amount.toInt(), changeStr)
                }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Top Spending Categories", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)

            if (items.isEmpty()) {
                Text(
                    "No expense transactions for $selectedMonth yet. Add an expense to populate this list.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            items.forEach { (cat, amount, change) ->
                val icon = MoneyCategory.iconFor(cat)
                val color = MoneyCategory.colorFor(cat)
                val isUp = change.startsWith("+")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectCategory(cat) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(color.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                    }

                    Column(Modifier.weight(1f)) {
                        Text(cat, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("₹ $amount", color = TextMuted, fontSize = 10.sp)
                    }

                    // Change indicator below is derived from recorded amounts; no synthetic sparkline.
                    Text(
                        if (change == "No baseline") change else "${if (isUp) "↑ " else "↓ "}$change",
                        color = if (change == "No baseline") TextMuted else if (isUp) CoralExpense else EmeraldIncome,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// TIME COMPARISON SECTION (SCREEN 3)
// ==========================================
@Composable
private fun TimeComparisonSection(
    logs: List<LogEntry>,
    selectedMonth: String,
    currentTab: String,
    onTabSelect: (String) -> Unit,
    onSelectCategory: (String) -> Unit
) {
    val hasAnyTransactions = remember(logs) { logs.any { it.category == "Expense" || it.category == "Income" } }

    val comparisonData = remember(logs, currentTab, hasAnyTransactions, selectedMonth) {
        if (hasAnyTransactions) {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = java.time.LocalDate.now()
            val selectedMonthDate = runCatching {
                java.time.YearMonth.parse(selectedMonth)
            }.getOrNull() ?: java.time.YearMonth.from(today)
            val selectedMonthEnd = selectedMonthDate.atEndOfMonth()
            // For the current month compare through today; for a historical/future month use its last day.
            val anchor = if (selectedMonthDate == java.time.YearMonth.from(today)) today
                else if (selectedMonthEnd.isAfter(today)) selectedMonthDate.atDay(1) else selectedMonthEnd

            val (thisEntries, lastEntries, thisLbl, lastLbl) = when (currentTab) {
                "Month" -> {
                    val start = selectedMonthDate.atDay(1)
                    val currentEnd = if (selectedMonthDate == java.time.YearMonth.from(today)) today else selectedMonthEnd
                    val previousStart = start.minusMonths(1)
                    val previousEnd = previousStart.plusDays((currentEnd.dayOfMonth - 1).toLong())
                        .coerceAtMost(previousStart.withDayOfMonth(previousStart.lengthOfMonth()))
                    val t = logs.filter { it.date >= start.toString() && it.date <= currentEnd.toString() }
                    val l = logs.filter { it.date >= previousStart.toString() && it.date <= previousEnd.toString() }
                    Quadruple(t, l, selectedMonth, previousStart.toString().substring(0, 7))
                }
                "Year" -> {
                    val start = selectedMonthDate.atDay(1).withDayOfYear(1)
                    val currentEnd = if (selectedMonthDate == java.time.YearMonth.from(today)) today else selectedMonthEnd
                    val previousStart = start.minusYears(1)
                    val previousEnd = currentEnd.minusYears(1)
                    val t = logs.filter { it.date >= start.toString() && it.date <= currentEnd.toString() }
                    val l = logs.filter { it.date >= previousStart.toString() && it.date <= previousEnd.toString() }
                    Quadruple(t, l, "${selectedMonth.take(4)} YTD", "${previousStart.year} comparable")
                }
                "Custom" -> {
                    val end = anchor
                    val start = end.minusDays(29)
                    val prevEnd = start.minusDays(1)
                    val prevStart = prevEnd.minusDays(29)
                    val t = logs.filter { it.date >= start.toString() && it.date <= end.toString() }
                    val l = logs.filter { it.date >= prevStart.toString() && it.date <= prevEnd.toString() }
                    Quadruple(t, l, "Last 30 Days", "Previous 30 Days")
                }
                else -> { // Week ending at the selected month anchor
                    val start = anchor.minusDays(6)
                    val prevEnd = start.minusDays(1)
                    val prevStart = prevEnd.minusDays(6)
                    val t = logs.filter { it.date >= start.toString() && it.date <= anchor.toString() }
                    val l = logs.filter { it.date >= prevStart.toString() && it.date <= prevEnd.toString() }
                    Quadruple(t, l, "Selected week", "Previous week")
                }
            }

            val inc1 = thisEntries.filter { it.category == "Income" }.sumOf { MoneyAmounts.value(it.amount) }
            val exp1 = thisEntries.filter { it.category == "Expense" }.sumOf { MoneyAmounts.value(it.amount) }
            val sav1 = inc1 - exp1

            val inc2 = lastEntries.filter { it.category == "Income" }.sumOf { MoneyAmounts.value(it.amount) }
            val exp2 = lastEntries.filter { it.category == "Expense" }.sumOf { MoneyAmounts.value(it.amount) }
            val sav2 = inc2 - exp2

            // Daily breakdown Mon-Sun
            val dThis = FloatArray(7)
            val dLast = FloatArray(7)
            thisEntries.filter { it.category == "Expense" }.forEach {
                try {
                    val c = Calendar.getInstance().apply { time = fmt.parse(it.date) ?: Date() }
                    val dayOfWeek = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
                    dThis[dayOfWeek] += MoneyAmounts.value(it.amount).toFloat()
                } catch (_: Exception) {}
            }
            lastEntries.filter { it.category == "Expense" }.forEach {
                try {
                    val c = Calendar.getInstance().apply { time = fmt.parse(it.date) ?: Date() }
                    val dayOfWeek = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
                    dLast[dayOfWeek] += MoneyAmounts.value(it.amount).toFloat()
                } catch (_: Exception) {}
            }
            val maxD = maxOf(dThis.maxOrNull() ?: 0f, dLast.maxOrNull() ?: 0f).coerceAtLeast(10f)
            val rThis = dThis.map { (it / maxD).coerceIn(0f, 1f) }
            val rLast = dLast.map { (it / maxD).coerceIn(0f, 1f) }

            val diffSavings = sav1 - sav2
            val bannerText = if (sav2 == 0.0) {
                "No previous savings baseline for comparison"
            } else {
                val pct = ((diffSavings / kotlin.math.abs(sav2)) * 100).toInt()
                "${if (pct > 0) "+" else ""}$pct% net savings vs $lastLbl"
            }

            ComparisonSummary(
                inc1, exp1, sav1,
                inc2, exp2, sav2,
                thisLbl, lastLbl,
                rThis, rLast,
                bannerText, diffSavings >= 0
            )
        } else {
            ComparisonSummary(
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                "This Period", "Last Period",
                List(7) { 0f },
                List(7) { 0f },
                "No comparison data recorded yet", true
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Time Comparison", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)

            // Period selector tabs: Week | Month | Year | Custom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardInner, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Week", "Month", "Year", "30 Days").forEach { period ->
                    val isSelected = if (period == "30 Days") currentTab == "Custom" else currentTab == period
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) VioletTab else Color.Transparent)
                            .clickable { onTabSelect(if (period == "30 Days") "Custom" else period) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            period,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Dual Comparison Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // This Period Card
                Surface(
                    color = CardInner,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(comparisonData.label1, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Selected date range", color = TextMuted, fontSize = 8.sp)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Income: ", color = TextMuted, fontSize = 10.sp)
                            Text("₹ ${String.format(Locale.US, "%,.0f", comparisonData.income1)}", color = EmeraldIncome, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Expense: ", color = TextMuted, fontSize = 10.sp)
                            Text("₹ ${String.format(Locale.US, "%,.0f", comparisonData.expense1)}", color = CoralExpense, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Savings: ", color = TextMuted, fontSize = 10.sp)
                            Text("₹ ${String.format(Locale.US, "%,.0f", comparisonData.savings1)}", color = CyanBrand, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Last Period Card
                Surface(
                    color = CardInner,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(comparisonData.label2, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Comparable previous range", color = TextMuted, fontSize = 8.sp)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Income: ", color = TextMuted, fontSize = 10.sp)
                            Text("₹ ${String.format(Locale.US, "%,.0f", comparisonData.income2)}", color = EmeraldIncome, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Expense: ", color = TextMuted, fontSize = 10.sp)
                            Text("₹ ${String.format(Locale.US, "%,.0f", comparisonData.expense2)}", color = CoralExpense, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Savings: ", color = TextMuted, fontSize = 10.sp)
                            Text("₹ ${String.format(Locale.US, "%,.0f", comparisonData.savings2)}", color = CyanBrand, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Insight Banner
            val bannerColor = if (comparisonData.insightText.startsWith("No previous") || comparisonData.insightText.startsWith("No comparison")) TextMuted
                else if (comparisonData.isHigherSavings) EmeraldIncome else CoralExpense
            Surface(
                color = bannerColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Stars, null, tint = bannerColor, modifier = Modifier.size(16.dp))
                    Text(
                        comparisonData.insightText,
                        color = bannerColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Daily Expense Comparison Dual Bars
            Text("Expense by weekday (period total)", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height - 18.dp.toPx()
                    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

                    val groupW = w / days.size
                    val barW = 6.dp.toPx()

                    days.forEachIndexed { i, _ ->
                        val centerX = (i * groupW) + (groupW / 2f)

                        // This period (Purple)
                        val b1H = h * comparisonData.dailyThis[i]
                        drawRoundRect(
                            color = PurpleAccent,
                            topLeft = Offset(centerX - barW - 1.dp.toPx(), h - b1H),
                            size = Size(barW, b1H),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )

                        // Last period (Slate)
                        val b2H = h * comparisonData.dailyLast[i]
                        drawRoundRect(
                            color = Color(0xFF64748B),
                            topLeft = Offset(centerX + 1.dp.toPx(), h - b2H),
                            size = Size(barW, b2H),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )
                    }
                }

                if (comparisonData.income1 == 0.0 && comparisonData.expense1 == 0.0 &&
                    comparisonData.income2 == 0.0 && comparisonData.expense2 == 0.0) {
                    Text(
                        "No transactions in these comparison periods yet.",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { d ->
                        Text(d, color = TextMuted, fontSize = 8.sp)
                    }
                }
            }

            // Category Wise Comparison
            var showAllCategoryComparisons by remember(currentTab, selectedMonth) { mutableStateOf(false) }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Category Wise Comparison", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (showAllCategoryComparisons) "Show less" else "See all >",
                    color = CyanBrand,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { showAllCategoryComparisons = !showAllCategoryComparisons }
                )
            }

            val categoryComparisons = remember(logs, currentTab, selectedMonth) {
                val today = java.time.LocalDate.now()
                val selectedYearMonth = runCatching { java.time.YearMonth.parse(selectedMonth) }.getOrNull()
                    ?: java.time.YearMonth.from(today)
                val monthEnd = selectedYearMonth.atEndOfMonth()
                val anchor = if (selectedYearMonth == java.time.YearMonth.from(today)) today
                    else if (monthEnd.isAfter(today)) selectedYearMonth.atDay(1) else monthEnd
                val currentRange: Pair<java.time.LocalDate, java.time.LocalDate>
                val previousRange: Pair<java.time.LocalDate, java.time.LocalDate>
                when (currentTab) {
                    "Month" -> {
                        val start = selectedYearMonth.atDay(1)
                        val end = if (selectedYearMonth == java.time.YearMonth.from(today)) today else monthEnd
                        val prevStart = start.minusMonths(1)
                        val prevEnd = prevStart.plusDays((end.dayOfMonth - 1).toLong())
                            .coerceAtMost(prevStart.withDayOfMonth(prevStart.lengthOfMonth()))
                        currentRange = start to end
                        previousRange = prevStart to prevEnd
                    }
                    "Year" -> {
                        val start = selectedYearMonth.atDay(1).withDayOfYear(1)
                        val end = if (selectedYearMonth == java.time.YearMonth.from(today)) today else monthEnd
                        currentRange = start to end
                        previousRange = start.minusYears(1) to end.minusYears(1)
                    }
                    "Custom" -> {
                        val start = anchor.minusDays(29)
                        currentRange = start to anchor
                        previousRange = start.minusDays(30) to start.minusDays(1)
                    }
                    else -> {
                        val start = anchor.minusDays(6)
                        currentRange = start to anchor
                        previousRange = start.minusDays(7) to start.minusDays(1)
                    }
                }
                fun categoryTotals(range: Pair<java.time.LocalDate, java.time.LocalDate>): Map<String, Double> =
                    logs.filter { entry ->
                        entry.category.equals("Expense", true) &&
                            runCatching { java.time.LocalDate.parse(entry.date) }.getOrNull()?.let { date ->
                                !date.isBefore(range.first) && !date.isAfter(range.second)
                            } == true
                    }.groupBy { it.moneyCategory.ifBlank { "Other" } }
                        .mapValues { (_, entries) -> entries.sumOf { MoneyAmounts.value(it.amount) } }

                val current = categoryTotals(currentRange)
                val previous = categoryTotals(previousRange)
                (current.keys + previous.keys).distinct()
                    .sortedByDescending { (current[it] ?: 0.0) + (previous[it] ?: 0.0) }
                    .map { category ->
                        val nowAmount = current[category] ?: 0.0
                        val oldAmount = previous[category] ?: 0.0
                        val change = if (oldAmount <= 0.0) "No baseline" else {
                            val pct = ((nowAmount - oldAmount) / oldAmount * 100).toInt()
                            "${if (pct > 0) "+" else ""}$pct%"
                        }
                        Triple(category, change, change.startsWith("+"))
                    }
            }

            if (categoryComparisons.isEmpty()) {
                Text("No expense category records for these date ranges yet.", color = TextMuted, fontSize = 11.sp)
            }
            val visibleCategoryComparisons = if (showAllCategoryComparisons) categoryComparisons else categoryComparisons.take(6)
            visibleCategoryComparisons.forEach { (cat, change, isIncrease) ->
                val icon = MoneyCategory.iconFor(cat)
                val color = MoneyCategory.colorFor(cat)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectCategory(cat) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(color.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                    }

                    Text(cat, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))

                    // Change indicator below is derived from recorded amounts; no synthetic sparkline.
                    Text(
                        if (change == "No baseline") change else "${if (isIncrease) "↑ " else "↓ "}$change",
                        color = if (change == "No baseline") TextMuted else if (isIncrease) CoralExpense else EmeraldIncome,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private data class ComparisonSummary(
    val income1: Double,
    val expense1: Double,
    val savings1: Double,
    val income2: Double,
    val expense2: Double,
    val savings2: Double,
    val label1: String,
    val label2: String,
    val dailyThis: List<Float>,
    val dailyLast: List<Float>,
    val insightText: String,
    val isHigherSavings: Boolean
)

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

// ==========================================
// 3. BUDGETS TAB (OVERALL & CATEGORY)
// ==========================================
@Composable
private fun MoneyBudgetsTab(
    logs: List<LogEntry>,
    selectedMonth: String,
    refreshKey: Int,
    budgetsSubTab: String,
    onBudgetsSubTabChange: (String) -> Unit,
    overallBudget: Double,
    onEditOverallBudget: () -> Unit,
    onAddCategoryBudget: () -> Unit,
    onEditCategoryBudget: (String) -> Unit
) {
    val currentMonthExpenses = remember(logs, selectedMonth, refreshKey) {
        logs.filter { it.category == "Expense" && it.date.startsWith(selectedMonth) }
            .sumOf { MoneyAmounts.value(it.amount) }
    }
    val remainingBudget = if (overallBudget > 0.0) (overallBudget - currentMonthExpenses).coerceAtLeast(0.0) else 0.0
    val budgetProgress = if (overallBudget > 0.0) (currentMonthExpenses / overallBudget).toFloat().coerceIn(0f, 1f) else 0f

    val daysLeftInMonth = remember(selectedMonth, refreshKey) {
        val monthDate = runCatching { SimpleDateFormat("yyyy-MM", Locale.US).parse(selectedMonth) }.getOrNull() ?: Date()
        val cal = Calendar.getInstance().apply { time = monthDate }
        val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val selectedCal = Calendar.getInstance()
        val sameMonth = selectedCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            selectedCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH)
        if (sameMonth) (totalDays - selectedCal.get(Calendar.DAY_OF_MONTH)).coerceAtLeast(0) else 0
    }

    val categoryBudgetsList = remember(logs, selectedMonth, MoneyCategory.categoryBudgets, MoneyCategory.monthlyCategoryBudgets, refreshKey) {
        MoneyCategory.categoryBudgetsForMonth(selectedMonth).mapNotNull { (cat, budget) ->
            if (budget <= 0.0) null
            else {
                val spent = logs.filter {
                    it.category == "Expense" &&
                    it.moneyCategory.equals(cat, ignoreCase = true) &&
                    it.date.startsWith(selectedMonth)
                }.sumOf { MoneyAmounts.value(it.amount) }
                val progress = if (budget > 0.0) (spent / budget).toFloat() else 0f
                CategoryBudgetItem(cat, spent, budget, progress)
            }
        }.sortedByDescending { it.budget }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sub-tabs: Category Budgets | Overall Budget
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardInner, RoundedCornerShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Category Budgets", "Overall Budget").forEach { tab ->
                    val isSelected = budgetsSubTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) VioletTab else Color.Transparent)
                            .clickable { onBudgetsSubTabChange(tab) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tab,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Overall Budget Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardBg,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Overall Budget", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Edit Limit",
                            color = CyanBrand,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onEditOverallBudget)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "₹ ${String.format(Locale.US, "%,.0f", currentMonthExpenses)}",
                                color = TextWhite,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                " / ₹ ${String.format(Locale.US, "%,.0f", overallBudget)}",
                                color = TextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text("${(budgetProgress * 100).toInt()}%", color = CyanBrand, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { budgetProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (budgetProgress > 0.9f) CoralExpense else CyanBrand,
                        trackColor = Color(0xFF1E293B)
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "₹ ${String.format(Locale.US, "%,.0f", remainingBudget)} remaining",
                            color = EmeraldIncome,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("$daysLeftInMonth days left", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        // Category Budgets Header
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Category Budgets", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onAddCategoryBudget)
                ) {
                    Icon(Icons.Default.Add, null, tint = CyanBrand, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Add Budget", color = CyanBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (categoryBudgetsList.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(CyanBrand.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = CyanBrand, modifier = Modifier.size(22.dp))
                        }
                        Text("No Category Budgets Set", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Set monthly limits for specific spending categories like Food, Travel, or Shopping.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onAddCategoryBudget,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanBrand),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, null, tint = Color(0xFF0F172A), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Set Category Budget", color = Color(0xFF0F172A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Category Budgets Items
        items(categoryBudgetsList) { item ->
            val icon = MoneyCategory.iconFor(item.category)
            val color = MoneyCategory.colorFor(item.category)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditCategoryBudget(item.category) },
                color = CardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }

                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.category, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${(item.progress * 100).toInt()}%", color = if (item.progress > 0.9f) CoralExpense else TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("₹ ${String.format(Locale.US, "%,.0f", item.spent)} / ₹ ${String.format(Locale.US, "%,.0f", item.budget)}", color = TextMuted, fontSize = 10.sp)
                        }

                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (item.progress > 0.9f) CoralExpense else color,
                            trackColor = Color(0xFF1E293B)
                        )
                    }
                }
            }
        }
    }
}

private data class CategoryBudgetItem(
    val category: String,
    val spent: Double,
    val budget: Double,
    val progress: Float
)

// ==========================================
// 4. QUICK ADD / EDIT TRANSACTION OVERLAY (SCREENS 5 & 6)
// ==========================================
@Composable
private fun QuickAddTransactionOverlay(
    initialType: String,
    existingEntry: LogEntry?,
    onSave: (LogEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onOpenCategoryManagement: () -> Unit,
    onOpenHistory: () -> Unit
) {
    var type by remember { mutableStateOf(initialType) } // "Expense", "Income", "Transfer"
    var amountString by remember { mutableStateOf(existingEntry?.amount ?: "0") }
    var selectedCategory by remember { mutableStateOf(existingEntry?.moneyCategory ?: if (type == "Income") "Salary" else "Food") }
    var selectedDate by remember { mutableStateOf(existingEntry?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var selectedTime by remember {
        mutableStateOf(
            existingEntry?.note?.split(" · ")?.firstOrNull { it.contains("AM", ignoreCase = true) || it.contains("PM", ignoreCase = true) }
                ?: SimpleDateFormat("h:mm a", Locale.US).format(Date())
        )
    }
    var selectedPaymentMethod by remember {
        mutableStateOf(
            if (existingEntry != null && existingEntry.note.contains("UPI")) "UPI"
            else if (existingEntry != null && existingEntry.note.contains("Card")) "Card"
            else if (existingEntry != null && existingEntry.note.contains("Bank")) "Bank"
            else if (existingEntry != null && existingEntry.note.contains("Cash")) "Cash"
            else "UPI"
        )
    }
    var noteInput by remember {
        mutableStateOf(
            existingEntry?.note?.let { n ->
                val parts = n.split(" · ")
                val remaining = parts.filterNot { part ->
                    part.contains("AM", ignoreCase = true) || part.contains("PM", ignoreCase = true) ||
                    listOf("UPI", "Cash", "Card", "Bank", "Other").contains(part)
                }
                remaining.joinToString(" · ")
            } ?: ""
        )
    }
    var titleInput by remember { mutableStateOf(existingEntry?.title ?: "") }
    var categoriesExpanded by remember { mutableStateOf(true) }
    var keypadVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun openDatePicker() {
        val initial = Calendar.getInstance()
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDate) }
            .getOrNull()?.let { initial.time = it }
        android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun openTimePicker() {
        val initial = Calendar.getInstance()
        runCatching { SimpleDateFormat("h:mm a", Locale.US).parse(selectedTime) }
            .getOrNull()?.let { initial.time = it }
        android.app.TimePickerDialog(
            context,
            { _, hour, minute ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedTime = SimpleDateFormat("h:mm a", Locale.US).format(picked.time)
            },
            initial.get(Calendar.HOUR_OF_DAY),
            initial.get(Calendar.MINUTE),
            false
        ).show()
    }

    fun formatDisplayDate(dateStr: String): String {
        return try {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yestStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
            val pretty = if (parsed != null) SimpleDateFormat("d MMM yyyy", Locale.US).format(parsed) else dateStr
            when (dateStr) {
                todayStr -> "Today, $pretty"
                yestStr -> "Yesterday, $pretty"
                else -> pretty
            }
        } catch (_: Exception) {
            dateStr
        }
    }

    // Keypad button click
    fun handleKeypad(key: String) {
        when (key) {
            "⌫" -> {
                if (amountString.length > 1) amountString = amountString.dropLast(1)
                else amountString = "0"
            }
            "." -> {
                if (!amountString.contains(".")) amountString += "."
            }
            else -> {
                if (amountString == "0") amountString = key
                else if (amountString.length < 9) amountString += key
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
          Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
          ) {
            // Header: Back Arrow | Title | Delete/History icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    if (existingEntry != null) "Edit Transaction" else if (type == "Income") "Add Income" else "Add Expense",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (existingEntry != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = CoralExpense)
                    }
                } else {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, "All Transactions", tint = TextMuted)
                    }
                }
            }

            // Type Switcher: Expense | Income | Transfer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardInner, RoundedCornerShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Expense", "Income", "Transfer").forEach { t ->
                    val isSelected = type == t
                    val activeBg = if (t == "Expense") CoralExpense else if (t == "Income") EmeraldIncome else VioletTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) activeBg else Color.Transparent)
                            .clickable {
                                keypadVisible = false
                                type = t
                                if (t == "Income" && selectedCategory == "Food") selectedCategory = "Salary"
                                else if (t == "Expense" && selectedCategory == "Salary") selectedCategory = "Food"
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Prominent Amount Display: ₹ 180 (x)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { keypadVisible = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "₹ $amountString",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable { amountString = "0" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Clear", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Quick Amount Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(50, 100, 250, 500, 1000, 2000).forEach { preset ->
                    val isChosen = amountString == preset.toString()
                    Surface(
                        color = if (isChosen) VioletTab else CardInner,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isChosen) VioletTab else CardBorder),
                        modifier = Modifier.weight(1f).clickable { amountString = preset.toString(); keypadVisible = false }
                    ) {
                        Text(
                            "₹$preset",
                            color = if (isChosen) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Category Selector Grid
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Category", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = CyanBrand.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable {
                            keypadVisible = false
                            onOpenCategoryManagement()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add category", tint = CyanBrand, modifier = Modifier.size(14.dp))
                            Text("Add", color = CyanBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        if (categoriesExpanded) "Collapse ↑" else "See all >",
                        color = CyanBrand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            keypadVisible = false
                            categoriesExpanded = !categoriesExpanded
                        }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            val allCategories = remember(type, MoneyCategory.customExpenses.size, MoneyCategory.customIncomes.size) {
                MoneyCategory.optionsFor(type).distinct()
            }
            val currentCategories = if (categoriesExpanded) allCategories else allCategories.take(8)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                currentCategories.chunked(4).forEach { categoryRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryRow.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            val icon = MoneyCategory.iconFor(cat)
                            val color = MoneyCategory.colorFor(cat)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) color.copy(alpha = 0.25f) else CardInner)
                                    .border(
                                        1.5.dp,
                                        if (isSelected) color else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedCategory = cat; keypadVisible = false }
                                    .padding(horizontal = 3.dp, vertical = 7.dp)
                            ) {
                                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    cat,
                                    color = TextWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        repeat((4 - categoryRow.size).coerceAtLeast(0)) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Quick Date Presets: Today | Yesterday | Custom
            val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val yestCalObj = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yestDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(yestCalObj.time)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Today" to todayDateStr, "Yesterday" to yestDateStr, "Custom" to null).forEach { (lbl, dateVal) ->
                    val isChosen = if (dateVal != null) selectedDate == dateVal else (selectedDate != todayDateStr && selectedDate != yestDateStr)
                    Surface(
                        color = if (isChosen) VioletTab else CardInner,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isChosen) VioletTab else CardBorder),
                        modifier = Modifier.weight(1f).clickable {
                            keypadVisible = false
                            if (dateVal != null) selectedDate = dateVal
                            else openDatePicker()
                        }
                    ) {
                        Text(
                            lbl,
                            color = if (isChosen) Color.White else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Date & Time Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = CardInner,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .clickable { keypadVisible = false; openDatePicker() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, null, tint = CyanBrand, modifier = Modifier.size(15.dp))
                        Text(
                            formatDisplayDate(selectedDate),
                            color = TextWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Surface(
                    color = CardInner,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .clickable { keypadVisible = false; openTimePicker() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, null, tint = CyanBrand, modifier = Modifier.size(15.dp))
                        Text(
                            selectedTime,
                            color = TextWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Payment Method Section
            Text("Payment Method", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("UPI", "Cash", "Card", "Bank", "Other").forEach { pm ->
                    val isSelected = selectedPaymentMethod == pm
                    Surface(
                        color = if (isSelected) VioletTab else CardInner,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPaymentMethod = pm; keypadVisible = false }
                    ) {
                        Text(
                            pm,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Note (Optional)
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                placeholder = {
                    Text(
                        if (type == "Income") "Note: e.g. Monthly salary" else "Note: e.g. Lunch with friends",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Notes, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    Icon(Icons.Default.Mic, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardInner,
                    unfocusedContainerColor = CardInner,
                    focusedBorderColor = CyanBrand.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .onFocusChanged { focusState -> if (focusState.isFocused) keypadVisible = false }
            )

            Spacer(Modifier.height(8.dp))

            // Save Button
            val activeColor = if (type == "Income") EmeraldIncome else CoralExpense
            Button(
                onClick = {
                    val amountVal = amountString.toDoubleOrNull() ?: 0.0
                    if (amountVal <= 0.0) {
                        Toast.makeText(context, "Please enter an amount greater than 0", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (amountVal > 0.0) {
                        val fullNote = "$selectedTime · $selectedPaymentMethod" + if (noteInput.isNotBlank()) " · $noteInput" else ""
                        val finalTitle = titleInput.ifBlank { selectedCategory }
                        val entry = LogEntry(
                            id = existingEntry?.id ?: UUID.randomUUID().toString(),
                            category = if (type == "Transfer") "Expense" else type,
                            title = finalTitle,
                            amount = String.format(Locale.US, "%.2f", amountVal).trimEnd('0').trimEnd('.'),
                            date = selectedDate,
                            note = fullNote,
                            moneyCategory = selectedCategory
                        )
                        onSave(entry)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = activeColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    if (existingEntry != null) "Update Transaction" else if (type == "Income") "Save Income" else "Save Expense",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
          } // Scrollable transaction details

            if (keypadVisible) {
                Spacer(Modifier.height(8.dp))

                // Numeric keypad is shown only while the amount is active.
                Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(".", "0", "⌫")
                )
                keys.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { key ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clickable { handleKeypad(key) },
                                color = CardInner,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(key, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                }
            }
        }

    }
}

// ==========================================
// 5. CATEGORY MANAGEMENT OVERLAY (SCREEN 7)
// ==========================================
@Composable
private fun CategoryManagementOverlay(
    logs: List<LogEntry>,
    onDismiss: () -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("Expense") } // "Expense", "Income", "Both"
    var searchQuery by remember { mutableStateOf("") }

    val builtInCategories = if (selectedFilter == "Income") MoneyCategory.incomes
    else if (selectedFilter == "Expense") MoneyCategory.expenses
    else (MoneyCategory.expenses + MoneyCategory.incomes).distinct()

    val filteredCategories = remember(builtInCategories, searchQuery) {
        if (searchQuery.isBlank()) builtInCategories
        else builtInCategories.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val customCategories = if (selectedFilter == "Income") MoneyCategory.customIncomes
    else if (selectedFilter == "Expense") MoneyCategory.customExpenses
    else (MoneyCategory.customExpenses + MoneyCategory.customIncomes).distinct()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Text("Categories", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onAddCategory) {
                    Icon(Icons.Default.Add, "Add Category", tint = CyanBrand)
                }
            }

            // Filter Tabs: Expense | Income | Both
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardInner, RoundedCornerShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Expense", "Income", "Both").forEach { f ->
                    val isSelected = selectedFilter == f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) CoralExpense else Color.Transparent)
                            .clickable { selectedFilter = f }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            f,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search categories...", color = TextMuted, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanBrand,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Grid of categories
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredCategories) { cat ->
                    val icon = MoneyCategory.iconFor(cat)
                    val color = MoneyCategory.colorFor(cat)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardInner)
                            .padding(vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                        }
                        Text(cat, color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }

                // Custom Categories Header
                item(span = { GridItemSpan(4) }) {
                    if (customCategories.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Custom Categories", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${customCategories.size} created", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }

                // Custom Categories Items
                items(customCategories.toList()) { cat ->
                    val icon = MoneyCategory.iconFor(cat)
                    val color = MoneyCategory.colorFor(cat)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardInner)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                        }
                        Text(cat, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = { onEditCategory(cat) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, "Edit", tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                        IconButton(
                            onClick = {
                                MoneyCategory.deleteCustom(context, selectedFilter, cat)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Delete", tint = CoralExpense, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Add Custom Category Button
            Button(
                onClick = onAddCategory,
                colors = ButtonDefaults.buttonColors(containerColor = VioletTab),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Add Custom Category", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 6. ADD / EDIT CATEGORY OVERLAY (SCREEN 8)
// ==========================================
@Composable
private fun AddEditCategoryOverlay(
    editingName: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf("Expense") }
    var nameInput by remember { mutableStateOf(editingName ?: "") }
    var selectedIconKey by remember { mutableStateOf("restaurant") }
    var selectedColor by remember { mutableStateOf(MoneyCategory.colorPalette[0]) }
    var includeInBudget by remember { mutableStateOf(true) }
    var budgetInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    if (editingName != null) "Edit Category" else "Add / Edit Category",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = {
                        val trimmed = nameInput.trim()
                        if (trimmed.isNotBlank()) {
                            val limit = budgetInput.toDoubleOrNull()
                            val err = MoneyCategory.addCustom(
                                context = context,
                                transactionType = type,
                                name = trimmed,
                                iconKey = selectedIconKey,
                                colorValue = selectedColor.value.toLong(),
                                budgetLimit = limit
                            )
                            if (err != null) errorText = err
                            else onSaved()
                        }
                    }
                ) {
                    Icon(Icons.Default.Check, "Save", tint = EmeraldIncome)
                }
            }

            // Type Switcher: Expense | Income
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardInner, RoundedCornerShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Expense", "Income").forEach { t ->
                    val isSelected = type == t
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) (if (t == "Expense") CoralExpense else EmeraldIncome) else Color.Transparent)
                            .clickable { type = t }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Icon Picker Grid
            Text("Select Icon", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(130.dp)
            ) {
                items(MoneyCategory.iconChoices) { key ->
                    val isSelected = selectedIconKey == key
                    val icon = MoneyCategory.iconByKey(key)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) selectedColor.copy(alpha = 0.25f) else CardInner)
                            .border(1.5.dp, if (isSelected) selectedColor else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { selectedIconKey = key },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = if (isSelected) selectedColor else TextMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Category Name Field
            Text("Category Name", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = nameInput,
                onValueChange = {
                    nameInput = it
                    errorText = null
                },
                placeholder = { Text("e.g. Project Materials", color = TextMuted) },
                singleLine = true,
                isError = errorText != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanBrand,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (errorText != null) {
                Text(errorText!!, color = CoralExpense, fontSize = 10.sp)
            }

            // Select Color Palette
            Text("Select Color", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MoneyCategory.colorPalette.forEach { c ->
                    val isSelected = selectedColor == c
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                            .clickable { selectedColor = c }
                    )
                }
            }

            // Include in Budget toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Include in Budget", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Track monthly spending for this category", color = TextMuted, fontSize = 10.sp)
                }
                Switch(
                    checked = includeInBudget,
                    onCheckedChange = { includeInBudget = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CyanBrand)
                )
            }

            // Monthly Budget Input
            if (includeInBudget) {
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { budgetInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Monthly Budget (Optional)") },
                    prefix = { Text("₹ ", color = CyanBrand) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanBrand,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.weight(1f))

            // Save Category Button
            Button(
                onClick = {
                    val trimmed = nameInput.trim()
                    if (trimmed.isNotBlank()) {
                        val limit = budgetInput.toDoubleOrNull()
                        val err = MoneyCategory.addCustom(
                            context = context,
                            transactionType = type,
                            name = trimmed,
                            iconKey = selectedIconKey,
                            colorValue = selectedColor.value.toLong(),
                            budgetLimit = if (includeInBudget) limit else null
                        )
                        if (err != null) errorText = err
                        else onSaved()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VioletTab),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Save Category", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 7. CATEGORY DETAIL & SPENDING INSIGHTS
// ==========================================
@Composable
private fun CategoryDetailOverlay(
    categoryName: String,
    logs: List<LogEntry>,
    selectedMonth: String,
    onDismiss: () -> Unit,
    onEditTransaction: (LogEntry) -> Unit
) {
    val icon = MoneyCategory.iconFor(categoryName)
    val color = MoneyCategory.colorFor(categoryName)

    val categoryEntries = remember(logs, categoryName, selectedMonth) {
        logs.filter { it.moneyCategory.equals(categoryName, ignoreCase = true) }
    }
    val totalSpent = remember(categoryEntries) {
        categoryEntries.sumOf { MoneyAmounts.value(it.amount) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(categoryName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Category Spending & Insights", color = TextMuted, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Total Spent Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Total Spent", color = TextMuted, fontSize = 11.sp)
                    Text(
                        "₹ ${String.format(Locale.US, "%,.0f", totalSpent)}",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${categoryEntries.size} recorded transactions",
                        color = CyanBrand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Transactions Header
            Text("Transactions in $categoryName", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (categoryEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions recorded in this category yet.", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(categoryEntries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardInner)
                                .clickable { onEditTransaction(entry) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.title.ifBlank { categoryName }, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("${entry.date} · ${entry.note}", color = TextMuted, fontSize = 9.sp)
                            }
                            Text(
                                "₹ ${entry.amount}",
                                color = if (entry.category == "Income") EmeraldIncome else CoralExpense,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. TRANSACTION HISTORY OVERLAY
// ==========================================
@Composable
private fun TransactionHistoryOverlay(
    logs: List<LogEntry>,
    onDismiss: () -> Unit,
    onEditTransaction: (LogEntry) -> Unit,
    onDeleteTransaction: (LogEntry) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("All") } // "All", "Expense", "Income"
    var paymentMethodFilter by remember { mutableStateOf("All") } // "All", "UPI", "Cash", "Card", "Bank"

    val filteredList = remember(logs, searchQuery, typeFilter, paymentMethodFilter) {
        logs.filter { entry ->
            (entry.category == "Expense" || entry.category == "Income") &&
            (typeFilter == "All" || entry.category == typeFilter) &&
            (paymentMethodFilter == "All" || entry.note.contains(paymentMethodFilter, ignoreCase = true)) &&
            (searchQuery.isBlank() ||
                entry.title.contains(searchQuery, ignoreCase = true) ||
                entry.moneyCategory.contains(searchQuery, ignoreCase = true) ||
                entry.amount.contains(searchQuery, ignoreCase = true) ||
                entry.date.contains(searchQuery, ignoreCase = true))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                Text("Transaction History", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search transactions...", color = TextMuted, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanBrand,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Type Filter Chips: All | Expense | Income
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("All", "Expense", "Income").forEach { t ->
                    val isSelected = typeFilter == t
                    Surface(
                        color = if (isSelected) VioletTab else CardInner,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { typeFilter = t }
                    ) {
                        Text(
                            t,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Payment Method Filter: All | UPI | Card | Bank | Cash
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("All", "UPI", "Card", "Bank", "Cash").forEach { pm ->
                    val isSelected = paymentMethodFilter == pm
                    Surface(
                        color = if (isSelected) CyanBrand.copy(alpha = 0.25f) else CardInner,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isSelected) CyanBrand else Color.Transparent),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { paymentMethodFilter = pm }
                    ) {
                        Text(
                            pm,
                            color = if (isSelected) CyanBrand else TextMuted,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Transactions Count
            Text("${filteredList.size} Transactions", color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))

            // Transaction List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filteredList) { entry ->
                    val isExpense = entry.category == "Expense"
                    val catName = entry.moneyCategory.ifBlank { if (isExpense) "Food" else "Salary" }
                    val icon = MoneyCategory.iconFor(catName)
                    val color = MoneyCategory.colorFor(catName)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardInner)
                            .clickable { onEditTransaction(entry) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                        }

                        Column(Modifier.weight(1f)) {
                            Text(entry.title.ifBlank { catName }, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("${entry.date} · ${entry.note}", color = TextMuted, fontSize = 9.sp)
                        }

                        Text(
                            "${if (isExpense) "-₹" else "+₹"}${entry.amount}",
                            color = if (isExpense) CoralExpense else EmeraldIncome,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
