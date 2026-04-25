package com.example.expensenotes.model

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class NewExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ExpenseDatabase.getDatabase(application).expenseDao()
    private val gson = Gson()
    private val folder: File? = application.getExternalFilesDir(null)
    private val jsonFileName = "expenses.json"

    private val _uiState = MutableStateFlow(NewExpenseUiState())
    val uiState: StateFlow<NewExpenseUiState> = _uiState.asStateFlow()

    // NEW: Search Query State handled in the background
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    // 1. Group the raw database data on a background thread (Only runs when DB changes)
    private val allGroupedData = dao.getAllExpenses().map { entityList ->
        withContext(Dispatchers.Default) {
            processExpensesInternal(entityList)
        }
    }

    // 2. Filter the data based on search on a background thread (Runs instantly without freezing UI)
    val monthlyData: StateFlow<List<MonthGroup>> = combine(
        allGroupedData,
        _searchQuery
    ) { groupedData, query ->
        withContext(Dispatchers.Default) {
            if (query.isBlank()) {
                groupedData
            } else {
                val lowerQuery = query.lowercase()
                groupedData.mapNotNull { month ->
                    val filteredDays = month.days.mapNotNull { day ->
                        val filteredExpenses = day.expenses.filter { expense ->
                            expense.description.lowercase().contains(lowerQuery) ||
                                    expense.amount.contains(lowerQuery) ||
                                    day.dateDisplay.lowercase().contains(lowerQuery) ||
                                    month.monthName.lowercase().contains(lowerQuery)
                        }
                        if (filteredExpenses.isNotEmpty()) {
                            day.copy(
                                expenses = filteredExpenses,
                                dayTotal = filteredExpenses.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                            )
                        } else null
                    }
                    if (filteredDays.isNotEmpty()) {
                        month.copy(days = filteredDays, monthTotal = filteredDays.sumOf { it.dayTotal })
                    } else null
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        migrateJsonToRoomIfNeeded(application)
    }

    fun updateDescription(newDescription: String) { _uiState.update { it.copy(description = newDescription) } }
    fun updateAmount(newAmount: String) { _uiState.update { it.copy(amount = newAmount) } }
    fun openDatePicker() { _uiState.update { it.copy(isDatePickerOpen = true) } }
    fun closeDatePicker() { _uiState.update { it.copy(isDatePickerOpen = false) } }
    fun selectDate(date: LocalDate) { _uiState.update { it.copy(selectedDate = date, isDatePickerOpen = false) } }
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }

    fun saveExpense() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _uiState.value
            val newExpense = ExpenseEntity(
                description = currentState.description,
                amount = currentState.amount,
                date = currentState.selectedDate.toString()
            )
            dao.insertExpense(newExpense)

            // Instantly clear UI so there is zero perceived lag
            _uiState.update { NewExpenseUiState() }

            // Run JSON sync silently in the background
            launch(Dispatchers.IO) { syncDatabaseToJson() }
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateExpense(expense)
            launch(Dispatchers.IO) { syncDatabaseToJson() }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteExpense(expense)
            launch(Dispatchers.IO) { syncDatabaseToJson() }
        }
    }

    private suspend fun syncDatabaseToJson() {
        if (folder == null) return
        try {
            val allExpenses = dao.getAllExpensesList()
            val mapToSave = mutableMapOf<String, MutableList<NewExpenseModel>>()

            allExpenses.forEach { entity ->
                val dateKey = entity.date
                val model = NewExpenseModel(entity.description, entity.amount, entity.date)
                mapToSave.getOrPut(dateKey) { mutableListOf() }.add(model)
            }
            val file = File(folder, jsonFileName)
            file.writeText(gson.toJson(mapToSave))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processExpensesInternal(expenses: List<ExpenseEntity>): List<MonthGroup> {
        val dateFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy")
        val monthHeaderFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

        val groupedByDate = expenses.groupBy { it.date }
        val sortedDates = groupedByDate.keys.mapNotNull {
            try { LocalDate.parse(it) } catch(e: Exception) { null }
        }.sortedDescending()

        val groupedByMonth = sortedDates.groupBy { YearMonth.from(it) }

        return groupedByMonth.map { (yearMonth, datesInMonth) ->
            val dayGroups = datesInMonth.map { date ->
                val dateString = date.toString()
                val dailyExpenses = groupedByDate[dateString] ?: emptyList()
                val dailySum = dailyExpenses.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                DayGroup(date.format(dateFormatter), dailySum, dailyExpenses)
            }
            MonthGroup(yearMonth.format(monthHeaderFormatter), dayGroups.sumOf { it.dayTotal }, dayGroups)
        }
    }

    fun loadExpenses() { /* No-op */ }

    private fun migrateJsonToRoomIfNeeded(application: Application) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentDbItems = dao.getAllExpensesList()
            if (currentDbItems.isNotEmpty()) return@launch

            val file = File(folder, jsonFileName)
            if (!file.exists()) return@launch

            try {
                val jsonString = file.readText()
                val mapType = object : TypeToken<Map<String, List<NewExpenseModel>>>() {}.type
                val rawMap: Map<String, List<NewExpenseModel>> = gson.fromJson(jsonString, mapType) ?: emptyMap()

                rawMap.forEach { (_, expenses) ->
                    expenses.forEach { oldExpense ->
                        val newEntity = ExpenseEntity(
                            description = oldExpense.description,
                            amount = oldExpense.amount,
                            date = oldExpense.date
                        )
                        dao.insertExpense(newEntity)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}