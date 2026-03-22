package com.mydiary.app.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mydiary.shared.model.CategoryInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val RECORDING_DURATION = intPreferencesKey("recording_duration")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val KEYWORD_MAPPINGS = stringPreferencesKey("keyword_mappings")
        val CATEGORIES = stringPreferencesKey("categories")
        val GEMINI_ENABLED = booleanPreferencesKey("gemini_enabled")

        const val DEFAULT_DURATION = 30
        const val DEFAULT_MAPPINGS = "recordar:TODO,nota:NOTE,destacar:HIGHLIGHT,pendiente:REMINDER"

        val DEFAULT_CATEGORIES = CategoryInfo.DEFAULTS
            .joinToString(",") { "${it.id}|${it.label}|${it.colorHex}" }
    }

    val recordingDuration: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[RECORDING_DURATION] ?: DEFAULT_DURATION
    }

    val autoStart: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_START] ?: false
    }

    val categories: Flow<List<CategoryInfo>> = context.dataStore.data.map { prefs ->
        parseCategories(prefs[CATEGORIES] ?: DEFAULT_CATEGORIES)
    }

    val keywordMappings: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        parseMappings(prefs[KEYWORD_MAPPINGS] ?: DEFAULT_MAPPINGS)
    }

    val geminiEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[GEMINI_ENABLED] ?: false
    }

    val keywords: Flow<List<String>> = keywordMappings.map { it.keys.toList() }

    suspend fun setRecordingDuration(seconds: Int) {
        context.dataStore.edit { it[RECORDING_DURATION] = seconds }
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_START] = enabled }
    }

    suspend fun setGeminiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[GEMINI_ENABLED] = enabled }
    }

    suspend fun setCategories(cats: List<CategoryInfo>) {
        val raw = cats.joinToString(",") { "${it.id}|${it.label}|${it.colorHex}" }
        context.dataStore.edit { it[CATEGORIES] = raw }
    }

    suspend fun setKeywordMappings(mappings: Map<String, String>) {
        val raw = mappings.entries.joinToString(",") { "${it.key}:${it.value}" }
        context.dataStore.edit { it[KEYWORD_MAPPINGS] = raw }
    }

    private fun parseCategories(raw: String): List<CategoryInfo> {
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.trim().split("|")
            if (parts.size == 3) {
                CategoryInfo(
                    id = parts[0].trim(),
                    label = parts[1].trim(),
                    colorHex = parts[2].trim()
                )
            } else null
        }.ifEmpty { CategoryInfo.DEFAULTS }
    }

    private fun parseMappings(raw: String): Map<String, String> {
        return raw.split(",").mapNotNull { pair ->
            val parts = pair.trim().split(":")
            if (parts.size == 2) {
                parts[0].trim().lowercase() to parts[1].trim()
            } else null
        }.toMap()
    }
}
