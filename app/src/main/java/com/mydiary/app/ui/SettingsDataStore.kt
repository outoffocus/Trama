package com.mydiary.app.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mydiary.shared.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val RECORDING_DURATION = intPreferencesKey("recording_duration")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val KEYWORD_MAPPINGS = stringPreferencesKey("keyword_mappings")

        const val DEFAULT_DURATION = 10
        const val DEFAULT_MAPPINGS = "recordar:TODO,nota:NOTE,destacar:HIGHLIGHT,pendiente:REMINDER"
    }

    val recordingDuration: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[RECORDING_DURATION] ?: DEFAULT_DURATION
    }

    val autoStart: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_START] ?: false
    }

    val keywordMappings: Flow<Map<String, Category>> = context.dataStore.data.map { prefs ->
        parseMappings(prefs[KEYWORD_MAPPINGS] ?: DEFAULT_MAPPINGS)
    }

    val keywords: Flow<List<String>> = keywordMappings.map { it.keys.toList() }

    suspend fun setRecordingDuration(seconds: Int) {
        context.dataStore.edit { it[RECORDING_DURATION] = seconds }
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_START] = enabled }
    }

    suspend fun setKeywordMappings(mappings: Map<String, Category>) {
        val raw = mappings.entries.joinToString(",") { "${it.key}:${it.value.name}" }
        context.dataStore.edit { it[KEYWORD_MAPPINGS] = raw }
    }

    private fun parseMappings(raw: String): Map<String, Category> {
        return raw.split(",").mapNotNull { pair ->
            val parts = pair.trim().split(":")
            if (parts.size == 2) {
                val keyword = parts[0].trim().lowercase()
                val category = try {
                    Category.valueOf(parts[1].trim())
                } catch (e: Exception) {
                    Category.NOTE
                }
                keyword to category
            } else null
        }.toMap()
    }
}
