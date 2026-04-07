package com.trama.app.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trama.app.speech.IntentPattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val RECORDING_DURATION = intPreferencesKey("recording_duration")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val KEYWORDS = stringPreferencesKey("keyword_mappings") // legacy key
        val INTENT_PATTERNS = stringPreferencesKey("intent_patterns_json")
        val CUSTOM_KEYWORDS = stringPreferencesKey("custom_keywords")
        val SUMMARY_ENABLED = booleanPreferencesKey("summary_enabled")
        val SUMMARY_HOUR = intPreferencesKey("summary_hour")
        val BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        val BACKUP_HOUR = intPreferencesKey("backup_hour")
        val CONTEXT_PRE_ROLL = intPreferencesKey("context_pre_roll_seconds")
        val CONTEXT_POST_ROLL = intPreferencesKey("context_post_roll_seconds")
        val ASR_DEBUG_ENABLED = booleanPreferencesKey("asr_debug_enabled")
        val ASR_DEBUG_ENGINE = stringPreferencesKey("asr_debug_engine")
        val ASR_DEBUG_STATUS = stringPreferencesKey("asr_debug_status")
        val ASR_DEBUG_LAST_TEXT = stringPreferencesKey("asr_debug_last_text")
        val ASR_DEBUG_LAST_WINDOW_MS = intPreferencesKey("asr_debug_last_window_ms")
        val ASR_DEBUG_LAST_DECODE_MS = intPreferencesKey("asr_debug_last_decode_ms")
        const val DEFAULT_DURATION = 30
        const val DEFAULT_SUMMARY_HOUR = 21
        const val DEFAULT_BACKUP_HOUR = 3  // 3:00 AM
        const val DEFAULT_CONTEXT_PRE_ROLL = 3
        const val DEFAULT_CONTEXT_POST_ROLL = 5
    }

    val recordingDuration: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[RECORDING_DURATION] ?: DEFAULT_DURATION
    }

    val autoStart: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_START] ?: false
    }

    /**
     * Intent patterns with full customization (triggers, labels, enabled state).
     * Stored as JSON. Merges with defaults on read to pick up new built-in patterns.
     */
    val intentPatterns: Flow<List<IntentPattern>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[INTENT_PATTERNS]
        if (jsonStr != null) {
            IntentPattern.deserialize(jsonStr)
        } else {
            IntentPattern.DEFAULTS
        }
    }

    /**
     * Custom keywords added by the user (simple contains matching).
     */
    val customKeywords: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val customStr = prefs[CUSTOM_KEYWORDS]
        if (customStr != null) {
            customStr.split(",").filter { it.isNotBlank() }.map { it.trim().lowercase() }
        } else {
            val oldKeywords = prefs[KEYWORDS]
            if (oldKeywords != null) migrateOldKeywords(oldKeywords)
            else emptyList()
        }
    }

    val summaryEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SUMMARY_ENABLED] ?: true
    }

    val summaryHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SUMMARY_HOUR] ?: DEFAULT_SUMMARY_HOUR
    }

    val backupEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BACKUP_ENABLED] ?: true
    }

    val backupHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[BACKUP_HOUR] ?: DEFAULT_BACKUP_HOUR
    }

    val contextPreRollSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[CONTEXT_PRE_ROLL] ?: DEFAULT_CONTEXT_PRE_ROLL
    }

    val contextPostRollSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[CONTEXT_POST_ROLL] ?: DEFAULT_CONTEXT_POST_ROLL
    }

    val asrDebugEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ASR_DEBUG_ENABLED] ?: false
    }

    val asrDebugEngine: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ASR_DEBUG_ENGINE] ?: "-"
    }

    val asrDebugStatus: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ASR_DEBUG_STATUS] ?: "sin datos"
    }

    val asrDebugLastText: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ASR_DEBUG_LAST_TEXT] ?: ""
    }

    val asrDebugLastWindowMs: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ASR_DEBUG_LAST_WINDOW_MS] ?: 0
    }

    val asrDebugLastDecodeMs: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ASR_DEBUG_LAST_DECODE_MS] ?: 0
    }

    suspend fun setRecordingDuration(seconds: Int) {
        context.dataStore.edit { it[RECORDING_DURATION] = seconds }
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_START] = enabled }
    }

    suspend fun setSummaryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SUMMARY_ENABLED] = enabled }
    }

    suspend fun setSummaryHour(hour: Int) {
        context.dataStore.edit { it[SUMMARY_HOUR] = hour }
    }

    suspend fun setBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BACKUP_ENABLED] = enabled }
    }

    suspend fun setBackupHour(hour: Int) {
        context.dataStore.edit { it[BACKUP_HOUR] = hour }
    }

    suspend fun setContextPreRollSeconds(seconds: Int) {
        context.dataStore.edit { it[CONTEXT_PRE_ROLL] = seconds }
    }

    suspend fun setContextPostRollSeconds(seconds: Int) {
        context.dataStore.edit { it[CONTEXT_POST_ROLL] = seconds }
    }

    suspend fun setAsrDebugEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ASR_DEBUG_ENABLED] = enabled }
    }

    suspend fun updateAsrDebugSnapshot(
        engine: String? = null,
        status: String? = null,
        lastText: String? = null,
        lastWindowMs: Int? = null,
        lastDecodeMs: Int? = null
    ) {
        context.dataStore.edit { prefs ->
            engine?.let { prefs[ASR_DEBUG_ENGINE] = it }
            status?.let { prefs[ASR_DEBUG_STATUS] = it }
            lastText?.let { prefs[ASR_DEBUG_LAST_TEXT] = it }
            lastWindowMs?.let { prefs[ASR_DEBUG_LAST_WINDOW_MS] = it }
            lastDecodeMs?.let { prefs[ASR_DEBUG_LAST_DECODE_MS] = it }
        }
    }

    /**
     * Save the full list of intent patterns (including customizations).
     */
    suspend fun setIntentPatterns(patterns: List<IntentPattern>) {
        context.dataStore.edit { it[INTENT_PATTERNS] = IntentPattern.serialize(patterns) }
    }

    /**
     * Convenience: update a single pattern in the list.
     */
    suspend fun updatePattern(updated: IntentPattern, allPatterns: List<IntentPattern>) {
        val newList = allPatterns.map { if (it.id == updated.id) updated else it }
        setIntentPatterns(newList)
    }

    /**
     * Add a new custom pattern.
     */
    suspend fun addPattern(pattern: IntentPattern, allPatterns: List<IntentPattern>) {
        setIntentPatterns(allPatterns + pattern)
    }

    /**
     * Remove a pattern (only custom patterns can be removed).
     */
    suspend fun removePattern(patternId: String, allPatterns: List<IntentPattern>) {
        setIntentPatterns(allPatterns.filter { it.id != patternId })
    }

    suspend fun setCustomKeywords(keywordList: List<String>) {
        val raw = keywordList.joinToString(",") { it.trim().lowercase() }
        context.dataStore.edit { it[CUSTOM_KEYWORDS] = raw }
    }

    suspend fun setKeywords(keywordList: List<String>) = setCustomKeywords(keywordList)

    private fun migrateOldKeywords(raw: String): List<String> {
        val parsed = raw.split(",").mapNotNull { entry ->
            val trimmed = entry.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            val keyword = if (trimmed.contains(":")) trimmed.substringBefore(":").trim()
            else trimmed
            keyword.lowercase().ifBlank { null }
        }.distinct()

        return parsed.filter { keyword ->
            !IntentPattern.DEFAULTS.any { pattern ->
                pattern.regex.containsMatchIn(keyword)
            }
        }
    }
}
