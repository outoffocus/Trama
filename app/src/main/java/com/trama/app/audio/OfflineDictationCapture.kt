package com.trama.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.trama.app.service.ServiceController
import com.trama.shared.audio.CapturedAudioWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class OfflineDictationCapture(context: Context) {
    companion object {
        private const val SAMPLE_RATE_HZ = 16_000
        private const val READ_SIZE = 1024
    }

    private val stopRequested = AtomicBoolean(false)
    private val appContext = context.applicationContext

    fun requestStop() {
        stopRequested.set(true)
    }

    suspend fun capture(maxDurationMs: Long = 12_000L): CapturedAudioWindow? = withContext(Dispatchers.IO) {
        stopRequested.set(false)

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext null
        }

        val shouldResumeListener = ServiceController.pauseListeningForRecording(
            appContext,
            source = "offline_dictation"
        )
        try {
            if (shouldResumeListener) delay(250L)
            captureAudio(maxDurationMs)
        } finally {
            if (shouldResumeListener) {
                ServiceController.resumeListeningAfterRecording(appContext, "offline_dictation")
            }
        }
    }

    private fun captureAudio(maxDurationMs: Long): CapturedAudioWindow? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(READ_SIZE * 2)

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )
        } catch (_: Exception) {
            return null
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return null
        }

        val chunks = mutableListOf<ShortArray>()
        var totalSamples = 0
        val readBuffer = ShortArray(READ_SIZE)
        val startedAt = SystemClock.elapsedRealtime()

        try {
            try {
                audioRecord.startRecording()
            } catch (_: Exception) {
                return null
            }
            while (!stopRequested.get() && SystemClock.elapsedRealtime() - startedAt < maxDurationMs) {
                val read = audioRecord.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    chunks += readBuffer.copyOf(read)
                    totalSamples += read
                }
            }
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
        }

        if (totalSamples <= 0) return null

        val pcm = ShortArray(totalSamples)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(pcm, destinationOffset = offset)
            offset += chunk.size
        }

        return CapturedAudioWindow(
            preRollPcm = shortArrayOf(),
            livePcm = pcm,
            sampleRateHz = SAMPLE_RATE_HZ
        )
    }
}
