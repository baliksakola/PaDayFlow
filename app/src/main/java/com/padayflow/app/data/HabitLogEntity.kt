package com.padayflow.app.data

import androidx.room.Entity

@Entity(tableName = "habit_logs", primaryKeys = ["habitId", "date"])
data class HabitLogEntity(
    val habitId: Long,
    val date: String,
    val completed: Boolean = true
)
