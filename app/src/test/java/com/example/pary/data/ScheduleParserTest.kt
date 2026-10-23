package com.example.pary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleParserTest {
    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun parsesGroupList() {
        val result = ScheduleParser.parseGroups(resource("autocomplete.json"))
        assertEquals(10, result.groups.size)
        assertTrue(result.hasMore)
        val first = result.groups.first()
        assertEquals("ap-11", first.slug)
        assertEquals("АП-11", first.name)
        assertEquals(1, first.course)
        assertEquals("Машиностроительный", first.faculty)
    }

    @Test
    fun parsesGroupSchedule() {
        val lessons = ScheduleParser.parseSchedule(resource("group_ap-11.json"))
        assertEquals(26, lessons.size)
        // На сайте EVEN — «над чертой», ODD — «под чертой».
        assertEquals(4, lessons.count { it.weekType == WeekType.ABOVE })
        assertEquals(5, lessons.count { it.weekType == WeekType.BELOW })
        assertEquals(17, lessons.count { it.weekType == WeekType.ALL })
        assertTrue(lessons.all { it.dayOfWeek in 0..4 })
    }

    @Test
    fun readsRealLessonFields() {
        val lessons = ScheduleParser.parseSchedule(resource("group_ap-11.json"))
        val math = lessons.first { it.subject == "Высшая математика" && it.weekType == WeekType.ABOVE }
        assertEquals(0, math.dayOfWeek)
        assertEquals(1, math.number)
        assertEquals("08:20", math.start)
        assertEquals("09:45", math.end)
        assertEquals(listOf("1-228"), math.rooms)
        assertTrue(math.teachers.single().startsWith("Задорожнюк"))
        assertEquals(Dates.epochDay(2026, 9, 8), math.firstDay)
        assertEquals(Dates.epochDay(2026, 12, 28), math.lastDay)
    }

    @Test
    fun lessonWithoutTeacherAndTypeIsStillParsed() {
        val lessons = ScheduleParser.parseSchedule(resource("group_ap-11.json"))
        val curatorHour = lessons.first { it.subject == "Кураторский час" }
        assertEquals(null, curatorHour.type)
        assertEquals("Ильков В.А.", curatorHour.teachers.single())
        assertTrue(lessons.any { it.teachers.isEmpty() && it.rooms.isEmpty() })
    }

    @Test
    fun aboveWeekLessonRunsOnlyOnAboveWeeks() {
        val lessons = ScheduleParser.parseSchedule(resource("group_ap-11.json"))
        val math = lessons.first { it.subject == "Высшая математика" && it.weekType == WeekType.ABOVE }
        val above = Dates.epochDay(2026, 9, 14) // понедельник недели «над чертой»
        val below = Dates.epochDay(2026, 9, 21)
        assertTrue(math.isOn(above))
        assertFalse(math.isOn(below))
        assertFalse(math.isOn(above + 1)) // не тот день недели
        assertFalse(math.isOn(Dates.epochDay(2026, 12, 28) + 14)) // после окончания периода
    }

    @Test(expected = java.io.IOException::class)
    fun errorResponseIsRejected() {
        ScheduleParser.parseSchedule("""{"success":false,"message":"Необходимо пройти авторизацию"}""")
    }
}

class LessonDaysTest {
    private val monday = Dates.epochDay(2026, 9, 14) // неделя «над чертой»

    private fun lesson(day: Int, number: Int, week: WeekType) = Lesson(
        dayOfWeek = day, number = number, start = "0$number:00", end = "0$number:30",
        subject = "Предмет $number", type = null, teachers = emptyList(), rooms = emptyList(),
        weekType = week, firstDay = monday, lastDay = monday + 60,
    )

    @Test
    fun onReturnsLessonsOfTheDayInOrder() {
        val lessons = listOf(lesson(0, 3, WeekType.ALL), lesson(0, 1, WeekType.ALL), lesson(1, 2, WeekType.ALL))
        assertEquals(listOf(1, 3), lessons.on(monday).map { it.number })
    }

    @Test
    fun nextDayWithLessonsSkipsEmptyDaysAndWrongWeeks() {
        val lessons = listOf(lesson(0, 1, WeekType.BELOW)) // понедельник, только «под чертой»
        // Начиная с понедельника «над чертой» ближайшая пара — через неделю.
        assertEquals(monday + 7, lessons.nextDayWithLessons(monday))
        assertEquals(monday + 7, lessons.nextDayWithLessons(monday + 7))
        assertEquals(null, lessons.nextDayWithLessons(monday + 8, limit = 3))
    }
}

class SubgroupTest {
    private fun lessons(): List<Lesson> =
        ScheduleParser.parseSchedule(
            javaClass.classLoader!!.getResourceAsStream("group_ap-11.json")!!.bufferedReader(Charsets.UTF_8).use { it.readText() },
        )

    @Test
    fun parsesSubgroupsOfTheGroupItself() {
        val all = lessons()
        assertEquals(listOf(1, 2), all.subgroupNumbers())
        assertEquals(8, all.count { it.subgroups.isNotEmpty() })
        assertTrue(all.filter { it.subgroups.isNotEmpty() }.all { it.subgroups.size == 1 })
    }

    @Test
    fun subgroupSeesOwnLessonsAndWholeGroupLessons() {
        val all = lessons()
        val first = all.first { it.subgroups == setOf(1) }
        val day = (0..7).map { first.firstDay + it }.first { first.isOn(it) }

        assertTrue(first in all.on(day, subgroup = 1))
        assertFalse(first in all.on(day, subgroup = 2))
        assertTrue(first in all.on(day)) // подгруппа не выбрана — видны все
        val wholeGroup = all.first { it.subgroups.isEmpty() }
        val wholeDay = (0..7).map { wholeGroup.firstDay + it }.first { wholeGroup.isOn(it) }
        assertTrue(wholeGroup in all.on(wholeDay, subgroup = 2))
    }

    @Test
    fun nextDayRespectsSubgroup() {
        val onlyFirst = Lesson(
            dayOfWeek = 2, number = 1, start = "08:20", end = "09:45", subject = "Лаба", type = null,
            teachers = emptyList(), rooms = emptyList(), weekType = WeekType.ALL,
            firstDay = Dates.epochDay(2026, 9, 8), lastDay = Dates.epochDay(2026, 12, 28), subgroups = setOf(1),
        )
        val monday = Dates.epochDay(2026, 9, 14)
        assertEquals(monday + 2, listOf(onlyFirst).nextDayWithLessons(monday, subgroup = 1))
        assertEquals(null, listOf(onlyFirst).nextDayWithLessons(monday, subgroup = 2))
    }
}

class WidgetSchedulerTest {
    private val day = Dates.epochDay(2026, 9, 14) // понедельник

    private fun lesson(start: String, end: String, subgroups: Set<Int> = emptySet()) = Lesson(
        dayOfWeek = 0, number = 1, start = start, end = end, subject = "Предмет", type = null,
        teachers = emptyList(), rooms = emptyList(), weekType = WeekType.ALL,
        firstDay = day, lastDay = day + 60, subgroups = subgroups,
    )

    private val lessons = listOf(lesson("08:20", "09:45"), lesson("10:00", "11:25"))

    @Test
    fun refreshesAtNextLessonBoundary() {
        // 08:00 -> начало первой пары, 09:00 -> её конец, 09:50 -> начало второй
        assertEquals(8 * 60 + 20, com.example.pary.widget.WidgetScheduler.nextRefreshMinute(lessons, 0, day, 8 * 60))
        assertEquals(9 * 60 + 45, com.example.pary.widget.WidgetScheduler.nextRefreshMinute(lessons, 0, day, 9 * 60))
        assertEquals(10 * 60, com.example.pary.widget.WidgetScheduler.nextRefreshMinute(lessons, 0, day, 9 * 60 + 50))
    }

    @Test
    fun refreshesAfterMidnightWhenNothingLeft() {
        assertEquals(24 * 60 + 1, com.example.pary.widget.WidgetScheduler.nextRefreshMinute(lessons, 0, day, 12 * 60))
    }

    @Test
    fun ignoresLessonsOfOtherSubgroup() {
        val mixed = listOf(lesson("08:20", "09:45", setOf(1)), lesson("10:00", "11:25", setOf(2)))
        assertEquals(10 * 60, com.example.pary.widget.WidgetScheduler.nextRefreshMinute(mixed, 2, day, 7 * 60))
    }
}

class RemindersTest {
    private val monday = Dates.epochDay(2026, 9, 14)

    private fun lesson(dayOfWeek: Int, start: String, end: String, subgroups: Set<Int> = emptySet()) = Lesson(
        dayOfWeek = dayOfWeek, number = 1, start = start, end = end, subject = "Предмет", type = null,
        teachers = emptyList(), rooms = emptyList(), weekType = WeekType.ALL,
        firstDay = monday, lastDay = monday + 60, subgroups = subgroups,
    )

    private val lessons = listOf(lesson(0, "08:20", "09:45"), lesson(0, "10:00", "11:25"), lesson(1, "10:00", "11:25"))

    private fun at(day: Long, hour: Int, minute: Int) = Dates.startOfDayMillis(day) + (hour * 60 + minute) * 60_000L

    @Test
    fun remindsBeforeFirstLessonOfTheDay() {
        val reminder = com.example.pary.notify.Reminders.next(lessons, 0, 30, at(monday - 1, 12, 0))!!
        assertEquals(at(monday, 7, 50), reminder.triggerMillis)
        assertEquals("08:20", reminder.firstLesson.start)
        assertEquals(2, reminder.lessonCount)
        assertEquals("11:25", reminder.lastEnd)
    }

    @Test
    fun movesToNextDayWhenTodaysReminderIsPast() {
        val reminder = com.example.pary.notify.Reminders.next(lessons, 0, 30, at(monday, 8, 0))!!
        assertEquals(at(monday + 1, 9, 30), reminder.triggerMillis)
    }

    @Test
    fun skipsEmptyDaysAndNullWhenNoLessons() {
        assertEquals(at(monday + 7, 7, 50), com.example.pary.notify.Reminders.next(lessons, 0, 30, at(monday + 2, 12, 0))!!.triggerMillis)
        assertEquals(null, com.example.pary.notify.Reminders.next(emptyList(), 0, 30, at(monday, 0, 0)))
    }

    @Test
    fun leadTimeAndSubgroupAreRespected() {
        val mixed = listOf(lesson(0, "08:20", "09:45", setOf(1)), lesson(0, "10:00", "11:25", setOf(2)))
        val reminder = com.example.pary.notify.Reminders.next(mixed, 2, 60, at(monday - 1, 12, 0))!!
        assertEquals(at(monday, 9, 0), reminder.triggerMillis)
    }
}

class ScheduleDiffTest {
    private val today = Dates.epochDay(2026, 10, 23)

    private fun lesson(
        day: Int = 0,
        number: Int = 2,
        subject: String = "Физика",
        room: String = "2-12",
        teachers: List<String> = listOf("Иванов И.И."),
        week: WeekType = WeekType.ALL,
        subgroups: Set<Int> = emptySet(),
        lastDay: Long = today + 60,
    ) = Lesson(
        dayOfWeek = day, number = number, start = "10:00", end = "11:25", subject = subject, type = "Лекция",
        teachers = teachers, rooms = listOf(room), weekType = week, firstDay = today - 30, lastDay = lastDay,
        subgroups = subgroups,
    )

    @Test
    fun noChangesWhenScheduleIsTheSame() {
        val list = listOf(lesson(), lesson(day = 1))
        assertEquals(emptyList<ScheduleChange>(), ScheduleDiff.compute(list, list.toList(), today))
    }

    @Test
    fun detectsChangedRoomAndTeacherAsOneChange() {
        val changes = ScheduleDiff.compute(
            listOf(lesson()),
            listOf(lesson(room = "2-14", teachers = listOf("Петров П.П."))),
            today,
        )
        assertEquals(1, changes.size)
        assertEquals(ChangeKind.CHANGED, changes[0].kind)
        val text = changes[0].describe()
        assertTrue(text, "аудитория: 2-12 → 2-14" in text)
        assertTrue(text, "преподаватель: Иванов И.И. → Петров П.П." in text)
        assertTrue(text, text.startsWith("Изменена · Пн, 2 пара"))
    }

    @Test
    fun detectsAddedAndRemovedLessons() {
        val changes = ScheduleDiff.compute(listOf(lesson(day = 1, number = 3)), listOf(lesson(day = 2, number = 1)), today)
        assertEquals(listOf(ChangeKind.REMOVED, ChangeKind.ADDED), changes.map { it.kind })
        assertTrue(changes[0].describe().startsWith("Отменена · Вт, 3 пара"))
        assertTrue(changes[1].describe().startsWith("Добавлена · Ср, 1 пара"))
    }

    @Test
    fun reorderedTeachersAreNotAChange() {
        val a = lesson(teachers = listOf("Иванов И.И.", "Петров П.П."))
        val b = lesson(teachers = listOf("Петров П.П.", "Иванов И.И."))
        assertEquals(0, ScheduleDiff.compute(listOf(a), listOf(b), today).size)
    }

    @Test
    fun expiredLessonsAreIgnored() {
        val expired = lesson(subject = "Старый предмет", lastDay = today - 1)
        assertEquals(0, ScheduleDiff.compute(listOf(expired), emptyList(), today).size)
    }

    @Test
    fun otherSubgroupChangesAreIgnored() {
        val first = lesson(subgroups = setOf(1))
        val changed = lesson(subgroups = setOf(1), room = "2-99")
        assertEquals(1, ScheduleDiff.compute(listOf(first), listOf(changed), today, subgroup = 1).size)
        assertEquals(0, ScheduleDiff.compute(listOf(first), listOf(changed), today, subgroup = 2).size)
        assertEquals(1, ScheduleDiff.compute(listOf(first), listOf(changed), today, subgroup = 0).size)
    }

    @Test
    fun weekTypeIsShownInDescription() {
        val changes = ScheduleDiff.compute(emptyList(), listOf(lesson(week = WeekType.BELOW)), today)
        assertTrue(changes[0].describe().contains("под чертой"))
    }
}

class DayItemsTest {
    private val day = Dates.epochDay(2026, 9, 14)

    private fun lesson(start: String, end: String, subject: String = "Предмет") = Lesson(
        dayOfWeek = 0, number = 1, start = start, end = end, subject = subject, type = null,
        teachers = emptyList(), rooms = emptyList(), weekType = WeekType.ALL,
        firstDay = day, lastDay = day + 60,
    )

    private fun kinds(items: List<DayItem>) = items.map { if (it is DayItem.Gap) "gap:" + it.minutes else "lesson" }

    @Test
    fun ordinaryBreaksAreNotGaps() {
        val items = dayItems(listOf(lesson("08:20", "09:45"), lesson("10:00", "11:25"), lesson("11:35", "13:00"), lesson("13:30", "14:55")))
        assertEquals(listOf("lesson", "lesson", "lesson", "lesson"), kinds(items))
    }

    @Test
    fun skippedPairBecomesGapWithExactLength() {
        val items = dayItems(listOf(lesson("08:20", "09:45"), lesson("13:30", "14:55")))
        assertEquals(listOf("lesson", "gap:225", "lesson"), kinds(items))
    }

    @Test
    fun simultaneousLessonsDoNotCreateGap() {
        // подгруппы: две пары в одно время, потом пара после небольшой перемены
        val items = dayItems(listOf(lesson("10:00", "11:25", "А"), lesson("10:00", "11:25", "Б"), lesson("11:35", "13:00")))
        assertEquals(listOf("lesson", "lesson", "lesson"), kinds(items))
    }

    @Test
    fun gapIsMeasuredFromLatestEndOfPreviousLessons() {
        val items = dayItems(listOf(lesson("08:20", "09:45"), lesson("08:20", "11:25"), lesson("13:30", "14:55")))
        assertEquals(listOf("lesson", "lesson", "gap:125", "lesson"), kinds(items))
    }

    @Test
    fun noGapBeforeFirstLessonOrForEmptyDay() {
        assertEquals(listOf("lesson"), kinds(dayItems(listOf(lesson("13:30", "14:55")))))
        assertEquals(emptyList<DayItem>(), dayItems(emptyList()))
    }

    @Test
    fun formatsGapLength() {
        assertEquals("40 мин", formatGap(40))
        assertEquals("2 ч", formatGap(120))
        assertEquals("1 ч 35 мин", formatGap(95))
    }
}

class DayToShowTest {
    private val monday = Dates.epochDay(2026, 9, 14)

    private fun lesson(day: Int, start: String, end: String) = Lesson(
        dayOfWeek = day, number = 1, start = start, end = end, subject = "Предмет", type = null,
        teachers = emptyList(), rooms = emptyList(), weekType = WeekType.ALL,
        firstDay = monday, lastDay = monday + 60,
    )

    private fun at(hour: Int, minute: Int) = hour * 60 + minute

    private val lessons = listOf(lesson(0, "08:20", "09:45"), lesson(0, "10:00", "11:25"), lesson(1, "10:00", "11:25"))

    @Test
    fun showsTodayWhileSomeLessonIsNotFinished() {
        assertEquals(monday, lessons.dayToShow(monday, at(7, 0)))
        assertEquals(monday, lessons.dayToShow(monday, at(10, 30))) // вторая пара идёт
        assertEquals(monday, lessons.dayToShow(monday, at(9, 50))) // первая закончилась, вторая впереди
    }

    @Test
    fun movesToNextLessonDayWhenTodayIsOver() {
        assertEquals(monday + 1, lessons.dayToShow(monday, at(11, 30)))
        assertEquals(monday + 1, lessons.dayToShow(monday, at(23, 0)))
    }

    @Test
    fun skipsDaysWithoutLessons() {
        assertEquals(monday + 7, lessons.dayToShow(monday + 2, at(9, 0)))
    }

    @Test
    fun staysOnTodayWhenNothingFollows() {
        val single = listOf(lesson(0, "08:20", "09:45").copy(lastDay = monday))
        assertEquals(monday, single.dayToShow(monday, at(12, 0)))
    }

    @Test
    fun returnsNullWithoutAnyLessons() {
        assertEquals(null, emptyList<Lesson>().dayToShow(monday, at(8, 0)))
    }
}
