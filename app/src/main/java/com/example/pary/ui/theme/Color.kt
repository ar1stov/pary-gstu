package com.example.pary.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Набор цветов приложения: фон, текст, акценты и метки типов занятий. */
@Immutable
class ParyPalette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    /** Неделя «над чертой» и «под чертой». */
    val above: Color,
    val below: Color,
    val lecture: Color,
    val practice: Color,
    val lab: Color,
    val otherType: Color,
    val noType: Color,
    val error: Color,
)

/** Две палитры: тёмная с яркими акцентами и светлая с более тёмными, чтобы цвета читались на белом. Их же использует виджет. */
object ParyColors {
    val Dark = ParyPalette(
        background = Color(0xFF0E1117),
        surface = Color(0xFF161B25),
        surfaceHigh = Color(0xFF1D2433),
        outline = Color(0xFF2B3347),
        text = Color(0xFFE8EBF2),
        textMuted = Color(0xFF9AA3B5),
        accent = Color(0xFF6C9BFF),
        onAccent = Color(0xFF06142E),
        accentContainer = Color(0xFF1B2C57),
        above = Color(0xFF6C9BFF),
        below = Color(0xFFC08CFF),
        lecture = Color(0xFF6C9BFF),
        practice = Color(0xFF3DDC97),
        lab = Color(0xFFFFB84D),
        otherType = Color(0xFFFF7A9C),
        noType = Color(0xFF5B6478),
        error = Color(0xFFFF6B6B),
    )

    val Light = ParyPalette(
        background = Color(0xFFF0F3F9),
        surface = Color(0xFFFFFFFF),
        surfaceHigh = Color(0xFFFFFFFF),
        outline = Color(0xFFD3DAE6),
        text = Color(0xFF171B26),
        textMuted = Color(0xFF59627A),
        accent = Color(0xFF2B63F0),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFDCE6FF),
        above = Color(0xFF2B63F0),
        below = Color(0xFF8A3FE0),
        lecture = Color(0xFF2B63F0),
        practice = Color(0xFF0E8A55),
        lab = Color(0xFFB26A00),
        otherType = Color(0xFFD1295F),
        noType = Color(0xFF9AA3B5),
        error = Color(0xFFD93025),
    )
}

val LocalParyColors = staticCompositionLocalOf { ParyColors.Dark }

/** Цвета приложения для текущей темы: `MaterialTheme.pary.accent`. */
val MaterialTheme.pary: ParyPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalParyColors.current

/** Цвет метки по типу занятия: лекции, практики и лабораторные различаются с первого взгляда. */
fun lessonAccent(type: String?, palette: ParyPalette): Color {
    val t = type?.lowercase().orEmpty()
    return when {
        "лекц" in t -> palette.lecture
        "практ" in t -> palette.practice
        "лаб" in t -> palette.lab
        t.isEmpty() -> palette.noType
        else -> palette.otherType
    }
}
