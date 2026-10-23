package com.example.pary.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.pary.notify.Reminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    /** Напоминать о первой паре дня. */
    val remindersEnabled: Boolean = false,
    /** За сколько минут до первой пары напомнить. */
    val reminderLeadMinutes: Int = 30,
    /** Присылать уведомление, когда расписание изменилось. */
    val changeAlertsEnabled: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private val remindersKey = booleanPreferencesKey("reminders_enabled")
    private val leadKey = intPreferencesKey("reminder_lead_minutes")
    private val changeAlertsKey = booleanPreferencesKey("change_alerts_enabled")

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[remindersKey] = enabled }
        Reminders.reschedule(context)
    }

    suspend fun setReminderLeadMinutes(minutes: Int) {
        context.dataStore.edit { it[leadKey] = minutes }
        Reminders.reschedule(context)
    }

    suspend fun setChangeAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[changeAlertsKey] = enabled }
    }

    private fun Preferences.toSettings() = AppSettings(
        remindersEnabled = this[remindersKey] ?: false,
        reminderLeadMinutes = this[leadKey] ?: 30,
        changeAlertsEnabled = this[changeAlertsKey] ?: true,
    )
}
