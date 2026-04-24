package com.trama.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.SyncEntry
import com.trama.shared.model.SyncPayload
import com.trama.shared.model.SyncRecording
import com.trama.shared.model.WatchAudioSyncMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WatchToPhoneSyncer(
    private val context: Context,
    private val repository: DiaryRepository
) {
    companion object {
        private const val TAG = "WatchToPhoneSyncer"
        private const val SYNC_PATH = "/trama/sync"
        private const val AUDIO_RECORDING_PATH_PREFIX = "/trama/audio-recording"
        private const val AUDIO_SYNC_TIMEOUT_MS = 10_000L
    }

    suspend fun syncUnsentEntries() {
        val unsyncedEntries = repository.getUnsynced()
        val unsyncedRecordings = try {
            repository.getUnsyncedRecordings()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get unsynced recordings", e)
            emptyList()
        }

        if (unsyncedEntries.isEmpty() && unsyncedRecordings.isEmpty()) return

        val payload = SyncPayload(
            entries = unsyncedEntries.map { SyncEntry.fromDiaryEntry(it) },
            recordings = unsyncedRecordings.map { SyncRecording.fromRecording(it) }
        )

        val json = Json.encodeToString(payload)

        try {
            val request = PutDataMapRequest.create(SYNC_PATH).apply {
                dataMap.putString("payload", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest()

            withTimeout(AUDIO_SYNC_TIMEOUT_MS) {
                Wearable.getDataClient(context).putDataItem(request).await()
            }

            if (unsyncedEntries.isNotEmpty()) {
                repository.markSynced(unsyncedEntries.map { it.id })
            }
            if (unsyncedRecordings.isNotEmpty()) {
                repository.markRecordingsSynced(unsyncedRecordings.map { it.id })
            }

            Log.i(TAG, "Synced ${unsyncedEntries.size} entries + ${unsyncedRecordings.size} recordings to phone")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
        }
    }

    suspend fun syncRecordingAudio(
        pcmBytes: ByteArray,
        metadata: WatchAudioSyncMetadata
    ) {
        if (pcmBytes.isEmpty()) return

        val request = PutDataMapRequest.create("$AUDIO_RECORDING_PATH_PREFIX/${metadata.createdAt}").apply {
            dataMap.putString("metadata", Json.encodeToString(metadata))
            dataMap.putAsset("audio_pcm16", Asset.createFromBytes(pcmBytes))
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest()

        // BLE transfers can stall silently when the phone is out of range; without
        // a timeout the coroutine would hang forever and freeze the next capture.
        // Retry with exponential backoff before surfacing the failure to the caller.
        var lastError: Throwable? = null
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            try {
                withTimeout(AUDIO_SYNC_TIMEOUT_MS) {
                    Wearable.getDataClient(context).putDataItem(request).await()
                }
                Log.i(TAG, "Synced watch audio (${pcmBytes.size} bytes) on attempt $attempt")
                return
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Audio sync attempt $attempt/$maxAttempts failed: ${e.message}")
                if (attempt < maxAttempts) {
                    delay(1_000L shl (attempt - 1)) // 1s, 2s
                }
            }
        }
        throw lastError ?: IllegalStateException("Audio sync failed without error")
    }
}
