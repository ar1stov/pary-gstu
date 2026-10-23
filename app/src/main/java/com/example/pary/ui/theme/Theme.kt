package com.example.pary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun schemeOf(p: ParyPalette, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentContainer, onPrimaryContainer = p.text,
            secondary = p.practice, secondaryContainer = p.accentContainer, onSecondaryContainer = p.text,
            tertiary = p.lab,
            background = p.background, onBackground = p.text, surface = p.background, onSurface = p.text,
            surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textMuted,
            surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceHigh,
            outline = p.outline, outlineVariant = p.outline, error = p.error,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentContainer, onPrimaryContainer = p.text,
            secondary = p.practice, secondaryContainer = p.accentContainer, onSecondaryContainer = p.text,
            tertiary = p.lab,
            background = p.background, onBackground = p.text, surface = p.background, onSurface = p.text,
            surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textMuted,
            surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceHigh,
            outline = p.outline, outlineVariant = p.outline, error = p.error,
        )
    }

/** Тема приложения: светлая или тёмная палитра; по умолчанию как в системе. */
@Composable
fun ParyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) ParyColors.Dark else ParyColors.Light
    CompositionLocalProvider(LocalParyColors provides palette) {
        MaterialTheme(
            colorScheme = schemeOf(palette, darkTheme),
            typography = Typography,
            content = content,
        )
    }
}
