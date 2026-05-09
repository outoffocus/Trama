package com.trama.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state for recording mode. Allows UI to observe and control
 * the RecordingService without tight coupling.
 */
object RecordingState {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _currentPartial = MutableStateFlow("")
    val currentPartial: StateFlow<String> = _currentPartial.asStateFlow()

    // ID of the recording being saved (set after stop, for navigation)
    private val _savedRecordingId = MutableStateFlow<Long?>(null)
    val savedRecordingId: StateFlow<Long?> = _savedRecordingId.asStateFlow()

    // Last processing error (shown as snackbar then cleared)
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun startRecording(context: Context) {
        _isRecording.value = true
        _isProcessing.value = false
        _elapsedSeconds.value = 0
        _transcription.value = ""
        _currentPartial.value = "Iniciando grabación..."
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }.onFailure { error ->
            reset()
            notifyError(error.message ?: "No se pudo iniciar la grabación")
        }
    }

    fun stopRecording(context: Context) {
        if (_isRecording.value) {
            _isRecording.value = false
            _isProcessing.value = true
            _currentPartial.value = "Transcribiendo..."
        }
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        runCatching {
            context.startService(intent)
        }.onFailure { error ->
            reset()
            notifyError(error.message ?: "No se pudo parar la grabación")
        }
    }

    // Called by the service — do not call from UI
    internal fun update(
        recording: Boolean,
        elapsed: Long,
        text: String,
        partial: String,
        processing: Boolean = false
    ) {
        _isRecording.value = recording
        _isProcessing.value = processing
        _elapsedSeconds.value = elapsed
        _transcription.value = text
        _currentPartial.value = partial
    }

    internal fun notifySaved(recordingId: Long) {
        _savedRecordingId.value = recordingId
    }

    internal fun clearSaved() {
        _savedRecordingId.value = null
    }

    internal fun notifyError(message: String) {
        _lastError.value = message
    }

    fun clearError() {
        _lastError.value = null
    }

    internal fun reset() {
        _isRecording.value = false
        _isProcessing.value = false
        _elapsedSeconds.value = 0
        _transcription.value = ""
        _currentPartial.value = ""
    }
}
