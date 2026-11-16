package com.padayflow.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PaDayFlowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE tasks ADD COLUMN startAt INTEGER")
                database.execSQL("ALTER TABLE tasks ADD COLUMN deadlineAt INTEGER")
                database.execSQL("ALTER TABLE tasks ADD COLUMN estimatedMinutes INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE tasks ADD COLUMN reminderAt INTEGER")
                database.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: PaDayFlowDatabase? = null

        fun getInstance(context: Context): PaDayFlowDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PaDayFlowDatabase::class.java,
                    "padayflow.db"
                ).addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
