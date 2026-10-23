package com.example.pary.data

import java.util.Calendar

/**
 * Даты как число дней от 01.01.1970 ("epoch day"): так их легко сравнивать и складывать,
 * а java.time на minSdk 24 недоступен без дополнительной настройки.
 */
object Dates {
    /** С этой даты сайт sc.gstu.by считает чередование недель: первая неделя — «над чертой». */
    private const val STUDY_START_YEAR = 2025
    private const val STUDY_START_MONTH = 9
    private const val STUDY_START_DAY = 1

    val DayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    val MonthNames = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

    private val DayNamesFull = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    private val MonthNamesGenitive = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    /** «Пятница, 23 октября». */
    fun longLabel(epochDay: Long): String {
        val (_, month, day) = civil(epochDay)
        return "${DayNamesFull[dayOfWeek(epochDay)]}, $day ${MonthNamesGenitive[month - 1]}"
    }

    /** «08:20» -> минуты от полуночи; null, если время не разобрать. */
    fun minutesOfDay(text: String): Int? {
        val parts = text.split(":")
        val hours = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minutes = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return hours * 60 + minutes
    }

    /** Сегодняшний день и время суток (в минутах) для момента [millis] по местному времени. */
    fun todayOf(millis: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return epochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun minutesOfDayAt(millis: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** Местная полночь указанного дня в миллисекундах. */
    fun startOfDayMillis(epochDay: Long): Long {
        val (year, month, day) = civil(epochDay)
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.timeInMillis
    }

    /** «Пт, 23 окт». */
    fun label(epochDay: Long): String =
        "${DayNames[dayOfWeek(epochDay)]}, ${civil(epochDay).third} ${MonthNames[civil(epochDay).second - 1]}"

    fun epochDay(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = Math.floorDiv(y, 400)
        val yoe = y - era * 400
        val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    /** Год, месяц (1..12) и день месяца. */
    fun civil(epochDay: Long): Triple<Int, Int, Int> {
        val z = epochDay + 719468L
        val era = Math.floorDiv(z, 146097L)
        val doe = (z - era * 146097L).toInt()
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = (yoe + era * 400).toInt() + if (month <= 2) 1 else 0
        return Triple(year, month, day)
    }

    /** «2026-09-08» -> число дней; null, если строка не похожа на дату. */
    fun parseIso(text: String?): Long? {
        val parts = text?.take(10)?.split("-") ?: return null
        if (parts.size != 3) return null
        val (y, m, d) = parts.map { it.toIntOrNull() ?: return null }
        return epochDay(y, m, d)
    }

    /** День недели: 0 — понедельник, 6 — воскресенье. */
    fun dayOfWeek(epochDay: Long): Int = Math.floorMod(epochDay + 3L, 7L).toInt()

    fun today(): Long {
        val c = Calendar.getInstance()
        return epochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** true — неделя «над чертой», false — «под чертой». */
    fun isAboveWeek(epochDay: Long): Boolean {
        val start = epochDay(STUDY_START_YEAR, STUDY_START_MONTH, STUDY_START_DAY)
        return Math.floorMod(Math.floorDiv(epochDay - start, 7L), 2L) == 0L
    }
}
