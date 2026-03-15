package com.mydiary.app.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val RECORDING_DURATION = intPreferencesKey("recording_duration")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val KEYWORDS = stringPreferencesKey("keywords")

        const val DEFAULT_DURATION = 10
        const val DEFAULT_KEYWORDS = "recordar,nota,destacar,pendiente"
    }

    val recordingDuration: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[RECORDING_DURATION] ?: DEFAULT_DURATION
    }

    val autoStart: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_START] ?: false
    }

    val keywords: Flow<List<String>> = context.dataStore.data.map { prefs ->
        (prefs[KEYWORDS] ?: DEFAULT_KEYWORDS).split(",").map { it.trim() }
    }

    suspend fun setRecordingDuration(seconds: Int) {
        context.dataStore.edit { it[RECORDING_DURATION] = seconds }
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_START] = enabled }
    }

    suspend fun setKeywords(keywords: List<String>) {
        context.dataStore.edit { it[KEYWORDS] = keywords.joinToString(",") }
    }
}
