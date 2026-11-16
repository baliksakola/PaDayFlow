package com.padayflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// PaDayFlow uses a calm teal / jade identity, distinct from FinTrack's financial palette.
private val LightColors = lightColorScheme(
    primary = Color(0xFF087F72),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F1E9),
    onPrimaryContainer = Color(0xFF063F39),
    secondary = Color(0xFFB66A32),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3CC),
    onSecondaryContainer = Color(0xFF4A2508),
    tertiary = Color(0xFF526B9A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE5FF),
    onTertiaryContainer = Color(0xFF172D57),
    background = Color(0xFFF4F7F5),
    onBackground = Color(0xFF18211F),
    surface = Color(0xFFFCFDFC),
    onSurface = Color(0xFF18211F),
    surfaceVariant = Color(0xFFE7EEEA),
    onSurfaceVariant = Color(0xFF53615C),
    outline = Color(0xFF87958F),
    outlineVariant = Color(0xFFD5DFDA),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78D7C5),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF07594F),
    onPrimaryContainer = Color(0xFFB6F2E5),
    secondary = Color(0xFFFFB982),
    onSecondary = Color(0xFF4A2508),
    secondaryContainer = Color(0xFF713F1C),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFB5C8FF),
    onTertiary = Color(0xFF23375F),
    tertiaryContainer = Color(0xFF394F79),
    onTertiaryContainer = Color(0xFFDCE5FF),
    background = Color(0xFF101916),
    onBackground = Color(0xFFE1EAE5),
    surface = Color(0xFF17211D),
    onSurface = Color(0xFFE1EAE5),
    surfaceVariant = Color(0xFF293630),
    onSurfaceVariant = Color(0xFFB8C7BF),
    outline = Color(0xFF82938A),
    outlineVariant = Color(0xFF3D4B44),
    error = Color(0xFFFFB4AB)
)

@Composable
fun PaDayFlowTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
