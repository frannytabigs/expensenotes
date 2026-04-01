package com.example.expensenotes.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensenotes.model.NewExpenseModel
import com.example.expensenotes.model.NewExpenseViewModel

data class MonthGroup(
    val monthName: String,
    val monthTotal: Double,
    val days: List<DayGroup>
)

data class DayGroup(
    val dateDisplay: String,
    val dayTotal: Double,
    val expenses: List<NewExpenseModel>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewExpensesScreen(
    viewModel: NewExpenseViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadExpenses() }
    val monthlyData by viewModel.monthlyData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // 1. Get the FocusManager to control keyboard/focus state
    val focusManager = LocalFocusManager.current

    // Search query state
    var searchQuery by remember { mutableStateOf("") }

    // Filter logic: Only recalculates when searchQuery or monthlyData changes
    val filteredData by remember(searchQuery, monthlyData) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                monthlyData
            } else {
                val query = searchQuery.lowercase()
                monthlyData.mapNotNull { month ->
                    // Filter days inside the month
                    val filteredDays = month.days.mapNotNull { day ->
                        // Filter expenses inside the day based on description, amount, date, or month name
                        val filteredExpenses = day.expenses.filter { expense ->
                            expense.description.lowercase().contains(query) ||
                                    expense.amount.contains(query) ||
                                    day.dateDisplay.lowercase().contains(query) ||
                                    month.monthName.lowercase().contains(query)
                        }

                        // If the day has matching expenses, keep it and recalculate its total
                        if (filteredExpenses.isNotEmpty()) {
                            day.copy(
                                expenses = filteredExpenses,
                                dayTotal = filteredExpenses.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                            )
                        } else null
                    }

                    // If the month has matching days, keep it and recalculate its total
                    if (filteredDays.isNotEmpty()) {
                        month.copy(
                            days = filteredDays,
                            monthTotal = filteredDays.sumOf { it.dayTotal }
                        )
                    } else null
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 2. Add pointer input to detect taps outside the search bar and clear focus
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
    ) {
        // --- SEARCH BAR ---
        // Only show search bar if there is actual data available to search through
        if (monthlyData.isNotEmpty() || searchQuery.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search expenses") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            focusManager.clearFocus() // Also hide keyboard when clearing search
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }

        // --- MAIN CONTENT ---
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (monthlyData.isEmpty()) {
            // Empty state: No data at all
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(45.dp))
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No recorded expenses yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (filteredData.isEmpty()) {
            // Empty state: Search yielded no results
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No results found for \"$searchQuery\"",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            // List of Expenses
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(filteredData) { month ->
                    MonthSection(month)
                }
            }
        }
    }
}

@Composable
fun MonthSection(month: MonthGroup) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = month.monthName.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "\uD83D\uDCB8 ${formatAmount(month.monthTotal)}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        month.days.forEach { day ->
            DayCard(day)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DayCard(day: DayGroup) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = day.dateDisplay,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            day.expenses.forEach { expense ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = expense.description,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f) // Keeps long descriptions from squishing the amount
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = formatAmount(expense.amount.toDoubleOrNull() ?: 0.0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TOTAL:",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "\uD83D\uDCB8 ${formatAmount(day.dayTotal)}",
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
fun formatAmount(amount: Double): String {
    return String.format("%,.2f", amount)
}