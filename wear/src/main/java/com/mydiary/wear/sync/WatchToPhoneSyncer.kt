package com.mydiary.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.SyncEntry
import com.mydiary.shared.model.SyncPayload
import com.mydiary.shared.model.SyncRecording
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WatchToPhoneSyncer(
    private val context: Context,
    private val repository: DiaryRepository
) {
    companion object {
        private const val TAG = "WatchToPhoneSyncer"
        private const val SYNC_PATH = "/mydiary/sync"
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

            Wearable.getDataClient(context).putDataItem(request).await()

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
}
