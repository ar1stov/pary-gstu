package com.example.pary.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

private const val MAX_PENDING_CHANGES = 30

/** Расписание группы: сначала берём сохранённое на телефоне, потом обновляем из сети. */
class ScheduleRepository(
    private val context: Context,
    private val api: ScheduleApi = ScheduleApi(),
) {
    /** [changes] — что изменилось при этой загрузке по сравнению с предыдущей. */
    data class Snapshot(val lessons: List<Lesson>, val savedAt: Long, val changes: List<ScheduleChange> = emptyList())

    suspend fun searchGroups(query: String): GroupSearchResult = api.searchGroups(query)

    /** Сохранённое расписание или null, если его ещё не загружали (или кэш повреждён). */
    suspend fun cached(slug: String): Snapshot? {
        val prefs = context.dataStore.data.first()
        val json = prefs[jsonKey(slug)] ?: return null
        return try {
            Snapshot(ScheduleParser.parseSchedule(json), prefs[timeKey(slug)] ?: 0L)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Скачивает расписание и кладёт в кэш; при ошибке прежний кэш остаётся нетронутым.
     * Новые изменения по сравнению с прежним кэшем (для подгруппы [subgroup]) попадают в список
     * непрочитанных, пока их не закроют кнопкой «Понятно».
     */
    suspend fun refresh(slug: String, subgroup: Int = 0): Snapshot {
        val json = api.fetchGroupScheduleJson(slug)
        val lessons = ScheduleParser.parseSchedule(json) // проверяем до записи
        val previous = cached(slug)?.lessons
        val changes = previous?.let { ScheduleDiff.compute(it, lessons, Dates.today(), subgroup) }.orEmpty()
        val now = System.currentTimeMillis()
        context.dataStore.edit {
            it[jsonKey(slug)] = json
            it[timeKey(slug)] = now
            if (changes.isNotEmpty()) {
                val earlier = it[changesKey(slug)]?.split("\n").orEmpty()
                it[changesKey(slug)] = (earlier + changes.map { c -> c.describe() })
                    .filter { line -> line.isNotBlank() }.distinct().takeLast(MAX_PENDING_CHANGES).joinToString("\n")
            }
        }
        return Snapshot(lessons, now, changes)
    }

    /** Непрочитанные изменения расписания (тексты) в порядке обнаружения. */
    suspend fun pendingChanges(slug: String): List<String> =
        context.dataStore.data.first()[changesKey(slug)]?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

    suspend fun dismissChanges(slug: String) {
        context.dataStore.edit { it.remove(changesKey(slug)) }
    }

    private fun changesKey(slug: String) = stringPreferencesKey("schedule_changes_$slug")
    private fun jsonKey(slug: String) = stringPreferencesKey("schedule_json_$slug")
    private fun timeKey(slug: String) = longPreferencesKey("schedule_time_$slug")
}
