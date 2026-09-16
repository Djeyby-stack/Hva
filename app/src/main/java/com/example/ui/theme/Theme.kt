package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = HvaWhite,
    secondary = HvaGreen,
    tertiary = HvaAccent,
    background = HvaBlack,
    surface = HvaSurface,
    surfaceVariant = HvaSurfaceVariant,
    onPrimary = HvaBlack,
    onSecondary = HvaBlack,
    onBackground = HvaWhite,
    onSurface = HvaWhite,
    outline = HvaBorder
)

@Composable
fun HvaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = HvaTheme(darkTheme = darkTheme, content = content)
