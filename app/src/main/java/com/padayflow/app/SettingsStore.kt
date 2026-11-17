package com.padayflow.app

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

internal val Context.dataStore by preferencesDataStore(name = "padayflow_settings")
internal val THEME_MODE = stringPreferencesKey("theme_mode")
internal val TASK_CATEGORIES = stringPreferencesKey("task_categories")
internal const val DEFAULT_TASK_CATEGORIES = "Sekolah – Sarpras\\nSekolah – Administrasi\\nPekerjaan Sampingan\\nPribadi\\nKeluarga"

internal val DAILY_PRODUCTIVE_HOURS = intPreferencesKey("daily_productive_hours")

internal val PROTECTED_TIME_BLOCKS = stringPreferencesKey("protected_time_blocks")
internal const val DEFAULT_PROTECTED_TIME_BLOCKS = "12:00-13:00|Makan\n18:00-19:00|Keluarga\n22:00-06:00|Tidur"

internal val REMINDER_SNOOZE_MINUTES = intPreferencesKey("reminder_snooze_minutes")

internal val WORKDAY_START = stringPreferencesKey("workday_start")
internal val WORKDAY_END = stringPreferencesKey("workday_end")
internal const val DEFAULT_WORKDAY_START = "08:00"
internal const val DEFAULT_WORKDAY_END = "20:00"
