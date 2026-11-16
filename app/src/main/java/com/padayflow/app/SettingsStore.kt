package com.padayflow.app

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

internal val Context.dataStore by preferencesDataStore(name = "padayflow_settings")
internal val THEME_MODE = stringPreferencesKey("theme_mode")
