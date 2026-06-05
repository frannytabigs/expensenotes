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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
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
    val thisMonthGroup = monthlyData.firstOrNull()
    val thisMonthExpenses = thisMonthGroup?.monthTotal ?: 0.0
    val thisMonthName = thisMonthGroup?.monthName?.substringBefore(" ") ?: ""
    val thisMonthTitle = if (thisMonthName.isNotEmpty()) "This Month ($thisMonthName)" else "This Month"

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
        StatCard(
            title = thisMonthTitle,
            value = formatAmount(thisMonthExpenses),
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )

        Spacer(Modifier.height(16.dp))

        // --- ENHANCED GRID STATS ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallStatCard("Avg Daily", formatAmount(averageDaily), Modifier.weight(1f))
            SmallStatCard("Avg / Expense", formatAmount(averageExpense), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallStatCard("Median Exp.", formatAmount(medianExpense), Modifier.weight(1f))
            SmallStatCard(
                title = "Transactions",
                value = totalTransactions.toString(),
                modifier = Modifier.weight(1f),
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
                // BUG FIX: Added verticalScroll here so long months don't get stuck!
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
    // STAT POPUP
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
                // BUG FIX: Added verticalScroll here as well for safety
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
}

// -------------------------
// UI COMPONENTS
// -------------------------
@Composable
fun StatCard(title: String, value: String, icon: ImageVector, color: Color, contentColor: Color) {
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