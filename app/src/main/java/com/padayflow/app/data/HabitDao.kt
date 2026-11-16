package com.padayflow.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt DESC")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_logs")
    fun observeLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habits")
    suspend fun getAllHabitsForBackup(): List<HabitEntity>

    @Query("SELECT * FROM habit_logs")
    suspend fun getAllLogsForBackup(): List<HabitLogEntity>

    @Query("DELETE FROM habit_logs")
    suspend fun clearLogsForRestore()

    @Query("DELETE FROM habits")
    suspend fun clearHabitsForRestore()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabitsForRestore(items: List<HabitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogsForRestore(items: List<HabitLogEntity>)

    @Insert
    suspend fun insertHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun removeLog(habitId: Long, date: String)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogsForHabit(habitId: Long)

    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteHabit(habitId: Long)
}
