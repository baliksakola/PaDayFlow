package com.padayflow.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4656C8),
    onPrimary = Color.White,
    secondary = Color(0xFF6955B8),
    tertiary = Color(0xFF087E9B),
    background = Color(0xFFF7F7FC),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C0FF),
    secondary = Color(0xFFD0C2FF),
    tertiary = Color(0xFF8BD5EA),
    background = Color(0xFF11121A),
    surface = Color(0xFF1A1B25),
    error = Color(0xFFFFB4AB)
)

@Composable
fun PaDayFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
