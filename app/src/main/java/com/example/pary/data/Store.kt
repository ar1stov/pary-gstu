package com.example.pary.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Единственное хранилище настроек и кэша; второй раз с тем же именем его объявлять нельзя. */
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
