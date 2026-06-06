package com.example.expensenotes.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensenotes.model.NewExpenseViewModel
import kotlin.math.roundToInt

// -------------------------
// SAFE PARSE
// -------------------------
fun String.toSafeDouble(): Double = this.toDoubleOrNull() ?: 0.0

// -------------------------
// FALLBACK DATA MODELS
// -------------------------
data class ExpenseItem(val amount: Double, val description: String, val date: String)

sealed class StatDetail {
    data class Highest(val list: List<ExpenseItem>) : StatDetail()
    data class Lowest(val list: List<ExpenseItem>) : StatDetail()
    data class TopDay(val day: DayGroup) : StatDetail()
}

// State for the new Formula popups
sealed class FormulaStat {
    data class AvgDaily(val total: Double, val days: Int, val result: Double) : FormulaStat()
    data class AvgExpense(val total: Double, val transactions: Int, val result: Double) : FormulaStat()
    data class Median(val median: Double, val transactions: Int) : FormulaStat()
    data class Transactions(val transactions: Int) : FormulaStat()
}

// -------------------------
// MAIN SCREEN
// -------------------------
@Composable
fun StatisticsScreen(
    viewModel: NewExpenseViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadExpenses() }

    val monthlyData by viewModel.monthlyData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var selectedMonth by remember { mutableStateOf<MonthGroup?>(null) }
    var selectedStat by remember { mutableStateOf<StatDetail?>(null) }
    var selectedFormulaStat by remember { mutableStateOf<FormulaStat?>(null) }
    var showLineChart by remember { mutableStateOf(true) }

    val expenseItems = monthlyData.flatMap { month ->
        month.days.flatMap { day ->
            day.expenses.map { expense ->
                ExpenseItem(
                    amount = expense.amount.toSafeDouble(),
                    description = expense.description.ifBlank { "No Description" },
                    date = day.dateDisplay
                )
            }
        }
    }

    val totalExpenses = expenseItems.sumOf { it.amount }

    // --- DYNAMIC THIS MONTH VS LAST MONTH CALCULATION ---
    val thisMonthGroup = monthlyData.firstOrNull()
    val thisMonthExpenses = thisMonthGroup?.monthTotal ?: 0.0
    val thisMonthName = thisMonthGroup?.monthName?.substringBefore(" ") ?: ""
    val thisMonthTitle = if (thisMonthName.isNotEmpty()) "This Month ($thisMonthName)" else "This Month"

    val lastMonthGroup = monthlyData.getOrNull(1)
    val lastMonthExpenses = lastMonthGroup?.monthTotal ?: 0.0
    val lastMonthName = lastMonthGroup?.monthName?.substringBefore(" ") ?: ""

    val trendIcon = when {
        lastMonthGroup == null -> Icons.AutoMirrored.Filled.TrendingFlat
        thisMonthExpenses > lastMonthExpenses -> Icons.AutoMirrored.Filled.TrendingUp
        thisMonthExpenses < lastMonthExpenses -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }

    val trendSubtitle = when {
        lastMonthGroup == null -> "No previous data"
        thisMonthExpenses > lastMonthExpenses -> "Up from last month (${lastMonthName}) (💸 ${formatAmount(thisMonthExpenses - lastMonthExpenses)} Difference)"
        thisMonthExpenses < lastMonthExpenses -> "Down from last month (${lastMonthName}) (💸 ${formatAmount(lastMonthExpenses - thisMonthExpenses)} Difference)"
        else -> "Same as last month (${lastMonthName})"
    }

    // Core Calculations
    val totalDays = monthlyData.sumOf { it.days.size }
    val totalTransactions = expenseItems.size

    val averageDaily = if (totalDays > 0) totalExpenses / totalDays else 0.0
    val averageExpense = if (totalTransactions > 0) totalExpenses / totalTransactions else 0.0

    // Calculate Median Expense
    val sortedAmounts = expenseItems.map { it.amount }.sorted()
    val medianExpense = if (sortedAmounts.isEmpty()) 0.0 else {
        val mid = sortedAmounts.size / 2
        if (sortedAmounts.size % 2 == 0) {
            (sortedAmounts[mid - 1] + sortedAmounts[mid]) / 2.0
        } else {
            sortedAmounts[mid]
        }
    }

    val validExpenses = expenseItems.filter { it.amount > 0.0 }
    val highestExpense = expenseItems.maxOfOrNull { it.amount } ?: 0.0
    val lowestExpense = validExpenses.minOfOrNull { it.amount } ?: 0.0

    val topHighest = expenseItems.sortedByDescending { it.amount }.take(5)
    val topLowest = validExpenses.sortedBy { it.amount }.take(5)

    val mostExpensiveDay = monthlyData
        .flatMap { it.days }
        .maxByOrNull { it.expenses.sumOf { e -> e.amount.toSafeDouble() } }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (monthlyData.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No data available for statistics.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        StatCard(
            title = "Total Expenses",
            value = formatAmount(totalExpenses),
            icon = Icons.Default.Analytics,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(12.dp))

        // Updated This Month Card with Trend Data
        StatCard(
            title = thisMonthTitle,
            value = formatAmount(thisMonthExpenses),
            subtitle = trendSubtitle,
            icon = trendIcon,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )

        Spacer(Modifier.height(16.dp))

        // --- ENHANCED GRID STATS ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallStatCard(
                title = "Avg Daily",
                value = formatAmount(averageDaily),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {
                    selectedFormulaStat = FormulaStat.AvgDaily(totalExpenses, totalDays, averageDaily)
                }
            )
            SmallStatCard(
                title = "Avg / Expense",
                value = formatAmount(averageExpense),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {
                    selectedFormulaStat = FormulaStat.AvgExpense(totalExpenses, totalTransactions, averageExpense)
                }
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallStatCard(
                title = "Median Exp.",
                value = formatAmount(medianExpense),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {
                    selectedFormulaStat = FormulaStat.Median(medianExpense, totalTransactions)
                }
            )
            SmallStatCard(
                title = "Transactions",
                value = totalTransactions.toString(),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {
                    selectedFormulaStat = FormulaStat.Transactions(totalTransactions)
                },
                isCurrency = false
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallStatCard(
                title = "Highest",
                value = formatAmount(highestExpense),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {
                    selectedStat = StatDetail.Highest(topHighest)
                }
            )
            SmallStatCard(
                title = "Lowest",
                value = formatAmount(lowestExpense),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {
                    selectedStat = StatDetail.Lowest(topLowest)
                }
            )
        }
        Spacer(Modifier.height(12.dp))

        SmallStatCard(
            title = "Top Spending Day",
            value = formatAmount(mostExpensiveDay?.dayTotal ?: 0.0),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                mostExpensiveDay?.let { selectedStat = StatDetail.TopDay(it) }
            }
        )

        Spacer(Modifier.height(24.dp))

        // GRAPH SECTION
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Monthly Trend",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        IconButton(onClick = { showLineChart = true }) {
                            Icon(
                                Icons.Default.ShowChart, null,
                                tint = if (showLineChart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(onClick = { showLineChart = false }) {
                            Icon(
                                Icons.Default.BarChart, null,
                                tint = if (!showLineChart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                val chartData = monthlyData.reversed()

                if (showLineChart) {
                    LineChart(chartData) { selectedMonth = it }
                } else {
                    BarChart(chartData) { selectedMonth = it }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    // -------------------------
    // MONTH POPUP
    // -------------------------
    selectedMonth?.let { month ->
        AlertDialog(
            onDismissRequest = { selectedMonth = null },
            confirmButton = { TextButton({ selectedMonth = null }) { Text("Close") } },
            title = { Text(month.monthName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Total: 💸 ${formatAmount(month.monthTotal)}", fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    month.days.forEach { day ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• ${day.dateDisplay}")
                            Text("💸 ${formatAmount(day.dayTotal)}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        )
    }

    // -------------------------
    // STAT LIST POPUP (Highest, Lowest, TopDay)
    // -------------------------
    selectedStat?.let { stat ->
        AlertDialog(
            onDismissRequest = { selectedStat = null },
            confirmButton = { TextButton({ selectedStat = null }) { Text("Close") } },
            title = {
                Text(
                    text = when (stat) {
                        is StatDetail.Highest -> "Top 5 Highest Expenses"
                        is StatDetail.Lowest -> "Top 5 Lowest Expenses"
                        is StatDetail.TopDay -> "Most Expensive Day"
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (stat) {
                        is StatDetail.Highest -> stat.list.forEach { item ->
                            Column {
                                Text("• ${item.description}: 💸 ${formatAmount(item.amount)}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text("   ${item.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is StatDetail.Lowest -> stat.list.forEach { item ->
                            Column {
                                Text("• ${item.description}: 💸 ${formatAmount(item.amount)}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text("   ${item.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is StatDetail.TopDay -> {
                            val total = stat.day.expenses.sumOf { it.amount.toSafeDouble() }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stat.day.dateDisplay,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    text = "Total Spent: 💸 ${formatAmount(total)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    // -------------------------
    // FORMULA POPUP
    // -------------------------
    selectedFormulaStat?.let { stat ->
        val titleText = when (stat) {
            is FormulaStat.AvgDaily -> "Average Daily Spend"
            is FormulaStat.AvgExpense -> "Average Per Expense"
            is FormulaStat.Median -> "Median Expense"
            is FormulaStat.Transactions -> "Total Transactions"
        }

        val formulaText = when (stat) {
            is FormulaStat.AvgDaily -> "💸 ${formatAmount(stat.total)}\n÷ ${stat.days} days recorded\n= 💸 ${formatAmount(stat.result)} / day"
            is FormulaStat.AvgExpense -> "💸 ${formatAmount(stat.total)}\n÷ ${stat.transactions} transactions\n= 💸 ${formatAmount(stat.result)} / expense"
            is FormulaStat.Median -> "Middle value out of\n${stat.transactions} sorted transactions\n= 💸 ${formatAmount(stat.median)}"
            is FormulaStat.Transactions -> "Sum of all individual\nexpense entries\n= ${stat.transactions} total"
        }

        val infoText = when (stat) {
            is FormulaStat.AvgDaily -> "This shows your typical daily spending rate. It divides your overall total expenses by the number of unique days you've recorded purchases."
            is FormulaStat.AvgExpense -> "This represents the typical amount you spend every time you make a single transaction. It divides your total expenses by the raw number of purchases."
            is FormulaStat.Median -> "The median is the exact middle point of your expenses. Unlike an 'Average', it is not skewed if you have a few unusually massive purchases or extremely tiny ones."
            is FormulaStat.Transactions -> "This is simply the raw count of how many separate expense items you have logged in the application."
        }

        AlertDialog(
            onDismissRequest = { selectedFormulaStat = null },
            confirmButton = {
                TextButton(onClick = { selectedFormulaStat = null }) { Text("Got it") }
            },
            icon = { Icon(Icons.Default.Info, contentDescription = "Info") },
            title = {
                Text(
                    text = titleText,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formulaText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        )
    }
}

// -------------------------
// UI COMPONENTS
// -------------------------
@Composable
fun StatCard(title: String, value: String, subtitle: String? = null, icon: ImageVector, color: Color, contentColor: Color) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = contentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(50.dp)
            ) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = contentColor.copy(alpha = 0.8f))
                Text("💸 $value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = contentColor)

                // New subtitle display matching the card's theme
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SmallStatCard(title: String, value: String, modifier: Modifier, isCurrency: Boolean = true) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val displayText = if (isCurrency) "💸 $value" else value
            Text(displayText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
    }
}

// -------------------------
// CHARTS
// -------------------------
@Composable
fun LineChart(data: List<MonthGroup>, onClick: (MonthGroup) -> Unit) {
    if (data.isEmpty()) return
    val max = (data.maxOfOrNull { it.monthTotal } ?: 0.0).coerceAtLeast(1.0)
    val primaryColor = MaterialTheme.colorScheme.primary

    var animationPlayed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (animationPlayed) 1f else 0f, tween(1000), label = "")
    LaunchedEffect(Unit) { animationPlayed = true }

    Canvas(
        modifier = Modifier.fillMaxWidth().height(180.dp).pointerInput(data) {
            detectTapGestures { offset ->
                val stepX = size.width / if (data.size > 1) (data.size - 1).toFloat() else 1f
                val index = (offset.x / stepX).roundToInt().coerceIn(data.indices)
                onClick(data[index])
            }
        }
    ) {
        val width = size.width
        val height = size.height
        val stepX = if (data.size > 1) width / (data.size - 1) else width
        val path = Path()
        val fillPath = Path()

        data.forEachIndexed { index, month ->
            val x = index * stepX
            val y = height - (((month.monthTotal * progress) / max) * height).toFloat()
            drawCircle(color = primaryColor, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height); fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y); fillPath.lineTo(x, y)
            }
        }
        if (data.isNotEmpty()) {
            fillPath.lineTo(width, height); fillPath.close()
            drawPath(fillPath, Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent)))
            drawPath(path, primaryColor, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun BarChart(data: List<MonthGroup>, onClick: (MonthGroup) -> Unit) {
    if (data.isEmpty()) return
    val max = (data.maxOfOrNull { it.monthTotal } ?: 0.0).coerceAtLeast(1.0)
    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationPlayed = true }

    Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        data.forEach { month ->
            val ratio = (month.monthTotal / max).toFloat()
            val animatedHeight by animateFloatAsState(if (animationPlayed) ratio else 0f, tween(800), label = "")
            Box(
                Modifier.weight(1f)
                    .fillMaxHeight(animatedHeight.coerceAtLeast(0.01f))
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onClick(month) }
            )
        }
    }
}