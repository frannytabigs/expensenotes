package com.example.expensenotes.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    // Keeps the UI fast and reactive
    @Query("SELECT * FROM expenses_table ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    // NEW: Gets a simple, one-time list of all expenses to save to the JSON file
    @Query("SELECT * FROM expenses_table")
    suspend fun getAllExpensesList(): List<ExpenseEntity>

    // Deletes all current records for a fresh restore
    @Query("DELETE FROM expenses_table") // Ensure "expenses" is your actual table name
    suspend fun deleteAllExpenses()

    // Inserts a whole list of expenses at once
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)
}