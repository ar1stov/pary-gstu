package com.example.pary.notify

import android.content.Context
import com.example.pary.widget.updateScheduleWidgets
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Что нужно пересчитать, когда поменялись группа, подгруппа или расписание: виджеты, напоминание и сводки. */
suspend fun onScheduleChanged(context: Context) {
    coroutineScope {
        launch { updateScheduleWidgets(context) }
        launch { Reminders.reschedule(context) }
        launch { Digests.reschedule(context) }
    }
}
