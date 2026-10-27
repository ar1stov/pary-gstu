package com.example.pary.data

/** Когда идёт пара: каждую неделю или только на верхней/нижней. */
enum class WeekType { ALL, ABOVE, BELOW }

data class Lesson(
    /** 0 — понедельник, 5 — суббота. */
    val dayOfWeek: Int,
    val number: Int,
    val start: String,
    val end: String,
    val subject: String,
    val type: String?,
    val teachers: List<String>,
    val rooms: List<String>,
    val weekType: WeekType,
    /** Первый и последний день, в которые пара действует (epoch day). */
    val firstDay: Long,
    val lastDay: Long,
    /** Для суббот: номера недель месяца (1..4), по которым идёт пара; пусто — каждую. */
    val saturdayWeeks: Set<Int> = emptySet(),
    /** Номера подгрупп, для которых идёт пара; пусто — для всей группы. */
    val subgroups: Set<Int> = emptySet(),
)

/** Подходит ли пара студенту подгруппы [subgroup]; 0 — подгруппа не выбрана, видны все пары. */
fun Lesson.isForSubgroup(subgroup: Int): Boolean =
    subgroup == 0 || subgroups.isEmpty() || subgroup in subgroups

/** Идёт ли эта пара в указанный день. */
fun Lesson.isOn(day: Long): Boolean {
    if (Dates.dayOfWeek(day) != dayOfWeek) return false
    if (day < firstDay || day > lastDay) return false
    val isAbove = Dates.isAboveWeek(day)
    when (weekType) {
        WeekType.ABOVE -> if (!isAbove) return false
        WeekType.BELOW -> if (isAbove) return false
        WeekType.ALL -> Unit
    }
    if (dayOfWeek == 5 && saturdayWeeks.isNotEmpty()) {
        val dayOfMonth = Dates.civil(day).third
        if ((dayOfMonth + 6) / 7 !in saturdayWeeks) return false
    }
    return true
}

/** Идёт ли пара в указанную минуту суток (начало включительно, конец нет). */
fun Lesson.isRunningAt(minutes: Int): Boolean {
    val from = Dates.minutesOfDay(start) ?: return false
    val to = Dates.minutesOfDay(end) ?: return false
    return minutes in from until to
}

/** Закончилась ли пара к указанной минуте суток. */
fun Lesson.isFinishedAt(minutes: Int): Boolean {
    val to = Dates.minutesOfDay(end) ?: return false
    return minutes >= to
}

/** Пары за день в порядке номеров. */
fun List<Lesson>.on(day: Long, subgroup: Int = 0): List<Lesson> =
    filter { it.isOn(day) && it.isForSubgroup(subgroup) }.sortedWith(compareBy({ it.number }, { it.start }))

/** Первый день начиная с [from] (в пределах [limit] дней), в который есть пары. */
fun List<Lesson>.nextDayWithLessons(from: Long, subgroup: Int = 0, limit: Int = 14): Long? =
    (0..limit).map { from + it }.firstOrNull { day -> any { it.isOn(day) && it.isForSubgroup(subgroup) } }

/**
 * Какой день показывать в виджетах: сегодня, пока остались ещё не закончившиеся пары, а потом ближайший
 * день с парами. Если дальше пар нет, а сегодняшние уже прошли, остаётся сегодня; если пар нет совсем, null.
 */
fun List<Lesson>.dayToShow(today: Long, nowMinutes: Int, subgroup: Int = 0): Long? {
    val todays = on(today, subgroup)
    if (todays.any { !it.isFinishedAt(nowMinutes) }) return today
    return nextDayWithLessons(today + 1, subgroup) ?: today.takeIf { todays.isNotEmpty() }
}

/** Номера подгрупп, которые встречаются в расписании (пусто — деления на подгруппы нет). */
fun List<Lesson>.subgroupNumbers(): List<Int> = flatMap { it.subgroups }.distinct().sorted()

/** Элемент списка дня: пара, окно между парами или переход между корпусами. */
sealed interface DayItem {
    class LessonItem(val lesson: Lesson) : DayItem
    class Gap(val minutes: Int) : DayItem

    /** Следующая пара в другом корпусе: [from] и [to] — номера корпусов, [minutes] — длина перемены. */
    class Transfer(val from: String, val to: String, val minutes: Int) : DayItem
}

/** Номер корпуса из названия аудитории: у «2-335» это «2»; для «Спортзал» и подобных null. */
fun buildingOf(room: String): String? {
    val prefix = room.trim().substringBefore('-', missingDelimiterValue = "")
    return prefix.takeIf { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
}

/** Корпуса, в которых проходят пары (пусто, если у пар нет понятных аудиторий). */
private fun buildingsOf(lessons: List<Lesson>): Set<String> =
    lessons.flatMap { it.rooms }.mapNotNull { buildingOf(it) }.toSet()

/**
 * Пары дня с «окнами» между ними: окном считаем паузу от [minGapMinutes] минут между концом
 * предыдущих пар и началом следующей (обычные перемены до 30 минут окнами не считаются).
 * Пары в одно и то же время (подгруппы) окна не создают.
 * Если следующая пара в другом корпусе, а окна нет, перед ней добавляется [DayItem.Transfer].
 */
fun dayItems(lessons: List<Lesson>, minGapMinutes: Int = 45): List<DayItem> {
    val items = mutableListOf<DayItem>()
    var latestEnd: Int? = null
    var previousGroup: List<Lesson> = emptyList()
    // Пары с одним временем начала (подгруппы) идут одним блоком: переход считаем между блоками.
    for (block in lessons.groupBy { it.start }.values) {
        val start = Dates.minutesOfDay(block.first().start)
        val previousEnd = latestEnd
        if (previousEnd != null && start != null) {
            val gap = start - previousEnd
            if (gap >= minGapMinutes) {
                items += DayItem.Gap(gap)
            } else if (gap >= 0) {
                val from = buildingsOf(previousGroup)
                val to = buildingsOf(block)
                if (from.isNotEmpty() && to.isNotEmpty() && from.intersect(to).isEmpty()) {
                    items += DayItem.Transfer(from.sorted().joinToString("/"), to.sorted().joinToString("/"), gap)
                }
            }
        }
        block.forEach { items += DayItem.LessonItem(it) }
        val blockEnd = block.mapNotNull { Dates.minutesOfDay(it.end) }.maxOrNull()
        if (blockEnd != null) latestEnd = maxOf(previousEnd ?: blockEnd, blockEnd)
        previousGroup = block
    }
    return items
}

/** «40 мин», «2 ч», «1 ч 35 мин». */
fun formatGap(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest мин"
        rest == 0 -> "$hours ч"
        else -> "$hours ч $rest мин"
    }
}

data class GroupInfo(
    val slug: String,
    val name: String,
    val course: Int = 0,
    val faculty: String = "",
    /** Выбранная подгруппа; 0 — все. */
    val subgroup: Int = 0,
)

data class GroupSearchResult(
    val groups: List<GroupInfo>,
    /** true — групп больше, чем вернул сервер; нужно уточнить запрос. */
    val hasMore: Boolean,
)
