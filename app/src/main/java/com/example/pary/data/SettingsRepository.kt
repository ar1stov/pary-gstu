package com.example.pary.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.pary.notify.Digests
import com.example.pary.notify.Reminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Тема оформления: как в системе, светлая или тёмная. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** О каких парах напоминать: только о первой паре дня или перед каждой. */
enum class ReminderMode { FIRST_ONLY, EVERY_LESSON }

data class AppSettings(
    /** Напоминать о парах. */
    val remindersEnabled: Boolean = false,
    /** За сколько минут до пары напомнить. */
    val reminderLeadMinutes: Int = 30,
    /** О первой паре дня или о каждой. */
    val reminderMode: ReminderMode = ReminderMode.FIRST_ONLY,
    /** Сколько минут занимает дорога: напоминание о первой паре дня приходит на столько раньше. */
    val travelMinutes: Int = 0,
    /** Присылать уведомление, когда расписание изменилось. */
    val changeAlertsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Утренняя сводка: что сегодня по парам. Время в минутах от полуночи. */
    val morningDigestEnabled: Boolean = false,
    val morningDigestMinutes: Int = 7 * 60,
    /** Вечерняя сводка: что завтра по парам. */
    val eveningDigestEnabled: Boolean = false,
    val eveningDigestMinutes: Int = 20 * 60,
    /** Версия, о которой пользователь уже прочитал «Что нового»; пусто — ещё ни о какой. */
    val lastSeenVersion: String = "",
)

class SettingsRepository(private val context: Context) {
    private val remindersKey = booleanPreferencesKey("reminders_enabled")
    private val leadKey = intPreferencesKey("reminder_lead_minutes")
    private val modeKey = intPreferencesKey("reminder_mode")
    private val travelKey = intPreferencesKey("travel_minutes")
    private val changeAlertsKey = booleanPreferencesKey("change_alerts_enabled")
    private val themeKey = intPreferencesKey("theme_mode")
    private val morningKey = booleanPreferencesKey("digest_morning_enabled")
    private val morningTimeKey = intPreferencesKey("digest_morning_minutes")
    private val eveningKey = booleanPreferencesKey("digest_evening_enabled")
    private val eveningTimeKey = intPreferencesKey("digest_evening_minutes")
    private val lastSeenVersionKey = stringPreferencesKey("last_seen_version")

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

    suspend fun setTravelMinutes(minutes: Int) {
        context.dataStore.edit { it[travelKey] = minutes }
        Reminders.reschedule(context)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.ordinal }
    }

    suspend fun setChangeAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[changeAlertsKey] = enabled }
    }

    suspend fun setMorningDigest(enabled: Boolean) {
        context.dataStore.edit { it[morningKey] = enabled }
        Digests.reschedule(context)
    }

    suspend fun setMorningDigestMinutes(minutes: Int) {
        context.dataStore.edit { it[morningTimeKey] = minutes }
        Digests.reschedule(context)
    }

    suspend fun setEveningDigest(enabled: Boolean) {
        context.dataStore.edit { it[eveningKey] = enabled }
        Digests.reschedule(context)
    }

    suspend fun setEveningDigestMinutes(minutes: Int) {
        context.dataStore.edit { it[eveningTimeKey] = minutes }
        Digests.reschedule(context)
    }

    suspend fun setLastSeenVersion(version: String) {
        context.dataStore.edit { it[lastSeenVersionKey] = version }
    }

    private fun Preferences.toSettings() = AppSettings(
        remindersEnabled = this[remindersKey] ?: false,
        reminderLeadMinutes = this[leadKey] ?: 30,
        reminderMode = ReminderMode.entries.getOrElse(this[modeKey] ?: 0) { ReminderMode.FIRST_ONLY },
        travelMinutes = this[travelKey] ?: 0,
        changeAlertsEnabled = this[changeAlertsKey] ?: true,
        themeMode = ThemeMode.entries.getOrElse(this[themeKey] ?: 0) { ThemeMode.SYSTEM },
        morningDigestEnabled = this[morningKey] ?: false,
        morningDigestMinutes = this[morningTimeKey] ?: (7 * 60),
        eveningDigestEnabled = this[eveningKey] ?: false,
        eveningDigestMinutes = this[eveningTimeKey] ?: (20 * 60),
        lastSeenVersion = this[lastSeenVersionKey].orEmpty(),
    )
}
