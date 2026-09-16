package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TermuxWhite,
    secondary = TermuxGreen,
    tertiary = TermuxAccent,
    background = TermuxBlack,
    surface = TermuxSurface,
    surfaceVariant = TermuxSurfaceVariant,
    onPrimary = TermuxBlack,
    onSecondary = TermuxBlack,
    onBackground = TermuxWhite,
    onSurface = TermuxWhite,
    outline = TermuxBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
