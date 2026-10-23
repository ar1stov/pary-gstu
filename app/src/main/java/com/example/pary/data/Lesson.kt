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

/** Номера подгрупп, которые встречаются в расписании (пусто — деления на подгруппы нет). */
fun List<Lesson>.subgroupNumbers(): List<Int> = flatMap { it.subgroups }.distinct().sorted()

/** Элемент списка дня: пара или окно между парами. */
sealed interface DayItem {
    class LessonItem(val lesson: Lesson) : DayItem
    class Gap(val minutes: Int) : DayItem
}

/**
 * Пары дня с «окнами» между ними: окном считаем паузу от [minGapMinutes] минут между концом
 * предыдущих пар и началом следующей (обычные перемены до 30 минут окнами не считаются).
 * Пары в одно и то же время (подгруппы) окна не создают.
 */
fun dayItems(lessons: List<Lesson>, minGapMinutes: Int = 45): List<DayItem> {
    val items = mutableListOf<DayItem>()
    var latestEnd: Int? = null
    for (lesson in lessons) {
        val start = Dates.minutesOfDay(lesson.start)
        val end = Dates.minutesOfDay(lesson.end)
        val previousEnd = latestEnd
        if (previousEnd != null && start != null && start - previousEnd >= minGapMinutes) {
            items += DayItem.Gap(start - previousEnd)
        }
        items += DayItem.LessonItem(lesson)
        if (end != null) latestEnd = maxOf(previousEnd ?: end, end)
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
