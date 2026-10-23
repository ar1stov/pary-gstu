package com.example.pary.ui.theme

import androidx.compose.ui.graphics.Color

/** Палитра приложения: тёмный фон и яркие акценты. Её же использует виджет. */
object ParyColors {
    val Background = Color(0xFF0E1117)
    val Surface = Color(0xFF161B25)
    val SurfaceHigh = Color(0xFF1D2433)
    val Outline = Color(0xFF2B3347)
    val Text = Color(0xFFE8EBF2)
    val TextMuted = Color(0xFF9AA3B5)

    val Accent = Color(0xFF6C9BFF)
    val OnAccent = Color(0xFF06142E)
    val AccentContainer = Color(0xFF1B2C57)

    /** Неделя «над чертой» и «под чертой». */
    val Above = Color(0xFF6C9BFF)
    val Below = Color(0xFFC08CFF)

    val Lecture = Color(0xFF6C9BFF)
    val Practice = Color(0xFF3DDC97)
    val Lab = Color(0xFFFFB84D)
    val OtherType = Color(0xFFFF7A9C)
    val NoType = Color(0xFF5B6478)

    val Error = Color(0xFFFF6B6B)
}

/** Цвет метки по типу занятия: лекции, практики и лабораторные различаются с первого взгляда. */
fun lessonAccent(type: String?): Color {
    val t = type?.lowercase().orEmpty()
    return when {
        "лекц" in t -> ParyColors.Lecture
        "практ" in t -> ParyColors.Practice
        "лаб" in t -> ParyColors.Lab
        t.isEmpty() -> ParyColors.NoType
        else -> ParyColors.OtherType
    }
}
