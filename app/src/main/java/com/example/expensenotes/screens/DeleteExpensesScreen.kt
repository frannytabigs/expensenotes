package com.example.expensenotes.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensenotes.model.NewExpenseModel
import com.example.expensenotes.model.NewExpenseViewModel

@Composable
fun DeleteExpensesScreen(
    viewModel: NewExpenseViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadExpenses() }

    val monthlyData by viewModel.monthlyData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (monthlyData.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
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
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(monthlyData) { month ->
                MonthSectionDelete(month, onDelete = { expense ->
                    viewModel.deleteExpense(expense)
                })
            }
        }
    }
}

@Composable
fun MonthSectionDelete(
    month: MonthGroup,
    onDelete: (NewExpenseModel) -> Unit
) {
    Column {


        month.days.forEach { day ->
            DayCardDelete(day, onDelete)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DayCardDelete(
    day: DayGroup,
    onDelete: (NewExpenseModel) -> Unit
) {
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
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = expense.description,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text =  "\uD83D\uDCB8 " + formatAmount(expense.amount.toDoubleOrNull() ?: 0.0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    IconButton(onClick = { onDelete(expense) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Expense",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}