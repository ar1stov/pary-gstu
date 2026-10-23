package com.example.pary.data

enum class ChangeKind { ADDED, REMOVED, CHANGED }

/** Одно изменение расписания между двумя загрузками: пара добавлена, отменена или изменена. */
class ScheduleChange(val kind: ChangeKind, val before: Lesson?, val after: Lesson?) {

    /** Короткое описание для списка и уведомления, например «Изменена · Пн, 2 пара: аудитория: 2-12 → 2-14». */
    fun describe(): String {
        val lesson = after ?: before!!
        return when (kind) {
            ChangeKind.ADDED -> "Добавлена · ${slot(lesson)}: ${brief(lesson)}"
            ChangeKind.REMOVED -> "Отменена · ${slot(lesson)}: ${brief(lesson)}"
            ChangeKind.CHANGED -> "Изменена · ${slot(lesson)}: ${ScheduleDiff.differences(before!!, after!!).joinToString("; ")}"
        }
    }

    private fun slot(l: Lesson): String {
        val week = when (l.weekType) {
            WeekType.ABOVE -> " · над чертой"
            WeekType.BELOW -> " · под чертой"
            WeekType.ALL -> ""
        }
        return "${Dates.DayNames[l.dayOfWeek]}, ${l.number} пара$week"
    }

    private fun brief(l: Lesson): String {
        val rooms = l.rooms.joinToString(", ")
        return if (rooms.isEmpty()) l.subject else "${l.subject} ($rooms)"
    }
}

object ScheduleDiff {

    /**
     * Что изменилось между [old] и [new]. Берём только актуальные пары (не закончившиеся к [today])
     * и только те, что видит студент [subgroup]. Пары на одном и том же месте (день, номер, неделя,
     * подгруппы) считаем одной изменённой парой, а не «отменой и добавлением».
     */
    fun compute(old: List<Lesson>, new: List<Lesson>, today: Long, subgroup: Int = 0): List<ScheduleChange> {
        fun relevant(list: List<Lesson>) = list.filter { it.lastDay >= today && it.isForSubgroup(subgroup) }.toSet()

        val oldSet = relevant(old)
        val newSet = relevant(new)
        val removed = (oldSet - newSet).toMutableList()
        val changes = mutableListOf<ScheduleChange>()

        for (added in newSet - oldSet) {
            val match = removed.firstOrNull {
                it.dayOfWeek == added.dayOfWeek && it.number == added.number &&
                    it.weekType == added.weekType && it.subgroups == added.subgroups
            }
            if (match == null) {
                changes += ScheduleChange(ChangeKind.ADDED, null, added)
            } else {
                removed.remove(match)
                // Если пара отличается только порядком преподавателей или аудиторий, это не изменение.
                if (differences(match, added).isNotEmpty()) changes += ScheduleChange(ChangeKind.CHANGED, match, added)
            }
        }
        removed.forEach { changes += ScheduleChange(ChangeKind.REMOVED, it, null) }

        return changes.sortedWith(compareBy({ (it.after ?: it.before)!!.dayOfWeek }, { (it.after ?: it.before)!!.number }))
    }

    /** Что именно поменялось в паре: пустой список, если ничего заметного. */
    fun differences(a: Lesson, b: Lesson): List<String> = buildList {
        if (a.subject != b.subject) add("${a.subject} → ${b.subject}")
        if (a.type != b.type) add("тип: ${a.type ?: "—"} → ${b.type ?: "—"}")
        if (a.teachers.sorted() != b.teachers.sorted()) add("преподаватель: ${names(a.teachers)} → ${names(b.teachers)}")
        if (a.rooms.sorted() != b.rooms.sorted()) add("аудитория: ${names(a.rooms)} → ${names(b.rooms)}")
        if (a.start != b.start || a.end != b.end) add("время: ${a.start}–${a.end} → ${b.start}–${b.end}")
        if (a.firstDay != b.firstDay || a.lastDay != b.lastDay) add("период: ${period(a)} → ${period(b)}")
    }

    private fun names(list: List<String>) = list.joinToString(", ").ifEmpty { "—" }

    private fun period(l: Lesson): String {
        fun label(day: Long) = Dates.civil(day).let { (_, month, d) -> "%02d.%02d".format(d, month) }
        return "${label(l.firstDay)}–${label(l.lastDay)}"
    }
}
