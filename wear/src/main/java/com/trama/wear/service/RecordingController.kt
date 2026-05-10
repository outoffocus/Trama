package com.trama.wear.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state for recording mode across Activity, Service and Composables.
 * The single source of truth for watch recording state.
 */
object RecordingController {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _currentPartial = MutableStateFlow("")
    val currentPartial: StateFlow<String> = _currentPartial.asStateFlow()

    private val _recordingKind = MutableStateFlow(WatchRecordingService.KIND_MANUAL_RECORDING)
    val recordingKind: StateFlow<String> = _recordingKind.asStateFlow()

    // ID of the saved recording (for navigation after stop)
    private val _savedRecordingId = MutableStateFlow<Long?>(null)
    val savedRecordingId: StateFlow<Long?> = _savedRecordingId.asStateFlow()

    // Incremented each time a toggle is requested from physical button
    private val _toggleRequest = MutableStateFlow(0L)
    val toggleRequest: StateFlow<Long> = _toggleRequest.asStateFlow()

    /** Start recording via foreground service */
    fun startRecording(context: Context) {
        _recordingKind.value = WatchRecordingService.KIND_MANUAL_RECORDING
        val intent = Intent(context, WatchRecordingService::class.java).apply {
            action = WatchRecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /** Start direct phone-processed capture via foreground service */
    fun startDirectCapture(context: Context) {
        _recordingKind.value = WatchRecordingService.KIND_DIRECT_CAPTURE
        val intent = Intent(context, WatchRecordingService::class.java).apply {
            action = WatchRecordingService.ACTION_START_DIRECT
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /** Stop recording via foreground service */
    fun stopRecording(context: Context) {
        val intent = Intent(context, WatchRecordingService::class.java).apply {
            action = WatchRecordingService.ACTION_STOP
        }
        context.startService(intent)
    }

    /** Toggle from physical button (triple-click) */
    fun requestToggle() {
        _toggleRequest.value = System.currentTimeMillis()
    }

    fun clearSaved() {
        _savedRecordingId.value = null
    }

    // ── Called by service only ──

    internal fun update(
        recording: Boolean,
        elapsed: Long,
        text: String,
        partial: String,
        kind: String = _recordingKind.value
    ) {
        _isRecording.value = recording
        _elapsedSeconds.value = elapsed
        _transcription.value = text
        _currentPartial.value = partial
        if (recording) {
            _recordingKind.value = kind
        }
    }

    internal fun notifySaved(recordingId: Long) {
        _savedRecordingId.value = recordingId
    }

    internal fun reset() {
        _isRecording.value = false
        _elapsedSeconds.value = 0
        _transcription.value = ""
        _currentPartial.value = ""
        _recordingKind.value = WatchRecordingService.KIND_MANUAL_RECORDING
    }
}
