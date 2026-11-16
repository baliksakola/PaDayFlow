package com.padayflow.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PaDayFlowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: PaDayFlowDatabase? = null

        fun getInstance(context: Context): PaDayFlowDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PaDayFlowDatabase::class.java,
                    "padayflow.db"
                ).build().also { INSTANCE = it }
            }
    }
}
