package com.trama.wear.audio

import android.content.Context
import android.util.Log
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.Source
import com.trama.shared.model.WatchAudioSyncMetadata
import com.trama.wear.sync.WatchToPhoneSyncer

object WatchRecordingRecovery {
    private const val TAG = "WatchRecordingRecovery"
    private const val SAMPLE_RATE_HZ = 16_000

    suspend fun retryPending(context: Context) {
        val repository = DatabaseProvider.getRepository(context)
        val syncer = WatchToPhoneSyncer(context, repository)
        WatchRecordingFileStore.recoverable(context).forEach { capture ->
            val file = runCatching { WatchRecordingFileStore.finalize(capture.file) }
                .getOrElse { return@forEach }
            if (file.length() <= 0L) return@forEach
            val bytes = file.readBytes()
            val metadata = WatchAudioSyncMetadata(
                createdAt = capture.createdAt,
                durationSeconds = (bytes.size / 2 / SAMPLE_RATE_HZ).coerceAtLeast(1),
                sampleRateHz = SAMPLE_RATE_HZ,
                source = Source.WATCH.name,
                kind = capture.kind,
                pcmByteCount = bytes.size,
                pcmSampleCount = bytes.size / 2,
                rms = rms(bytes)
            )
            runCatching { syncer.syncRecordingAudio(bytes, metadata) }
                .onSuccess {
                    file.delete()
                    Log.i(TAG, "Recovered and synced ${file.name}")
                }
                .onFailure { Log.w(TAG, "Pending watch audio remains for retry", it) }
        }
    }

    private fun rms(bytes: ByteArray): Double {
        val samples = bytes.size / 2
        if (samples == 0) return 0.0
        var sum = 0.0
        repeat(samples) { index ->
            val offset = index * 2
            val sample = (((bytes[offset + 1].toInt() and 0xff) shl 8) or
                (bytes[offset].toInt() and 0xff)).toShort().toInt()
            sum += sample.toDouble() * sample.toDouble()
        }
        return kotlin.math.sqrt(sum / samples)
    }
}
