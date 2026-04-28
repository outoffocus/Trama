package com.trama.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.trama.app.backup.BackupManager
import com.trama.app.speech.IntentPattern
import com.trama.app.speech.PersonalDictionary
import com.trama.app.speech.speaker.SherpaSpeakerVerificationManager
import com.trama.app.ui.SettingsDataStore
import com.trama.shared.data.DiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsDataStore,
    val repository: DiaryRepository,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    val personalDictionary = PersonalDictionary(appContext)
    val speakerVerificationManager = SherpaSpeakerVerificationManager(appContext)

    val autoStart = settingsStore.autoStart
    val summaryEnabled = settingsStore.summaryEnabled
    val summaryHour = settingsStore.summaryHour
    val visibleCalendarIds = settingsStore.visibleCalendarIds
    val intentPatterns = settingsStore.intentPatterns
    val backupEnabled = settingsStore.backupEnabled
    val backupHour = settingsStore.backupHour
    val contextPreRollSeconds = settingsStore.contextPreRollSeconds
    val contextPostRollSeconds = settingsStore.contextPostRollSeconds
    val asrDebugEnabled = settingsStore.asrDebugEnabled
    val asrDebugEngine = settingsStore.asrDebugEngine
    val asrDebugStatus = settingsStore.asrDebugStatus
    val asrDebugLastText = settingsStore.asrDebugLastText
    val asrDebugGateText = settingsStore.asrDebugGateText
    val asrDebugTriggerReason = settingsStore.asrDebugTriggerReason
    val asrDebugLastWindowMs = settingsStore.asrDebugLastWindowMs
    val asrDebugLastDecodeMs = settingsStore.asrDebugLastDecodeMs
    val watchDebugStatus = settingsStore.watchDebugStatus
    val watchDebugTrigger = settingsStore.watchDebugTrigger
    val locationEnabled = settingsStore.locationEnabled
    val locationIntervalMinutes = settingsStore.locationIntervalMinutes
    val locationDwellMinutes = settingsStore.locationDwellMinutes
    val locationEntryRadiusMeters = settingsStore.locationEntryRadiusMeters
    val locationExitRadiusMeters = settingsStore.locationExitRadiusMeters
    val googlePlacesApiKey = settingsStore.googlePlacesApiKey
    val timelineColorPending = settingsStore.timelineColorPending
    val timelineColorCompleted = settingsStore.timelineColorCompleted
    val timelineColorRecording = settingsStore.timelineColorRecording
    val timelineColorPlace = settingsStore.timelineColorPlace
    val timelineColorCalendar = settingsStore.timelineColorCalendar
    val themeMode = settingsStore.themeMode
    val showOldEntriesExpanded = settingsStore.showOldEntriesExpanded

    private val summaryPrefs = appContext.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
    private val _geminiApiKey = MutableStateFlow(summaryPrefs.getString("gemini_api_key", "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun setGeminiApiKey(value: String) {
        val trimmed = value.trim()
        _geminiApiKey.value = value
        summaryPrefs.edit().putString("gemini_api_key", trimmed).apply()
    }

    suspend fun setAutoStart(enabled: Boolean) = settingsStore.setAutoStart(enabled)
    suspend fun setSummaryEnabled(enabled: Boolean) = settingsStore.setSummaryEnabled(enabled)
    suspend fun setSummaryHour(hour: Int) = settingsStore.setSummaryHour(hour)
    suspend fun setVisibleCalendarIds(ids: Set<Long>) = settingsStore.setVisibleCalendarIds(ids)
    suspend fun setBackupEnabled(enabled: Boolean) = settingsStore.setBackupEnabled(enabled)
    suspend fun setBackupHour(hour: Int) = settingsStore.setBackupHour(hour)
    suspend fun setContextPreRollSeconds(seconds: Int) = settingsStore.setContextPreRollSeconds(seconds)
    suspend fun setContextPostRollSeconds(seconds: Int) = settingsStore.setContextPostRollSeconds(seconds)
    suspend fun setAsrDebugEnabled(enabled: Boolean) = settingsStore.setAsrDebugEnabled(enabled)
    suspend fun setThemeMode(mode: Int) = settingsStore.setThemeMode(mode)
    suspend fun setShowOldEntriesExpanded(expanded: Boolean) = settingsStore.setShowOldEntriesExpanded(expanded)
    suspend fun setTimelineColorPending(index: Int) = settingsStore.setTimelineColorPending(index)
    suspend fun setTimelineColorCompleted(index: Int) = settingsStore.setTimelineColorCompleted(index)
    suspend fun setTimelineColorRecording(index: Int) = settingsStore.setTimelineColorRecording(index)
    suspend fun setTimelineColorPlace(index: Int) = settingsStore.setTimelineColorPlace(index)
    suspend fun setTimelineColorCalendar(index: Int) = settingsStore.setTimelineColorCalendar(index)
    suspend fun setLocationEnabled(enabled: Boolean) = settingsStore.setLocationEnabled(enabled)
    suspend fun setLocationIntervalMinutes(minutes: Int) = settingsStore.setLocationIntervalMinutes(minutes)
    suspend fun setLocationDwellMinutes(minutes: Int) = settingsStore.setLocationDwellMinutes(minutes)
    suspend fun setLocationEntryRadiusMeters(meters: Int) = settingsStore.setLocationEntryRadiusMeters(meters)
    suspend fun setLocationExitRadiusMeters(meters: Int) = settingsStore.setLocationExitRadiusMeters(meters)
    suspend fun setGooglePlacesApiKey(apiKey: String) = settingsStore.setGooglePlacesApiKey(apiKey)
    suspend fun updatePattern(updated: IntentPattern, allPatterns: List<IntentPattern>) =
        settingsStore.updatePattern(updated, allPatterns)
    suspend fun removePattern(patternId: String, allPatterns: List<IntentPattern>) =
        settingsStore.removePattern(patternId, allPatterns)
    suspend fun addPattern(pattern: IntentPattern, allPatterns: List<IntentPattern>) =
        settingsStore.addPattern(pattern, allPatterns)

    suspend fun exportBackup(uri: Uri): Int =
        BackupManager.exportToUri(appContext, uri, repository)

    suspend fun importBackup(uri: Uri): Pair<Int, Int> =
        BackupManager.importFromUri(appContext, uri, repository)
}
