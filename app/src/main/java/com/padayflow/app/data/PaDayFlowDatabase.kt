package com.padayflow.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, HabitEntity::class, HabitLogEntity::class, NoteEntity::class],
    version = 6,
    exportSchema = false
)
abstract class PaDayFlowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun noteDao(): NoteDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS habits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, targetPerWeek INTEGER NOT NULL DEFAULT 7, createdAt INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS habit_logs (habitId INTEGER NOT NULL, date TEXT NOT NULL, completed INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(habitId, date))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL DEFAULT '', type TEXT NOT NULL DEFAULT 'NOTE', createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN scheduledStartAt INTEGER")
                database.execSQL("ALTER TABLE tasks ADD COLUMN scheduledEndAt INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceRule TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceEndAt INTEGER")
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build().also { INSTANCE = it }
            }
    }
}
