package com.example.pary.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Расписание в виде текста и файла календаря: чтобы поделиться и загрузить в календарь телефона. */
object ScheduleExport {

    /** Текст дня для сообщения: заголовок и по строке на пару. */
    fun dayText(group: GroupInfo, day: Long, lessons: List<Lesson>, notes: Map<String, String> = emptyMap()): String {
        val subgroup = if (group.subgroup != 0) ", подгруппа ${group.subgroup}" else ""
        val header = "${Dates.longLabel(day)} · ${group.name}$subgroup"
        val dayLessons = lessons.on(day, group.subgroup)
        if (dayLessons.isEmpty()) return "$header\nПар нет"
        val lines = dayLessons.map { lesson ->
            val title = listOfNotNull(lesson.subject, lesson.type?.let { "($it)" }).joinToString(" ")
            val details = listOfNotNull(
                lesson.rooms.joinToString(", ").ifEmpty { null },
                lesson.teachers.joinToString(", ").ifEmpty { null },
            ).joinToString(" · ")
            val note = notes[NotesRepository.key(group.slug, day, lesson)]?.let { "\n   📝 $it" }.orEmpty()
            "${lesson.start}–${lesson.end} " + listOf(title, details).filter { it.isNotEmpty() }.joinToString(" · ") + note
        }
        return header + "\n" + lines.joinToString("\n")
    }

    /**
     * Файл календаря (iCalendar) с парами на [days] дней, начиная с [fromDay]. Время «плавающее»,
     * без часового пояса: календарь покажет пару в то же время по часам телефона.
     */
    fun calendar(
        group: GroupInfo,
        lessons: List<Lesson>,
        fromDay: Long,
        days: Int,
        notes: Map<String, String> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val stamp = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(nowMillis))
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Pary GSTU//RU",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:" + escape("Пары ${group.name}"),
        )
        for (day in fromDay until fromDay + days) {
            for (lesson in lessons.on(day, group.subgroup)) {
                val start = dateTime(day, lesson.start) ?: continue
                val end = dateTime(day, lesson.end) ?: continue
                val description = listOfNotNull(
                    lesson.teachers.joinToString(", ").ifEmpty { null },
                    notes[NotesRepository.key(group.slug, day, lesson)]?.let { "Заметка: $it" },
                ).joinToString("\n")
                lines += "BEGIN:VEVENT"
                lines += "UID:${group.slug}-$day-${lesson.start}-${(lesson.subject + lesson.rooms).hashCode().toUInt()}@pary-gstu"
                lines += "DTSTAMP:$stamp"
                lines += "DTSTART:$start"
                lines += "DTEND:$end"
                lines += "SUMMARY:" + escape(listOfNotNull(lesson.subject, lesson.type?.let { "($it)" }).joinToString(" "))
                lesson.rooms.joinToString(", ").ifEmpty { null }?.let { lines += "LOCATION:" + escape(it) }
                if (description.isNotEmpty()) lines += "DESCRIPTION:" + escape(description)
                lines += "END:VEVENT"
            }
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("") { fold(it) + "\r\n" }
    }

    /** «20261027T082000» из дня и времени «08:20»; null, если время не разобрать. */
    fun dateTime(day: Long, time: String): String? {
        val minutes = Dates.minutesOfDay(time) ?: return null
        val (year, month, date) = Dates.civil(day)
        return "%04d%02d%02dT%02d%02d00".format(Locale.US, year, month, date, minutes / 60, minutes % 60)
    }

    /** Экранирование значения по правилам iCalendar: обратная косая, точка с запятой, запятая и перенос строки. */
    fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    /** Длинные строки iCalendar переносятся: не больше 75 байт, продолжение начинается с пробела. */
    fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val out = StringBuilder()
        var bytes = 0
        var limit = 75
        var i = 0
        while (i < line.length) {
            // Символ целиком (эмодзи занимают две позиции строки): его нельзя разрывать между строками.
            val length = Character.charCount(line.codePointAt(i))
            val symbol = line.substring(i, i + length)
            val size = symbol.toByteArray(Charsets.UTF_8).size
            if (bytes + size > limit) {
                out.append("\r\n ")
                bytes = 0
                limit = 74
            }
            out.append(symbol)
            bytes += size
            i += length
        }
        return out.toString()
    }
}
