package com.example.pary.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.pary.notify.onScheduleChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class GroupRepository(private val context: Context) {
    private val slugKey = stringPreferencesKey("selected_group_slug")
    private val nameKey = stringPreferencesKey("selected_group_name")
    private val subgroupKey = intPreferencesKey("selected_subgroup")
    private val recentKey = stringPreferencesKey("recent_groups")

    /** Подгруппа, выбранная в этой группе в прошлый раз: при возврате на группу она вспоминается. */
    private fun subgroupOf(slug: String) = intPreferencesKey("subgroup_of_$slug")

    /** Выбранная группа; null, пока студент ещё ничего не выбрал. */
    val selectedGroup: Flow<GroupInfo?> = context.dataStore.data.map { prefs ->
        val slug = prefs[slugKey]
        val name = prefs[nameKey]
        if (slug == null || name == null) null else GroupInfo(slug, name, subgroup = prefs[subgroupKey] ?: 0)
    }

    /** Группы, которые выбирали раньше (самая свежая первой), для быстрого переключения. */
    val recentGroups: Flow<List<GroupInfo>> = context.dataStore.data.map { parseRecent(it[recentKey]) }

    suspend fun saveGroup(group: GroupInfo) {
        context.dataStore.edit {
            val previousSlug = it[slugKey]
            val previousName = it[nameKey]
            var recent = parseRecent(it[recentKey])
            // Группа, с которой уходим, тоже попадает в недавние: к ней можно будет вернуться в одно нажатие.
            if (previousSlug != null && previousName != null && previousSlug != group.slug) {
                recent = pushRecent(recent, GroupInfo(previousSlug, previousName), keepExisting = true)
            }
            it[recentKey] = encodeRecent(pushRecent(recent, group, keepExisting = false))
            it[slugKey] = group.slug
            it[nameKey] = group.name
            val remembered = it[subgroupOf(group.slug)]
            if (remembered != null) it[subgroupKey] = remembered else it.remove(subgroupKey) // у другой группы подгруппы другие
        }
        onScheduleChanged(context)
    }

    /** Запоминает подгруппу выбранной группы; 0 — показывать все. */
    suspend fun saveSubgroup(subgroup: Int) {
        context.dataStore.edit {
            it[subgroupKey] = subgroup
            it[slugKey]?.let { slug -> it[subgroupOf(slug)] = subgroup }
        }
        onScheduleChanged(context)
    }

    suspend fun clearGroup() {
        context.dataStore.edit {
            val slug = it[slugKey]
            val name = it[nameKey]
            if (slug != null && name != null) {
                it[recentKey] = encodeRecent(pushRecent(parseRecent(it[recentKey]), GroupInfo(slug, name), keepExisting = true))
            }
            it.remove(slugKey)
            it.remove(nameKey)
            it.remove(subgroupKey)
        }
        onScheduleChanged(context)
    }

    companion object {
        private const val MAX_RECENT = 5

        /**
         * Ставит [group] первой в список недавних. [keepExisting]: если такая группа уже есть, остаётся её запись
         * (с курсом и факультетом), а не новая, у которой этих сведений нет.
         */
        fun pushRecent(recent: List<GroupInfo>, group: GroupInfo, keepExisting: Boolean): List<GroupInfo> {
            val existing = recent.firstOrNull { it.slug == group.slug }
            val first = (if (keepExisting) existing else null) ?: group.copy(subgroup = 0)
            return (listOf(first) + recent.filter { it.slug != group.slug }).take(MAX_RECENT)
        }

        fun parseRecent(json: String?): List<GroupInfo> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    GroupInfo(
                        slug = obj.getString("slug"),
                        name = obj.getString("name"),
                        course = obj.optInt("course"),
                        faculty = obj.optString("faculty"),
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun encodeRecent(groups: List<GroupInfo>): String = JSONArray().apply {
            groups.take(MAX_RECENT).forEach { g ->
                put(JSONObject().put("slug", g.slug).put("name", g.name).put("course", g.course).put("faculty", g.faculty))
            }
        }.toString()
    }
}
