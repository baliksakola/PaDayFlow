package com.padayflow.app

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

internal val Context.dataStore by preferencesDataStore(name = "padayflow_settings")
internal val THEME_MODE = stringPreferencesKey("theme_mode")

internal val DAILY_PRODUCTIVE_HOURS = intPreferencesKey("daily_productive_hours")

internal val PROTECTED_TIME_BLOCKS = stringPreferencesKey("protected_time_blocks")
internal const val DEFAULT_PROTECTED_TIME_BLOCKS = "12:00-13:00|Makan\n18:00-19:00|Keluarga\n22:00-06:00|Tidur"
