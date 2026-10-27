package com.example.pary.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.pary.R
import com.example.pary.data.Dates
import com.example.pary.data.GroupRepository
import com.example.pary.data.Lesson
import com.example.pary.data.ReminderMode
import com.example.pary.data.ScheduleRepository
import com.example.pary.data.SettingsRepository
import com.example.pary.data.WeekType
import com.example.pary.data.on
import kotlinx.coroutines.flow.first

/** Напоминания о парах: будильник ставится на «время пары минус N минут». */
object Reminders {
    private const val NOTIFICATION_ID = 1
    private const val ACTION = "com.example.pary.REMINDER"

    /**
     * Одно напоминание: о первой паре дня или (в режиме «каждая пара») о следующей.
     * [lessons] — пары с одним временем начала (у подгрупп они идут одновременно).
     */
    class Reminder(
        val triggerMillis: Long,
        val lessons: List<Lesson>,
        val isFirstOfDay: Boolean,
        val minutesBefore: Int,
        val dayLessonCount: Int,
        val lastEnd: String,
        /** Сколько минут из [minutesBefore] заложено на дорогу (только у первой пары дня). */
        val travelMinutes: Int = 0,
    )

    /**
     * Ближайшее напоминание после [nowMillis] (смотрим на две недели вперёд) или null, если пар нет.
     * В режиме [ReminderMode.EVERY_LESSON] напоминание о следующей паре приходит за [leadMinutes] минут
     * до её начала, но не раньше конца предыдущей: на перемене, а не посреди пары.
     * Напоминание о первой паре дня приходит ещё на [travelMinutes] минут раньше: это время на дорогу.
     */
    fun next(
        lessons: List<Lesson>,
        subgroup: Int,
        leadMinutes: Int,
        nowMillis: Long,
        mode: ReminderMode = ReminderMode.FIRST_ONLY,
        travelMinutes: Int = 0,
    ): Reminder? {
        val today = Dates.todayOf(nowMillis)
        for (i in 0..14) {
            val day = today + i
            val dayLessons = lessons.on(day, subgroup)
            if (dayLessons.isEmpty()) continue
            val dayStart = Dates.startOfDayMillis(day)
            val lastEnd = dayLessons.maxOf { it.end }

            var previousEnd: Int? = null
            var isFirst = true
            // Пары с одним временем начала считаем одним событием.
            for ((start, group) in dayLessons.groupBy { it.start }.toSortedMap()) {
                val startMinute = Dates.minutesOfDay(start) ?: continue
                if (isFirst || mode == ReminderMode.EVERY_LESSON) {
                    val triggerMinute = if (isFirst) {
                        startMinute - leadMinutes - travelMinutes
                    } else {
                        maxOf(startMinute - leadMinutes, previousEnd ?: 0)
                    }
                    val trigger = dayStart + triggerMinute * 60_000L
                    if (trigger > nowMillis) {
                        return Reminder(
                            trigger, group, isFirst, startMinute - triggerMinute, dayLessons.size, lastEnd,
                            travelMinutes = if (isFirst) travelMinutes else 0,
                        )
                    }
                }
                val groupEnd = group.maxOf { Dates.minutesOfDay(it.end) ?: startMinute }
                previousEnd = maxOf(previousEnd ?: groupEnd, groupEnd)
                isFirst = false
            }
        }
        return null
    }

    /** Ставит (или снимает) будильник по текущим настройкам, группе и сохранённому расписанию. */
    suspend fun reschedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        val settings = SettingsRepository(context).current()
        val group = GroupRepository(context).selectedGroup.first()
        val lessons = group?.let { ScheduleRepository(context).cached(it.slug)?.lessons }
        val reminder = if (settings.remindersEnabled && group != null && lessons != null) {
            next(
                lessons, group.subgroup, settings.reminderLeadMinutes, System.currentTimeMillis(),
                settings.reminderMode, settings.travelMinutes,
            )
        } else {
            null
        }
        if (reminder == null) {
            manager.cancel(pending)
            return
        }
        if (canScheduleExact(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerMillis, pending)
        } else {
            // Точные будильники не разрешены: система сработает в окне до ~10 минут после времени.
            manager.setWindow(AlarmManager.RTC_WAKEUP, reminder.triggerMillis, 60_000L, pending)
        }
    }

    /** Будильник сработал: показываем напоминание и ставим следующее. */
    suspend fun fire(context: Context) {
        val settings = SettingsRepository(context).current()
        val group = GroupRepository(context).selectedGroup.first()
        val lessons = group?.let { ScheduleRepository(context).cached(it.slug)?.lessons }
        if (settings.remindersEnabled && group != null && lessons != null) {
            val now = System.currentTimeMillis()
            // Будильник сработал в момент напоминания, поэтому ищем его, отступив на 5 минут назад.
            val due = next(
                lessons, group.subgroup, settings.reminderLeadMinutes, now - 5 * 60_000L, settings.reminderMode,
                settings.travelMinutes,
            )
            if (due != null && due.triggerMillis <= now + 60_000L) {
                post(context, due)
            }
        }
        reschedule(context)
    }

    fun post(context: Context, reminder: Reminder) {
        Notifications.ensureChannels(context)
        val line = describe(reminder.lessons)
        val before = formatMinutes(reminder.minutesBefore)
        val builder = NotificationCompat.Builder(context, Notifications.REMINDERS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lessons)
            .setContentIntent(Notifications.openApp(context))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
        if (reminder.isFirstOfDay) {
            val summary = context.getString(R.string.reminder_summary, reminder.dayLessonCount, reminder.lastEnd)
            val travel = if (reminder.travelMinutes > 0) {
                "\n" + context.getString(R.string.reminder_travel_hint, formatMinutes(reminder.travelMinutes))
            } else {
                ""
            }
            builder.setContentTitle(context.getString(R.string.reminder_title, before))
                .setContentText(line)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$line\n$summary$travel"))
        } else {
            builder.setContentTitle(context.getString(R.string.reminder_next_title, before))
                .setContentText(line)
                .setStyle(NotificationCompat.BigTextStyle().bigText(line))
        }
        Notifications.show(context, NOTIFICATION_ID, builder.build())
    }

    /** Пример уведомления для кнопки «Показать пример» в настройках. */
    fun postSample(context: Context, leadMinutes: Int, mode: ReminderMode) {
        val sample = Lesson(
            dayOfWeek = 0, number = 1, start = "08:20", end = "09:45", subject = "Математический анализ",
            type = "Лекция", teachers = emptyList(), rooms = listOf("2-227"), weekType = WeekType.ALL,
            firstDay = 0, lastDay = 0,
        )
        // В режиме «каждая пара» показываем уведомление о следующей паре: оно бывает чаще.
        val isFirst = mode == ReminderMode.FIRST_ONLY
        post(context, Reminder(0, listOf(sample), isFirst, leadMinutes, dayLessonCount = 3, lastEnd = "13:00"))
    }

    /** Разрешены ли точные будильники (до Android 12 разрешение не требуется). */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** «10:00 · Предмет · 2-12», а для нескольких пар в одно время: «10:00 · А (2-12) / Б (2-14)». */
    fun describe(lessons: List<Lesson>): String {
        val start = lessons.first().start
        if (lessons.size == 1) {
            val lesson = lessons.single()
            val place = lesson.rooms.joinToString(", ")
            return listOfNotNull(start, lesson.subject, place.ifEmpty { null }).joinToString(" · ")
        }
        val parts = lessons.joinToString(" / ") { lesson ->
            val place = lesson.rooms.joinToString(", ")
            if (place.isEmpty()) lesson.subject else "${lesson.subject} ($place)"
        }
        return "$start · $parts"
    }

    /** «15 мин», «1 ч», «1 ч 30 мин». */
    fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "$rest мин"
            rest == 0 -> "$hours ч"
            else -> "$hours ч $rest мин"
        }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
