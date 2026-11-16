package com.padayflow.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE reminderAt IS NOT NULL AND reminderAt > :now AND status NOT IN ('Selesai', 'Dibatalkan')")
    suspend fun getTasksWithPendingReminders(now: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    suspend fun getAllForBackup(): List<TaskEntity>

    @Query("DELETE FROM tasks")
    suspend fun clearForRestore()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(items: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status WHERE id = :taskId")
    suspend fun updateStatus(taskId: Long, status: String)

    @Query("UPDATE tasks SET status = 'Selesai' WHERE id = :taskId AND status NOT IN ('Selesai', 'Dibatalkan')")
    suspend fun completeIfActive(taskId: Long): Int

    @Query("UPDATE tasks SET reminderAt = :reminderAt WHERE id = :taskId AND status NOT IN ('Selesai', 'Dibatalkan')")
    suspend fun updateReminderAtIfActive(taskId: Long, reminderAt: Long?): Int

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun delete(taskId: Long)
}
