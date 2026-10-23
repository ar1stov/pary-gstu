package com.example.pary.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ParyColorScheme = darkColorScheme(
    primary = ParyColors.Accent,
    onPrimary = ParyColors.OnAccent,
    primaryContainer = ParyColors.AccentContainer,
    onPrimaryContainer = ParyColors.Text,
    secondary = ParyColors.Practice,
    secondaryContainer = ParyColors.AccentContainer,
    onSecondaryContainer = ParyColors.Text,
    tertiary = ParyColors.Lab,
    background = ParyColors.Background,
    onBackground = ParyColors.Text,
    surface = ParyColors.Background,
    onSurface = ParyColors.Text,
    surfaceVariant = ParyColors.SurfaceHigh,
    onSurfaceVariant = ParyColors.TextMuted,
    surfaceContainer = ParyColors.Surface,
    surfaceContainerHigh = ParyColors.SurfaceHigh,
    outline = ParyColors.Outline,
    outlineVariant = ParyColors.Outline,
    error = ParyColors.Error,
)

/** Приложение всегда тёмное: так яркие метки типов занятий читаются лучше всего. */
@Composable
fun ParyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ParyColorScheme,
        typography = Typography,
        content = content,
    )
}
