package com.example.pary.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Номер последнего обновления виджетов. Виджет следит за ним и перечитывает данные, когда он меняется:
 * пока сессия Glance жива, повторный updateAll не запускает загрузку данных заново.
 */
internal object WidgetVersion {
    val flow = MutableStateFlow(0)
}

/** Перерисовывает оба виджета: крупный 4×3 и компактный 4×2. Обновляются одновременно, а не по очереди. */
suspend fun updateScheduleWidgets(context: Context) {
    WidgetVersion.flow.update { it + 1 }
    coroutineScope {
        launch { ScheduleWidget().updateAll(context) }
        launch { CompactScheduleWidget().updateAll(context) }
    }
}
