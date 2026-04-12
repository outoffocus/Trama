package com.trama.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.trama.shared.audio.CapturedAudioWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class OfflineDictationCapture {
    companion object {
        private const val SAMPLE_RATE_HZ = 16_000
        private const val READ_SIZE = 1024
    }

    private val stopRequested = AtomicBoolean(false)

    fun requestStop() {
        stopRequested.set(true)
    }

    suspend fun capture(maxDurationMs: Long = 12_000L): CapturedAudioWindow? = withContext(Dispatchers.IO) {
        stopRequested.set(false)

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
            return@withContext null
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return@withContext null
        }

        val chunks = mutableListOf<ShortArray>()
        var totalSamples = 0
        val readBuffer = ShortArray(READ_SIZE)
        val startedAt = SystemClock.elapsedRealtime()

        try {
            audioRecord.startRecording()
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

        if (totalSamples <= 0) return@withContext null

        val pcm = ShortArray(totalSamples)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(pcm, destinationOffset = offset)
            offset += chunk.size
        }

        CapturedAudioWindow(
            preRollPcm = shortArrayOf(),
            livePcm = pcm,
            sampleRateHz = SAMPLE_RATE_HZ
        )
    }
}
