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
import com.trama.shared.speech.CaptureProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsDataStore,
    val repository: DiaryRepository,
    val personalDictionary: PersonalDictionary,
    val speakerVerificationManager: SherpaSpeakerVerificationManager,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    val autoStart = settingsStore.autoStart
    val recordingDuration = settingsStore.recordingDuration
    val summaryEnabled = settingsStore.summaryEnabled
    val summaryHour = settingsStore.summaryHour
    val visibleCalendarIds = settingsStore.visibleCalendarIds
    val intentPatterns = settingsStore.intentPatterns
    val customKeywords = settingsStore.customKeywords
    val captureProfile = settingsStore.captureProfile
    val backupEnabled = settingsStore.backupEnabled
    val backupHour = settingsStore.backupHour
    val contextPreRollSeconds = settingsStore.contextPreRollSeconds
    val contextPostRollSeconds = settingsStore.contextPostRollSeconds
    val asrDebugEnabled = settingsStore.asrDebugEnabled
    val listeningStatusOnHome = settingsStore.listeningStatusOnHome
    val ambientContextConfig = settingsStore.ambientContextConfig
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
    val showAdvancedOptions = settingsStore.showAdvancedOptions
    val learnFromDeletions = settingsStore.learnFromDeletions
    val weeklyAgendaEnabled = settingsStore.weeklyAgendaEnabled
    val weeklyAgendaDayOfWeek = settingsStore.weeklyAgendaDayOfWeek
    val weeklyAgendaHour = settingsStore.weeklyAgendaHour

    suspend fun setAutoStart(enabled: Boolean) = settingsStore.setAutoStart(enabled)
    suspend fun setRecordingDuration(minutes: Int) = settingsStore.setRecordingDuration(minutes)
    suspend fun setSummaryEnabled(enabled: Boolean) = settingsStore.setSummaryEnabled(enabled)
    suspend fun setSummaryHour(hour: Int) = settingsStore.setSummaryHour(hour)
    suspend fun setVisibleCalendarIds(ids: Set<Long>) = settingsStore.setVisibleCalendarIds(ids)
    suspend fun setBackupEnabled(enabled: Boolean) = settingsStore.setBackupEnabled(enabled)
    suspend fun setBackupHour(hour: Int) = settingsStore.setBackupHour(hour)
    suspend fun setContextPreRollSeconds(seconds: Int) = settingsStore.setContextPreRollSeconds(seconds)
    suspend fun setContextPostRollSeconds(seconds: Int) = settingsStore.setContextPostRollSeconds(seconds)
    suspend fun setCaptureProfile(profile: CaptureProfile) = settingsStore.setCaptureProfile(profile)
    suspend fun resetRecommendedCaptureSettings() = settingsStore.resetRecommendedCaptureSettings()
    suspend fun setAsrDebugEnabled(enabled: Boolean) = settingsStore.setAsrDebugEnabled(enabled)
    suspend fun setListeningStatusOnHome(enabled: Boolean) = settingsStore.setListeningStatusOnHome(enabled)
    suspend fun setAmbientContextEnabled(enabled: Boolean) = settingsStore.setAmbientContextEnabled(enabled)
    suspend fun setAmbientContextHours(startHour: Int, endHour: Int) =
        settingsStore.setAmbientContextHours(startHour, endHour)
    suspend fun setAmbientContextExcludeHome(exclude: Boolean) =
        settingsStore.setAmbientContextExcludeHome(exclude)
    suspend fun setAmbientContextExcludeWork(exclude: Boolean) =
        settingsStore.setAmbientContextExcludeWork(exclude)
    suspend fun setThemeMode(mode: Int) = settingsStore.setThemeMode(mode)
    suspend fun setShowOldEntriesExpanded(expanded: Boolean) = settingsStore.setShowOldEntriesExpanded(expanded)
    suspend fun setShowAdvancedOptions(visible: Boolean) = settingsStore.setShowAdvancedOptions(visible)
    suspend fun setLearnFromDeletions(enabled: Boolean) = settingsStore.setLearnFromDeletions(enabled)
    suspend fun setWeeklyAgendaEnabled(enabled: Boolean) = settingsStore.setWeeklyAgendaEnabled(enabled)
    suspend fun setWeeklyAgendaDayOfWeek(dayOfWeek: Int) = settingsStore.setWeeklyAgendaDayOfWeek(dayOfWeek)
    suspend fun setWeeklyAgendaHour(hour: Int) = settingsStore.setWeeklyAgendaHour(hour)
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
