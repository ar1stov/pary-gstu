package com.example.pary.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pary.widget.updateScheduleWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Срабатывает по будильнику напоминания: показывает уведомление и ставит следующее. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Reminders.fire(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}

/** После перезагрузки телефона и обновления приложения будильники пропадают: ставим их заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext
                Reminders.reschedule(app)
                updateScheduleWidgets(app)
            } finally {
                pending.finish()
            }
        }
    }
}
