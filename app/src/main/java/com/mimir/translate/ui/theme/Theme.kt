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

private val PurpleOledColorScheme = darkColorScheme(
    primary = Color(0xFF9333EA),           // Deep violet
    onPrimary = Color.White,
    secondary = Color(0xFF7C3AED),         // Deeper violet
    onSecondary = Color.White,
    tertiary = Color(0xFFC084FC),          // Lighter purple accent
    onTertiary = Color(0xFF1A0830),
    background = Color(0xFF000000),        // True OLED black
    onBackground = Color(0xFFF3E8FF),      // Warm purple-white text
    surface = Color(0xFF000000),           // True OLED black
    onSurface = Color(0xFFF3E8FF),
    surfaceVariant = Color(0xFF1A0830),    // Very dark purple card bg
    onSurfaceVariant = Color(0xFFD8B4FE),  // Soft lavender on dark cards
    error = Color(0xFFCF6679),
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
    val colorScheme = when (themeMode) {
        0 -> DarkColorScheme
        1 -> LightColorScheme
        3 -> PurpleOledColorScheme
        else -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
