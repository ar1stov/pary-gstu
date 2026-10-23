package com.example.pary.notify

import android.content.Context
import com.example.pary.widget.updateScheduleWidgets

/** Что нужно пересчитать, когда поменялись группа, подгруппа или расписание: виджеты и напоминание. */
suspend fun onScheduleChanged(context: Context) {
    updateScheduleWidgets(context)
    Reminders.reschedule(context)
}
