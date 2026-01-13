package com.example.expensenotes.screens

import java.time.LocalDate

data class NewExpenseUiState(
    val description: String = "",
    val amount: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val isDatePickerOpen: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)