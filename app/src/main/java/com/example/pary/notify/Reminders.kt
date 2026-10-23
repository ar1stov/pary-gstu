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
import com.example.pary.data.ScheduleRepository
import com.example.pary.data.SettingsRepository
import com.example.pary.data.on
import kotlinx.coroutines.flow.first

/** Напоминание о первой паре дня: будильник ставится на «время пары минус N минут». */
object Reminders {
    private const val NOTIFICATION_ID = 1
    private const val ACTION = "com.example.pary.REMINDER"

    class Reminder(val triggerMillis: Long, val firstLesson: Lesson, val lessonCount: Int, val lastEnd: String)

    /** Ближайшее напоминание после [nowMillis] (смотрим на две недели вперёд) или null, если пар нет. */
    fun next(lessons: List<Lesson>, subgroup: Int, leadMinutes: Int, nowMillis: Long): Reminder? {
        val today = Dates.todayOf(nowMillis)
        for (i in 0..14) {
            val day = today + i
            val dayLessons = lessons.on(day, subgroup)
            val first = dayLessons.firstOrNull() ?: continue
            val start = Dates.minutesOfDay(first.start) ?: continue
            val trigger = Dates.startOfDayMillis(day) + (start - leadMinutes) * 60_000L
            if (trigger > nowMillis) {
                return Reminder(trigger, first, dayLessons.size, dayLessons.maxOf { it.end })
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
            next(lessons, group.subgroup, settings.reminderLeadMinutes, System.currentTimeMillis())
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
            val due = next(lessons, group.subgroup, settings.reminderLeadMinutes, now - 5 * 60_000L)
            if (due != null && due.triggerMillis <= now + 60_000L) {
                post(context, due, settings.reminderLeadMinutes)
            }
        }
        reschedule(context)
    }

    fun post(context: Context, reminder: Reminder, leadMinutes: Int) {
        Notifications.ensureChannels(context)
        val first = reminder.firstLesson
        val place = first.rooms.joinToString(", ")
        val line = listOfNotNull(first.start, first.subject, place.ifEmpty { null }).joinToString(" · ")
        val summary = context.getString(R.string.reminder_summary, reminder.lessonCount, reminder.lastEnd)
        val lead = if (leadMinutes % 60 == 0) "${leadMinutes / 60} ч" else "$leadMinutes мин"
        val notification = NotificationCompat.Builder(context, Notifications.REMINDERS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lessons)
            .setContentTitle(context.getString(R.string.reminder_title, lead))
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$line\n$summary"))
            .setContentIntent(Notifications.openApp(context))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        Notifications.show(context, NOTIFICATION_ID, notification)
    }

    /** Пример уведомления для кнопки «Показать пример» в настройках. */
    fun postSample(context: Context, leadMinutes: Int) {
        val sample = Lesson(
            dayOfWeek = 0, number = 1, start = "08:20", end = "09:45", subject = "Математический анализ",
            type = "Лекция", teachers = emptyList(), rooms = listOf("2-227"), weekType = com.example.pary.data.WeekType.ALL,
            firstDay = 0, lastDay = 0,
        )
        post(context, Reminder(0, sample, lessonCount = 3, lastEnd = "13:00"), leadMinutes)
    }

    /** Разрешены ли точные будильники (до Android 12 разрешение не требуется). */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
