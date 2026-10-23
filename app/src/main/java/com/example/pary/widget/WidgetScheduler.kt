package com.example.pary.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.pary.data.Dates
import com.example.pary.data.Lesson
import com.example.pary.data.on

/**
 * Будильник для виджетов: система сама обновляет их лишь раз в ~30 минут, а нам нужно
 * перерисовать виджет, когда пара закончилась или началась, и после полуночи.
 */
object WidgetScheduler {
    private const val MINUTES_IN_DAY = 24 * 60

    /**
     * Минута суток, когда виджет нужно перерисовать в следующий раз: ближайшее начало или конец
     * сегодняшней пары, а если их больше нет, минута после полуночи.
     */
    fun nextRefreshMinute(lessons: List<Lesson>, subgroup: Int, today: Long, nowMinute: Int): Int =
        lessons.on(today, subgroup)
            .flatMap { listOfNotNull(Dates.minutesOfDay(it.start), Dates.minutesOfDay(it.end)) }
            .filter { it > nowMinute }
            .minOrNull() ?: (MINUTES_IN_DAY + 1)

    fun scheduleNext(context: Context, lessons: List<Lesson>, subgroup: Int, nowMillis: Long) {
        val today = Dates.todayOf(nowMillis)
        val nowMinute = Dates.minutesOfDayAt(nowMillis)
        val minutesLeft = nextRefreshMinute(lessons, subgroup, today, nowMinute) - nowMinute
        // Чуть позже границы минуты, чтобы пара уже точно считалась начавшейся или закончившейся.
        val minuteStart = nowMillis - nowMillis % 60_000L
        val trigger = minuteStart + minutesLeft * 60_000L + 5_000L

        val intent = Intent(context, WidgetTickReceiver::class.java).setAction(ACTION)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Будильник с коротким окном: разрешений не требует и срабатывает почти вовремя
        // (обычный неточный будильник мог бы опоздать на десятки минут; система не даёт окно меньше ~10 минут).
        context.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC_WAKEUP, trigger, WINDOW_MILLIS, pending)
    }

    private const val WINDOW_MILLIS = 60_000L

    const val ACTION = "com.example.pary.WIDGET_TICK"
}
