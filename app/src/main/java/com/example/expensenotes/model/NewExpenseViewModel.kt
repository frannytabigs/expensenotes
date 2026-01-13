package com.example.expensenotes.model

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensenotes.screens.DayGroup
import com.example.expensenotes.screens.MonthGroup
import com.example.expensenotes.screens.NewExpenseUiState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class NewExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NewExpenseUiState())
    val uiState: StateFlow<NewExpenseUiState> = _uiState.asStateFlow()

    private val _monthlyData = MutableStateFlow<List<MonthGroup>>(emptyList())
    val monthlyData: StateFlow<List<MonthGroup>> = _monthlyData.asStateFlow()

    @SuppressLint("StaticFieldLeak")
    private val fileName = "expenses.json"
    private val gson = Gson()

    private val folder: File? = getApplication<Application>().getExternalFilesDir(null)

    fun updateDescription(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

    fun updateAmount(newAmount: String) {
        _uiState.update { it.copy(amount = newAmount) }
    }

    fun openDatePicker() {
        _uiState.update { it.copy(isDatePickerOpen = true) }
    }

    fun closeDatePicker() {
        _uiState.update { it.copy(isDatePickerOpen = false) }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date, isDatePickerOpen = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun saveExpense() {
        viewModelScope.launch(Dispatchers.IO) {
            clearError()

            if (folder == null) return@launch

            try {
                val currentState = _uiState.value
                val newExpense = NewExpenseModel(
                    description = currentState.description,
                    amount = currentState.amount,
                    date = currentState.selectedDate.toString()
                )

                val file = File(folder, fileName)
                val mapType = object : TypeToken<MutableMap<String, MutableList<NewExpenseModel>>>() {}.type

                val currentMap: MutableMap<String, MutableList<NewExpenseModel>> = if (file.exists()) {
                    gson.fromJson(file.readText(), mapType) ?: mutableMapOf()
                } else {
                    mutableMapOf()
                }

                val dateKey = newExpense.date
                currentMap.getOrPut(dateKey) { mutableListOf() }.add(newExpense)

                file.writeText(gson.toJson(currentMap))

                _uiState.update { NewExpenseUiState() }
                loadExpenses()

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(errorMessage = "Save failed: ${e.message}") }
            }
        }
    }

    fun loadExpenses() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            if (folder == null) return@launch
            val file = File(folder, fileName)

            if (!folder.exists() || !file.exists()) {
                _monthlyData.value = emptyList()
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            try {
                val jsonString = file.readText()
                val mapType = object : TypeToken<Map<String, List<NewExpenseModel>>>() {}.type
                val rawMap: Map<String, List<NewExpenseModel>> = gson.fromJson(jsonString, mapType) ?: emptyMap()

                val processedList = withContext(Dispatchers.Default) {
                    processExpensesInternal(rawMap)
                }

                _monthlyData.value = processedList

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(errorMessage = "Load failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun processExpensesInternal(rawMap: Map<String, List<NewExpenseModel>>): List<MonthGroup> {
        val dateFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy")
        val monthHeaderFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

        val sortedDates = rawMap.keys.mapNotNull {
            try { LocalDate.parse(it) } catch(e: Exception) { null }
        }.sortedDescending()

        val groupedByMonth = sortedDates.groupBy { YearMonth.from(it) }

        return groupedByMonth.map { (yearMonth, datesInMonth) ->
            val dayGroups = datesInMonth.map { date ->
                val dateString = date.toString()
                val expenses = rawMap[dateString] ?: emptyList()
                val dailySum = expenses.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                DayGroup(
                    dateDisplay = date.format(dateFormatter),
                    dayTotal = dailySum,
                    expenses = expenses
                )
            }
            val monthSum = dayGroups.sumOf { it.dayTotal }
            MonthGroup(
                monthName = yearMonth.format(monthHeaderFormatter),
                monthTotal = monthSum,
                days = dayGroups
            )
        }
    }

    fun deleteExpense(expenseToDelete: NewExpenseModel) {
        viewModelScope.launch(Dispatchers.IO) {
            clearError()
            if (folder == null) return@launch
            val file = File(folder, fileName)

            if (!file.exists()) return@launch

            try {
                val jsonString = file.readText()
                val mapType = object : TypeToken<MutableMap<String, MutableList<NewExpenseModel>>>() {}.type
                val currentMap: MutableMap<String, MutableList<NewExpenseModel>> = gson.fromJson(jsonString, mapType) ?: mutableMapOf()

                val dateKey = expenseToDelete.date
                val expensesOnDay = currentMap[dateKey]

                expensesOnDay?.remove(expenseToDelete)

                if (expensesOnDay.isNullOrEmpty()) {
                    currentMap.remove(dateKey)
                }

                file.writeText(gson.toJson(currentMap))

                loadExpenses()

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(errorMessage = "Delete failed: ${e.message}") }
            }
        }
    }
}