package com.example.pary.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.pary.R
import com.example.pary.data.Dates
import com.example.pary.data.GroupRepository
import com.example.pary.data.Lesson
import com.example.pary.data.NotesRepository
import com.example.pary.data.ScheduleRepository
import com.example.pary.data.SettingsRepository
import com.example.pary.data.nextDayWithLessons
import com.example.pary.data.slotCount
import com.example.pary.data.on
import kotlinx.coroutines.flow.first

/**
 * Сводка дня: утром «что сегодня по парам», вечером «что завтра». Приходит в выбранное время,
 * а в дни без пар не приходит совсем.
 */
object Digests {
    enum class Kind(val requestCode: Int, val notificationId: Int) {
        MORNING(2, 3),
        EVENING(3, 4),
    }

    private const val ACTION = "com.example.pary.DIGEST"
    const val EXTRA_KIND = "kind"

    /** Одна сводка: когда её показать, о каком дне и какие в нём пары. */
    class Digest(val kind: Kind, val triggerMillis: Long, val day: Long, val lessons: List<Lesson>)

    /**
     * Ближайшая сводка после [nowMillis] (смотрим на две недели вперёд) или null, если подходящих дней нет.
     * Утренняя приходит в [minutes] минут от полуночи о том же дне, вечерняя о следующем.
     */
    fun next(lessons: List<Lesson>, subgroup: Int, kind: Kind, minutes: Int, nowMillis: Long): Digest? {
        val today = Dates.todayOf(nowMillis)
        for (i in 0..14) {
            val sendDay = today + i
            val trigger = Dates.startOfDayMillis(sendDay) + minutes * 60_000L
            if (trigger <= nowMillis) continue
            val target = if (kind == Kind.MORNING) sendDay else sendDay + 1
            val dayLessons = lessons.on(target, subgroup)
            if (dayLessons.isNotEmpty()) return Digest(kind, trigger, target, dayLessons)
        }
        return null
    }

    /** «1 пара», «2 пары», «5 пар», «21 пара». */
    fun lessonsCount(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        val word = when {
            mod100 in 11..14 -> "пар"
            mod10 == 1 -> "пара"
            mod10 in 2..4 -> "пары"
            else -> "пар"
        }
        return "$count $word"
    }

    /** «подгр. 2» у пары для части группы, если у студента выбраны все подгруппы; иначе null. */
    private fun subgroupLabel(lesson: Lesson, show: Boolean): String? =
        if (show && lesson.subgroups.isNotEmpty()) "подгр. " + lesson.subgroups.sorted().joinToString(", ") else null

    /** Строка пары в сводке: «08:20–09:45 · Предмет · 2-335», с заметкой, если она есть. */
    fun lessonLine(lesson: Lesson, note: String?, showSubgroups: Boolean = false): String {
        val place = lesson.rooms.joinToString(", ").ifEmpty { null }
        val groups = subgroupLabel(lesson, showSubgroups)
        val base = listOfNotNull("${lesson.start}–${lesson.end}", lesson.subject, place, groups).joinToString(" · ")
        return if (note.isNullOrBlank()) base else "$base\n    📝 $note"
    }

    /** Ставит (или снимает) будильники обеих сводок по текущим настройкам, группе и расписанию. */
    suspend fun reschedule(context: Context) {
        val settings = SettingsRepository(context).current()
        val group = GroupRepository(context).selectedGroup.first()
        val lessons = group?.let { ScheduleRepository(context).cached(it.slug)?.lessons }
        val now = System.currentTimeMillis()
        for (kind in Kind.entries) {
            val enabled = when (kind) {
                Kind.MORNING -> settings.morningDigestEnabled
                Kind.EVENING -> settings.eveningDigestEnabled
            }
            val minutes = if (kind == Kind.MORNING) settings.morningDigestMinutes else settings.eveningDigestMinutes
            val digest = if (enabled && group != null && lessons != null) next(lessons, group.subgroup, kind, minutes, now) else null
            val manager = context.getSystemService(AlarmManager::class.java)
            val pending = alarmIntent(context, kind)
            if (digest == null) {
                manager.cancel(pending)
            } else if (Reminders.canScheduleExact(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, digest.triggerMillis, pending)
            } else {
                manager.setWindow(AlarmManager.RTC_WAKEUP, digest.triggerMillis, 60_000L, pending)
            }
        }
    }

    /** Будильник сработал: показываем сводку и ставим следующие. */
    suspend fun fire(context: Context, kind: Kind) {
        val settings = SettingsRepository(context).current()
        val group = GroupRepository(context).selectedGroup.first()
        val lessons = group?.let { ScheduleRepository(context).cached(it.slug)?.lessons }
        val enabled = if (kind == Kind.MORNING) settings.morningDigestEnabled else settings.eveningDigestEnabled
        if (enabled && group != null && lessons != null) {
            val minutes = if (kind == Kind.MORNING) settings.morningDigestMinutes else settings.eveningDigestMinutes
            val now = System.currentTimeMillis()
            // Будильник сработал в момент сводки, поэтому ищем её, отступив на 5 минут назад.
            val due = next(lessons, group.subgroup, kind, minutes, now - 5 * 60_000L)
            if (due != null && due.triggerMillis <= now + 60_000L) {
                val notes = NotesRepository(context).current()
                post(context, due, notes, group.slug, group.subgroup == 0)
            }
        }
        reschedule(context)
    }

    /** Какие примеры показать: по одному на каждый включённый переключатель, а если оба выключены, утренний. */
    fun sampleKinds(morning: Boolean, evening: Boolean): List<Kind> =
        listOfNotNull(Kind.MORNING.takeIf { morning }, Kind.EVENING.takeIf { evening }).ifEmpty { listOf(Kind.MORNING) }

    /**
     * Пример для кнопки в настройках, показывается сразу: утренний — о сегодняшнем дне, вечерний — о завтрашнем
     * (если в этот день пар нет, берётся ближайший день с парами).
     */
    suspend fun postSample(context: Context) {
        val settings = SettingsRepository(context).current()
        val group = GroupRepository(context).selectedGroup.first() ?: return
        val lessons = ScheduleRepository(context).cached(group.slug)?.lessons ?: return
        val today = Dates.todayOf(System.currentTimeMillis())
        val notes = NotesRepository(context).current()
        for (kind in sampleKinds(settings.morningDigestEnabled, settings.eveningDigestEnabled)) {
            val wanted = if (kind == Kind.MORNING) today else today + 1
            val day = lessons.nextDayWithLessons(wanted, group.subgroup) ?: continue
            post(context, Digest(kind, System.currentTimeMillis(), day, lessons.on(day, group.subgroup)), notes, group.slug, group.subgroup == 0)
        }
    }

    fun post(context: Context, digest: Digest, notes: Map<String, String>, slug: String, showSubgroups: Boolean = false) {
        Notifications.ensureChannels(context)
        val lines = digest.lessons.map { lessonLine(it, notes[NotesRepository.key(slug, digest.day, it)], showSubgroups) }
        val count = lessonsCount(digest.lessons.slotCount())
        val title = context.getString(
            if (digest.kind == Kind.MORNING) R.string.digest_title_today else R.string.digest_title_tomorrow,
            count,
        )
        val first = digest.lessons.first()
        val firstPlace = first.rooms.joinToString(", ").ifEmpty { null }
        val short = context.getString(R.string.digest_first, first.start, listOfNotNull(first.subject, firstPlace).joinToString(" · "))
        val notification = NotificationCompat.Builder(context, Notifications.DIGEST_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lessons)
            .setContentTitle(title)
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(Notifications.openApp(context))
            .setAutoCancel(true)
            .build()
        Notifications.show(context, digest.kind.notificationId, notification)
    }

    private fun alarmIntent(context: Context, kind: Kind): PendingIntent = PendingIntent.getBroadcast(
        context,
        kind.requestCode,
        Intent(context, DigestReceiver::class.java).setAction(ACTION).putExtra(EXTRA_KIND, kind.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
