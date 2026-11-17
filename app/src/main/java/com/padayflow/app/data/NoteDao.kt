package com.padayflow.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE type = :type ORDER BY updatedAt DESC")
    fun observeByType(type: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun getAllForBackup(): List<NoteEntity>

    @Query("DELETE FROM notes")
    suspend fun clearForRestore()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(items: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}
