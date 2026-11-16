package com.padayflow.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String = "Pribadi",
    val description: String = "",
    val priority: String = "Normal",
    val status: String = "Belum dikerjakan",
    val startAt: Long? = null,
    val deadlineAt: Long? = null,
    val estimatedMinutes: Int = 0,
    val reminderAt: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledStartAt: Long? = null,
    val scheduledEndAt: Long? = null
)
