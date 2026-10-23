package com.example.pary.notify

import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.pary.R
import com.example.pary.data.ScheduleChange

/** Уведомление «Расписание изменилось» (приходит, когда обновление нашло изменения в фоне). */
object ChangeNotifier {
    private const val NOTIFICATION_ID = 2
    private const val MAX_LINES = 8

    fun post(context: Context, groupName: String, changes: List<ScheduleChange>) {
        if (changes.isEmpty()) return
        Notifications.ensureChannels(context)
        val lines = changes.map { it.describe() }
        val more = lines.size - MAX_LINES
        val details = lines.take(MAX_LINES).joinToString("\n") + if (more > 0) "\n" + context.getString(R.string.changes_more, more) else ""
        val notification = NotificationCompat.Builder(context, Notifications.CHANGES_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lessons)
            .setContentTitle(context.getString(R.string.changes_notification_title, groupName))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(Notifications.openApp(context))
            .setAutoCancel(true)
            .build()
        Notifications.show(context, NOTIFICATION_ID, notification)
    }
}
