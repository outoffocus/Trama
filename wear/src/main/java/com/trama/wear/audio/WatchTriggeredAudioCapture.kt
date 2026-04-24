package com.trama.wear.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class WatchTriggeredAudioCapture {
    companion object {
        private const val SAMPLE_RATE_HZ = 16_000
        private const val READ_SIZE = 1024
        private const val MAX_DURATION_MS = 45_000L  // 45s — enough for a long verbal note
        private const val MIN_DURATION_MS = 1_200L
        private const val SILENCE_STOP_MS = 3_000L   // 3s pause → stop (natural speech rhythm)
        private const val SILENCE_RMS_THRESHOLD = 700.0
        // Minimum RMS for the full capture to be considered real audio.
        // Captures below this are likely mic-contention artifacts (all-zero PCM).
        private const val MIN_CAPTURE_RMS = 300.0
    }

    suspend fun capture(): ShortArray = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(READ_SIZE * 2)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )
        } catch (_: Exception) {
            return@withContext shortArrayOf()
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext shortArrayOf()
        }

        val chunks = mutableListOf<ShortArray>()
        var totalSamples = 0
        val buffer = ShortArray(READ_SIZE)
        val startedAt = SystemClock.elapsedRealtime()
        var silenceStartedAt = 0L

        try {
            record.startRecording()
            while (SystemClock.elapsedRealtime() - startedAt < MAX_DURATION_MS) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val chunk = buffer.copyOf(read)
                chunks += chunk
                totalSamples += read

                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val isSilent = rms(chunk) < SILENCE_RMS_THRESHOLD

                if (elapsed >= MIN_DURATION_MS) {
                    if (isSilent) {
                        if (silenceStartedAt == 0L) {
                            silenceStartedAt = SystemClock.elapsedRealtime()
                        } else if (SystemClock.elapsedRealtime() - silenceStartedAt >= SILENCE_STOP_MS) {
                            break
                        }
                    } else {
                        silenceStartedAt = 0L
                    }
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }

        if (totalSamples <= 0) {
            return@withContext shortArrayOf()
        }

        val merged = ShortArray(totalSamples)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(merged, destinationOffset = offset)
            offset += chunk.size
        }

        // Reject captures that are all-zero or near-silent — this happens when
        // the mic was not fully released by SpeechRecognizer before AudioRecord opened it.
        if (rms(merged) < MIN_CAPTURE_RMS) {
            return@withContext shortArrayOf()
        }

        merged
    }

    private fun rms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        samples.forEach { sample ->
            val value = abs(sample.toInt()).toDouble()
            sum += value * value
        }
        return kotlin.math.sqrt(sum / samples.size)
    }
}
