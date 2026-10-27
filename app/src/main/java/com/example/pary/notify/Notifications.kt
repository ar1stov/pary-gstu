package com.example.pary.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pary.MainActivity
import com.example.pary.R

/** Каналы уведомлений и общие мелочи: разрешение, нажатие на уведомление. */
object Notifications {
    const val REMINDERS_CHANNEL = "lesson_reminders"
    const val CHANGES_CHANNEL = "schedule_changes"
    const val DIGEST_CHANNEL = "daily_digest"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDERS_CHANNEL,
                context.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANGES_CHANNEL,
                context.getString(R.string.channel_changes),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                DIGEST_CHANNEL,
                context.getString(R.string.channel_digest),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /** С Android 13 уведомления можно показывать только после разрешения пользователя. */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission") // разрешение проверяет canPost
    fun show(context: Context, id: Int, notification: Notification) {
        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, notification)
    }
}
