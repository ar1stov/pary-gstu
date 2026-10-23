package com.example.pary.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Срабатывает по будильнику из [WidgetScheduler] и перерисовывает виджеты. */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Перерисовка сама поставит следующий будильник.
                updateScheduleWidgets(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
