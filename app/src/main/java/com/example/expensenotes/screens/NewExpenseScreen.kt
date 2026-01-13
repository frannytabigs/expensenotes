package com.example.expensenotes.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensenotes.model.NewExpenseViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewExpenseScreen(
    modifier: Modifier = Modifier,
    viewModel: NewExpenseViewModel = viewModel()
) {

    var mToast by remember { mutableStateOf<Toast?>(null) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current


    val state by viewModel.uiState.collectAsState()
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }

            .padding(24.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "New Expense",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = { viewModel.updateDescription(it) },
            label = { Text("Description") },
            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.amount,
            onValueChange = { viewModel.updateAmount(it) },
            label = { Text("Amount") },
            leadingIcon = { Text(
                text = "💸",
                fontSize = 24.sp
            ) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.selectedDate.format(dateFormatter),
            onValueChange = { },
            label = { Text("Date") },
            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
            readOnly = true,
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.openDatePicker() },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                focusManager.clearFocus()

                var msg = "Expense Saved Successfully!"
                if (state.description.isBlank()) msg = "Missing Description"
                else if (state.amount.isBlank()) msg = "Missing Amount"
                else if (state.amount.toDoubleOrNull() == null) msg = "Invalid Amount"
                else viewModel.saveExpense()

                mToast?.cancel()
                mToast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                mToast?.show()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Record Expense", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(50.dp))
    }

    if (state.isDatePickerOpen) {
        val datePickerState = rememberDatePickerState()

        DatePickerDialog(
            onDismissRequest = { viewModel.closeDatePicker() },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val date = LocalDate.ofEpochDay(millis / 86400000)
                        viewModel.selectDate(date)
                    }
                }) { Text("Ok") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeDatePicker() }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}