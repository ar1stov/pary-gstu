package com.example.pary.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Заметки к парам («принести тетрадь», «сдать лабу»). Привязаны к конкретной паре в конкретный день:
 * в следующую неделю та же пара приходит без заметки. Хранятся только на телефоне.
 */
class NotesRepository(private val context: Context) {
    private val notesKey = stringPreferencesKey("lesson_notes")

    /** Все заметки: ключ из [key] → текст. */
    val notes: Flow<Map<String, String>> = context.dataStore.data.map { parse(it[notesKey]) }

    suspend fun current(): Map<String, String> = notes.first()

    /** Сохраняет заметку; пустой текст удаляет её. Заметки старше [KEEP_DAYS] дней убираются сами. */
    suspend fun set(key: String, text: String) {
        val today = Dates.today()
        context.dataStore.edit { prefs ->
            val map = parse(prefs[notesKey]).toMutableMap()
            if (text.isBlank()) map.remove(key) else map[key] = text.trim()
            map.keys.removeAll { dayOf(it)?.let { day -> day < today - KEEP_DAYS } ?: false }
            if (map.isEmpty()) prefs.remove(notesKey) else prefs[notesKey] = JSONObject(map as Map<*, *>).toString()
        }
    }

    companion object {
        private const val KEEP_DAYS = 30

        /** Ключ заметки: группа, день, время начала и название пары. */
        fun key(slug: String, day: Long, lesson: Lesson): String = "$slug|$day|${lesson.start}|${lesson.subject}"

        /** День (epoch day) из ключа заметки; null, если ключ не в нашем формате. */
        fun dayOf(key: String): Long? = key.split("|").getOrNull(1)?.toLongOrNull()

        fun parse(json: String?): Map<String, String> {
            if (json.isNullOrBlank()) return emptyMap()
            return try {
                val obj = JSONObject(json)
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}
