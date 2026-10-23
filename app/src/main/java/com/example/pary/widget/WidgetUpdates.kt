package com.example.pary.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Перерисовывает оба виджета: крупный 4×3 и компактный 4×2. */
suspend fun updateScheduleWidgets(context: Context) {
    ScheduleWidget().updateAll(context)
    CompactScheduleWidget().updateAll(context)
}
