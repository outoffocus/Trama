package com.trama.app.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Voice Activity Detector using AudioRecord + dB threshold.
 *
 * Ultra-lightweight approach: continuously reads mic audio and calculates RMS.
 * When volume exceeds a threshold (someone talking nearby), notifies the callback.
 * When silence returns, notifies silence callback.
 *
 * This avoids running SpeechRecognizer during silence, saving ~60-70% battery.
 *
 * For the phone, this is used as a gate before SpeechRecognizer.
 * For the watch, same approach (dB threshold is ideal for low-resource devices).
 */
class VoiceActivityDetector(private val context: Context) {

    companion object {
        private const val TAG = "VoiceActivityDetector"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // Thresholds calibrated for typical use:
        // -45 dB is ambient room noise, -30 dB is normal speech at arm's length
        private const val SPEECH_THRESHOLD_DB = -38.0  // Start recognizer
        private const val SILENCE_THRESHOLD_DB = -45.0  // Stop recognizer

        // Require sustained speech/silence to avoid false triggers
        private const val SPEECH_FRAMES_REQUIRED = 8    // ~256ms of speech
        private const val SILENCE_FRAMES_REQUIRED = 60  // ~2s of silence

        private const val FRAME_SIZE = 512  // samples per frame (~32ms at 16kHz)
    }

    private var audioRecord: AudioRecord? = null

    @Volatile
    private var running = false

    var onVoiceDetected: (() -> Unit)? = null
    var onSilenceDetected: (() -> Unit)? = null

    /**
     * Start the VAD loop. Runs continuously on IO dispatcher.
     * Call stop() to terminate.
     */
    suspend fun start() = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return@withContext
        }

        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING),
            FRAME_SIZE * 2
        )

        try {
            @Suppress("MissingPermission")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            return@withContext
        }

        val record = audioRecord ?: return@withContext
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            record.release()
            audioRecord = null
            return@withContext
        }

        running = true
        record.startRecording()
        Log.i(TAG, "VAD started")

        val buffer = ShortArray(FRAME_SIZE)
        var speechFrames = 0
        var silenceFrames = 0
        var isSpeaking = false

        while (running && isActive) {
            val read = record.read(buffer, 0, FRAME_SIZE)
            if (read <= 0) continue

            val rms = calculateRMS(buffer, read)
            val dbFS = if (rms > 0) 20.0 * log10(rms / 32768.0) else -100.0

            if (dbFS > SPEECH_THRESHOLD_DB) {
                speechFrames++
                silenceFrames = 0

                if (!isSpeaking && speechFrames >= SPEECH_FRAMES_REQUIRED) {
                    isSpeaking = true
                    Log.i(TAG, "Voice detected (${String.format("%.1f", dbFS)} dB)")
                    withContext(Dispatchers.Main) { onVoiceDetected?.invoke() }
                }
            } else if (dbFS < SILENCE_THRESHOLD_DB) {
                silenceFrames++
                speechFrames = 0

                if (isSpeaking && silenceFrames >= SILENCE_FRAMES_REQUIRED) {
                    isSpeaking = false
                    Log.i(TAG, "Silence detected (${String.format("%.1f", dbFS)} dB)")
                    withContext(Dispatchers.Main) { onSilenceDetected?.invoke() }
                }
            }
        }

        record.stop()
        record.release()
        audioRecord = null
        Log.i(TAG, "VAD stopped")
    }

    fun stop() {
        running = false
    }

    fun isRunning(): Boolean = running

    private fun calculateRMS(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / length)
    }
}
