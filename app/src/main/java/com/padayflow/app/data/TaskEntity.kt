package com.padayflow.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String = "Pribadi",
    val priority: String = "Normal",
    val status: String = "Belum dikerjakan",
    val createdAt: Long = System.currentTimeMillis()
)
