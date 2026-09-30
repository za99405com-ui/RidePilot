package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val RidePilotDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = DarkBackground,
    primaryContainer = EmeraldPrimaryDark,
    onPrimaryContainer = TextPrimary,
    secondary = AmberAccent,
    onSecondary = DarkBackground,
    tertiary = InfoBlue,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    onError = TextPrimary
)

@Composable
fun RidePilotTheme(
    content: @Composable () -> Unit
) {
    // Provide RTL layout direction for Arabic experience
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = RidePilotDarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
