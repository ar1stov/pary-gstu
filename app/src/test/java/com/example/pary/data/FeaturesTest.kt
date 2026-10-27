package com.example.pary.data

import com.example.pary.notify.Digests
import com.example.pary.notify.Reminders
import com.example.pary.notify.ScheduleSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val monday = Dates.epochDay(2026, 9, 14)

private fun lesson(
    day: Int = 0,
    start: String = "08:20",
    end: String = "09:45",
    subject: String = "Предмет",
    rooms: List<String> = emptyList(),
    number: Int = 1,
) = Lesson(
    dayOfWeek = day, number = number, start = start, end = end, subject = subject, type = "Лекция",
    teachers = listOf("Иванов И.И."), rooms = rooms, weekType = WeekType.ALL,
    firstDay = monday, lastDay = monday + 60,
)

private fun at(day: Long, hour: Int, minute: Int) = Dates.startOfDayMillis(day) + (hour * 60 + minute) * 60_000L

class TravelReminderTest {
    private val lessons = listOf(lesson(), lesson(start = "10:00", end = "11:25", number = 2))

    @Test
    fun firstLessonReminderComesEarlierByTravelTime() {
        val reminder = Reminders.next(lessons, 0, 30, at(monday - 1, 12, 0), ReminderMode.FIRST_ONLY, travelMinutes = 40)!!
        assertEquals(at(monday, 7, 10), reminder.triggerMillis)
        assertEquals(70, reminder.minutesBefore)
        assertEquals(40, reminder.travelMinutes)
    }

    @Test
    fun laterLessonsIgnoreTravelTime() {
        val every = ReminderMode.EVERY_LESSON
        val first = Reminders.next(lessons, 0, 30, at(monday - 1, 12, 0), every, travelMinutes = 40)!!
        val second = Reminders.next(lessons, 0, 30, first.triggerMillis, every, travelMinutes = 40)!!
        assertEquals(at(monday, 9, 45), second.triggerMillis)
        assertEquals(0, second.travelMinutes)
    }

    @Test
    fun noTravelTimeKeepsOldBehaviour() {
        val reminder = Reminders.next(lessons, 0, 30, at(monday - 1, 12, 0))!!
        assertEquals(at(monday, 7, 50), reminder.triggerMillis)
        assertEquals(0, reminder.travelMinutes)
    }
}

class DigestsTest {
    private val lessons = listOf(lesson(), lesson(day = 1, start = "10:00", end = "11:25", number = 2))

    @Test
    fun morningDigestIsAboutTheSameDay() {
        val digest = Digests.next(lessons, 0, Digests.Kind.MORNING, 7 * 60, at(monday - 1, 12, 0))!!
        assertEquals(at(monday, 7, 0), digest.triggerMillis)
        assertEquals(monday, digest.day)
        assertEquals(1, digest.lessons.size)
    }

    @Test
    fun morningDigestSkipsDaysWithoutLessonsAndPastTimes() {
        // понедельник 07:30: сегодняшняя утренняя сводка прошла, ближайшая во вторник
        val digest = Digests.next(lessons, 0, Digests.Kind.MORNING, 7 * 60, at(monday, 7, 30))!!
        assertEquals(at(monday + 1, 7, 0), digest.triggerMillis)
        // со вторника пары идут только в понедельник и вторник: дальше сводка через неделю
        val later = Digests.next(lessons, 0, Digests.Kind.MORNING, 7 * 60, at(monday + 1, 8, 0))!!
        assertEquals(at(monday + 7, 7, 0), later.triggerMillis)
    }

    @Test
    fun eveningDigestIsAboutTomorrow() {
        val digest = Digests.next(lessons, 0, Digests.Kind.EVENING, 20 * 60, at(monday, 12, 0))!!
        // вечером понедельника: завтра вторник, пары есть
        assertEquals(at(monday, 20, 0), digest.triggerMillis)
        assertEquals(monday + 1, digest.day)
    }

    @Test
    fun noLessonsMeansNoDigest() {
        assertNull(Digests.next(emptyList(), 0, Digests.Kind.MORNING, 7 * 60, at(monday, 0, 0)))
    }

    @Test
    fun lessonCountUsesRussianForms() {
        assertEquals("1 пара", Digests.lessonsCount(1))
        assertEquals("2 пары", Digests.lessonsCount(2))
        assertEquals("5 пар", Digests.lessonsCount(5))
        assertEquals("11 пар", Digests.lessonsCount(11))
        assertEquals("21 пара", Digests.lessonsCount(21))
        assertEquals("24 пары", Digests.lessonsCount(24))
    }

    @Test
    fun lessonLineShowsPlaceAndNote() {
        val withRoom = lesson(subject = "Физика", rooms = listOf("2-12"))
        assertEquals("08:20–09:45 · Физика · 2-12", Digests.lessonLine(withRoom, null))
        assertEquals("08:20–09:45 · Физика · 2-12\n    📝 принести тетрадь", Digests.lessonLine(withRoom, "принести тетрадь"))
        assertEquals("08:20–09:45 · Физика", Digests.lessonLine(lesson(subject = "Физика"), " "))
    }
}

class BuildingTransferTest {
    @Test
    fun buildingIsThePartBeforeTheDash() {
        assertEquals("2", buildingOf("2-335"))
        assertEquals("10", buildingOf(" 10-5а "))
        assertNull(buildingOf("Спортзал"))
        assertNull(buildingOf("а-12"))
        assertNull(buildingOf(""))
    }

    @Test
    fun differentBuildingsBetweenLessonsGiveTransfer() {
        val items = dayItems(
            listOf(
                lesson(rooms = listOf("2-335")),
                lesson(start = "10:00", end = "11:25", rooms = listOf("3-12"), number = 2),
            ),
        )
        assertEquals(3, items.size)
        val transfer = items[1] as DayItem.Transfer
        assertEquals("2", transfer.from)
        assertEquals("3", transfer.to)
        assertEquals(15, transfer.minutes)
    }

    @Test
    fun sameBuildingOrUnknownRoomsGiveNoTransfer() {
        val same = dayItems(
            listOf(lesson(rooms = listOf("2-1")), lesson(start = "10:00", end = "11:25", rooms = listOf("2-9"), number = 2)),
        )
        assertTrue(same.none { it is DayItem.Transfer })
        val unknown = dayItems(listOf(lesson(rooms = listOf("2-1")), lesson(start = "10:00", end = "11:25", number = 2)))
        assertTrue(unknown.none { it is DayItem.Transfer })
    }

    @Test
    fun longGapIsAWindowNotATransfer() {
        val items = dayItems(
            listOf(lesson(rooms = listOf("2-1")), lesson(start = "11:30", end = "13:00", rooms = listOf("3-1"), number = 3)),
        )
        assertTrue(items.any { it is DayItem.Gap })
        assertTrue(items.none { it is DayItem.Transfer })
    }

    @Test
    fun simultaneousSubgroupLessonsAreOneBlock() {
        val items = dayItems(
            listOf(
                lesson(rooms = listOf("2-1")),
                lesson(rooms = listOf("3-1")),
                lesson(start = "10:00", end = "11:25", rooms = listOf("3-5"), number = 2),
            ),
        )
        // первый блок в корпусах 2 и 3, второй в 3: общий корпус есть, перехода нет
        assertTrue(items.none { it is DayItem.Transfer })
        assertEquals(3, items.count { it is DayItem.LessonItem })
    }
}

class ScheduleExportTest {
    private val group = GroupInfo("ip-11", "ИП-11", subgroup = 0)

    @Test
    fun escapesSpecialCharacters() {
        assertEquals("а\\;б\\,в\\\\г\\nд", ScheduleExport.escape("а;б,в\\г\nд"))
    }

    @Test
    fun foldsLongLinesWithoutBreakingSymbols() {
        val long = "DESCRIPTION:" + "ж".repeat(60) + "📝" + "я".repeat(30)
        val folded = ScheduleExport.fold(long)
        val parts = folded.split("\r\n")
        assertTrue(parts.size > 1)
        parts.forEachIndexed { i, part ->
            assertTrue("часть $i длиннее 75 байт", part.toByteArray(Charsets.UTF_8).size <= 75)
            if (i > 0) assertTrue(part.startsWith(" "))
        }
        assertEquals(long, parts.joinToString("") { it.removePrefix(" ") })
    }

    @Test
    fun dateTimeIsFloatingLocalTime() {
        assertEquals("20260914T082000", ScheduleExport.dateTime(monday, "08:20"))
        assertNull(ScheduleExport.dateTime(monday, "не время"))
    }

    @Test
    fun calendarHasOneEventPerLessonDay() {
        val lessons = listOf(lesson(subject = "Физика", rooms = listOf("2-12")))
        val ics = ScheduleExport.calendar(group, lessons, monday, 14, nowMillis = 0)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        // две недели: понедельники 14 и 21 сентября
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("DTSTART:20260914T082000\r\n"))
        assertTrue(ics.contains("LOCATION:2-12\r\n"))
        assertTrue(ics.contains("SUMMARY:Физика (Лекция)\r\n"))
    }

    @Test
    fun dayTextListsLessonsAndNotes() {
        val lessons = listOf(lesson(subject = "Физика", rooms = listOf("2-12")))
        val key = NotesRepository.key(group.slug, monday, lessons.first())
        val text = ScheduleExport.dayText(group, monday, lessons, mapOf(key to "тетрадь"))
        assertTrue(text.contains("ИП-11"))
        assertTrue(text.contains("08:20–09:45 Физика (Лекция) · 2-12 · Иванов И.И."))
        assertTrue(text.contains("📝 тетрадь"))
        assertTrue(ScheduleExport.dayText(group, monday + 2, lessons).endsWith("Пар нет"))
    }
}

class ReleaseVersionRuleTest {
    private fun newer(candidate: String, installed: String) = com.example.pary.update.Versions.isNewer(candidate, installed)

    @Test
    fun nextVersionAfter122MustBeComparedPartByPart() {
        // 1.3 и 1.4 приложение считает старше 1.22: плашка обновления у версий 1.2–1.22 не появится
        assertFalse(newer("1.3", "1.22"))
        assertFalse(newer("1.4", "1.22"))
        assertFalse(newer("1.4", "1.21"))
        assertTrue(newer("1.23", "1.22"))
        assertTrue(newer("2.0", "1.22"))
        assertTrue(newer("2.0", "1.4"))
    }
}

class NotesAndGroupsTest {
    @Test
    fun noteKeyContainsDayAndParses() {
        val key = NotesRepository.key("ip-11", monday, lesson(subject = "Физика"))
        assertEquals(monday, NotesRepository.dayOf(key))
        assertNull(NotesRepository.dayOf("мусор"))
    }

    @Test
    fun notesParseTolerantly() {
        assertEquals(emptyMap<String, String>(), NotesRepository.parse(null))
        assertEquals(emptyMap<String, String>(), NotesRepository.parse("не json"))
        assertEquals(mapOf("a" to "b"), NotesRepository.parse("""{"a":"b"}"""))
    }

    @Test
    fun recentGroupsRoundTripAndAreCapped() {
        val groups = (1..7).map { GroupInfo("g$it", "Г-$it", course = it, faculty = "Ф$it") }
        val restored = GroupRepository.parseRecent(GroupRepository.encodeRecent(groups))
        assertEquals(5, restored.size)
        assertEquals(groups.first().copy(subgroup = 0), restored.first())
        assertEquals(emptyList<GroupInfo>(), GroupRepository.parseRecent("сломано"))
    }

    @Test
    fun pushRecentPutsGroupFirstWithoutDuplicates() {
        val a = GroupInfo("a", "А", course = 2, faculty = "Ф")
        val b = GroupInfo("b", "Б")
        val list = GroupRepository.pushRecent(GroupRepository.pushRecent(emptyList(), a, keepExisting = false), b, keepExisting = false)
        assertEquals(listOf("b", "a"), list.map { it.slug })
        // старая запись с курсом сохраняется, если новая о ней ничего не знает
        val again = GroupRepository.pushRecent(list, GroupInfo("a", "А"), keepExisting = true)
        assertEquals(listOf("a", "b"), again.map { it.slug })
        assertEquals(2, again.first().course)
        // новые сведения заменяют старые
        val fresh = GroupRepository.pushRecent(list, GroupInfo("a", "А", course = 3), keepExisting = false)
        assertEquals(3, fresh.first().course)
    }

    @Test
    fun syncIsQuietAtNightOnly() {
        assertTrue(ScheduleSync.isQuietHour(23))
        assertTrue(ScheduleSync.isQuietHour(0))
        assertTrue(ScheduleSync.isQuietHour(5))
        assertFalse(ScheduleSync.isQuietHour(6))
        assertFalse(ScheduleSync.isQuietHour(12))
        assertFalse(ScheduleSync.isQuietHour(22))
    }
}
