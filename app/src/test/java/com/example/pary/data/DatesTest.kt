package com.example.pary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatesTest {
    @Test
    fun epochDayMatchesKnownDates() {
        assertEquals(0L, Dates.epochDay(1970, 1, 1))
        assertEquals(10_957L, Dates.epochDay(2000, 1, 1))
        assertEquals(Triple(2026, 9, 8), Dates.civil(Dates.epochDay(2026, 9, 8)))
        assertEquals(Triple(2028, 2, 29), Dates.civil(Dates.epochDay(2028, 2, 29)))
    }

    @Test
    fun dayOfWeekStartsFromMonday() {
        assertEquals(3, Dates.dayOfWeek(0)) // 1 января 1970 — четверг
        assertEquals(0, Dates.dayOfWeek(Dates.epochDay(2026, 9, 7))) // понедельник
        assertEquals(1, Dates.dayOfWeek(Dates.epochDay(2026, 9, 8))) // вторник
        assertEquals(6, Dates.dayOfWeek(Dates.epochDay(2026, 10, 25))) // воскресенье
    }

    @Test
    fun weeksAlternateFromFirstSeptember2025() {
        assertTrue(Dates.isAboveWeek(Dates.epochDay(2025, 9, 1)))
        assertTrue(Dates.isAboveWeek(Dates.epochDay(2025, 9, 7)))
        assertFalse(Dates.isAboveWeek(Dates.epochDay(2025, 9, 8)))
        assertTrue(Dates.isAboveWeek(Dates.epochDay(2025, 9, 15)))
        // Даты до начала отсчёта тоже чередуются правильно.
        assertFalse(Dates.isAboveWeek(Dates.epochDay(2025, 8, 25)))
        // 19 октября 2026 — понедельник 59-й недели от точки отсчёта, то есть «под чертой».
        assertFalse(Dates.isAboveWeek(Dates.epochDay(2026, 10, 19)))
        assertTrue(Dates.isAboveWeek(Dates.epochDay(2026, 10, 26)))
    }

    @Test
    fun parsesIsoDates() {
        assertEquals(Dates.epochDay(2026, 9, 8), Dates.parseIso("2026-09-08"))
        assertEquals(null, Dates.parseIso("не дата"))
        assertEquals(null, Dates.parseIso(null))
    }
}
