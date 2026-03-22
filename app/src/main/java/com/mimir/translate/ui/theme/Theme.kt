package com.mimir.translate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE91E63),        // Pink accent for main actions
    onPrimary = Color.White,
    secondary = Color(0xFF4CAF50),       // Green for JLPT badges
    surface = Color(0xFF1A1A2E),         // Dark navy background
    onSurface = Color(0xFFE0E0E0),       // Light text
    background = Color(0xFF0F0F1A),      // Darker background
    onBackground = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF252540),  // Card backgrounds
    onSurfaceVariant = Color(0xFFB0B0B0),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFC2185B),
    onPrimary = Color.White,
    secondary = Color(0xFF2E7D32),
    surface = Color(0xFFF8F8FC),
    onSurface = Color(0xFF1D1D2A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1D1D2A),
    surfaceVariant = Color(0xFFE9E9F2),
    onSurfaceVariant = Color(0xFF5E5E72),
)

@Composable
fun MimirTheme(themeMode: Int, content: @Composable () -> Unit) {
    val useDarkTheme = when (themeMode) {
        0 -> true
        1 -> false
        else -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
