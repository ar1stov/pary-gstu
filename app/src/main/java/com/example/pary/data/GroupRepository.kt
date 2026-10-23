package com.example.pary.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.pary.notify.onScheduleChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GroupRepository(private val context: Context) {
    private val slugKey = stringPreferencesKey("selected_group_slug")
    private val nameKey = stringPreferencesKey("selected_group_name")
    private val subgroupKey = intPreferencesKey("selected_subgroup")

    /** Выбранная группа; null, пока студент ещё ничего не выбрал. */
    val selectedGroup: Flow<GroupInfo?> = context.dataStore.data.map { prefs ->
        val slug = prefs[slugKey]
        val name = prefs[nameKey]
        if (slug == null || name == null) null else GroupInfo(slug, name, subgroup = prefs[subgroupKey] ?: 0)
    }

    suspend fun saveGroup(group: GroupInfo) {
        context.dataStore.edit {
            it[slugKey] = group.slug
            it[nameKey] = group.name
            it.remove(subgroupKey) // у другой группы подгруппы другие
        }
        onScheduleChanged(context)
    }

    /** Запоминает подгруппу выбранной группы; 0 — показывать все. */
    suspend fun saveSubgroup(subgroup: Int) {
        context.dataStore.edit { it[subgroupKey] = subgroup }
        onScheduleChanged(context)
    }

    suspend fun clearGroup() {
        context.dataStore.edit {
            it.remove(slugKey)
            it.remove(nameKey)
            it.remove(subgroupKey)
        }
        onScheduleChanged(context)
    }
}
