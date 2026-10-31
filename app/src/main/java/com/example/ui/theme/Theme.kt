package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = KemnakerCyan,
    onPrimary = NavyDeep,
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color.White,
    secondary = AmberGold,
    onSecondary = NavyDeep,
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = AmberLight,
    tertiary = EmeraldSuccess,
    background = NavyDeep,
    onBackground = Color(0xFFF8FAFC),
    surface = NavySurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = NavyCard,
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = RoseDanger
)

private val LightColorScheme = lightColorScheme(
    primary = KemnakerBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = AmberGold,
    onSecondary = Color.White,
    secondaryContainer = AmberLight,
    onSecondaryContainer = Color(0xFF92400E),
    tertiary = KemnakerTeal,
    background = LightBackground,
    onBackground = DarkTextPrimary,
    surface = LightSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    error = RoseDanger
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
