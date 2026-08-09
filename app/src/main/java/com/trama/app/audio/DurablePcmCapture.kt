package com.trama.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class DurablePcmCapture(context: Context) {
    data class Result(val sampleCount: Long)

    companion object {
        private const val READ_SIZE = 1024
        private const val SYNC_INTERVAL_MS = 5_000L
    }

    private val appContext = context.applicationContext
    private val stopRequested = AtomicBoolean(false)

    fun requestStop() {
        stopRequested.set(true)
    }

    suspend fun capture(pendingFile: File, maxDurationMs: Long): Result? =
        withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) return@withContext null

            pendingFile.parentFile?.mkdirs()
            val minBufferSize = AudioRecord.getMinBufferSize(
                PcmRecordingStorage.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(READ_SIZE * 2)
            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    PcmRecordingStorage.SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize
                )
            } catch (_: Exception) {
                return@withContext null
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return@withContext null
            }

            var totalSamples = 0L
            val samples = ShortArray(READ_SIZE)
            val bytes = ByteArray(READ_SIZE * PcmRecordingStorage.BYTES_PER_SAMPLE)
            val startedAt = SystemClock.elapsedRealtime()
            var lastSyncAt = startedAt
            try {
                FileOutputStream(pendingFile, false).use { output ->
                    record.startRecording()
                    while (!stopRequested.get() &&
                        SystemClock.elapsedRealtime() - startedAt < maxDurationMs
                    ) {
                        val read = record.read(samples, 0, samples.size)
                        if (read < 0) break
                        if (read == 0) continue
                        repeat(read) { index ->
                            val value = samples[index].toInt()
                            bytes[index * 2] = value.toByte()
                            bytes[index * 2 + 1] = (value ushr 8).toByte()
                        }
                        output.write(bytes, 0, read * PcmRecordingStorage.BYTES_PER_SAMPLE)
                        totalSamples += read
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastSyncAt >= SYNC_INTERVAL_MS) {
                            output.flush()
                            output.fd.sync()
                            lastSyncAt = now
                        }
                    }
                    output.flush()
                    output.fd.sync()
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
            Result(totalSamples).takeIf { totalSamples > 0L }
        }
}
