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

/** О каких парах напоминать: только о первой паре дня или перед каждой. */
enum class ReminderMode { FIRST_ONLY, EVERY_LESSON }

data class AppSettings(
    /** Напоминать о парах. */
    val remindersEnabled: Boolean = false,
    /** За сколько минут до пары напомнить. */
    val reminderLeadMinutes: Int = 30,
    /** О первой паре дня или о каждой. */
    val reminderMode: ReminderMode = ReminderMode.FIRST_ONLY,
    /** Присылать уведомление, когда расписание изменилось. */
    val changeAlertsEnabled: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private val remindersKey = booleanPreferencesKey("reminders_enabled")
    private val leadKey = intPreferencesKey("reminder_lead_minutes")
    private val modeKey = intPreferencesKey("reminder_mode")
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

    suspend fun setReminderMode(mode: ReminderMode) {
        context.dataStore.edit { it[modeKey] = mode.ordinal }
        Reminders.reschedule(context)
    }

    suspend fun setChangeAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[changeAlertsKey] = enabled }
    }

    private fun Preferences.toSettings() = AppSettings(
        remindersEnabled = this[remindersKey] ?: false,
        reminderLeadMinutes = this[leadKey] ?: 30,
        reminderMode = ReminderMode.entries.getOrElse(this[modeKey] ?: 0) { ReminderMode.FIRST_ONLY },
        changeAlertsEnabled = this[changeAlertsKey] ?: true,
    )
}
