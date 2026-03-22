package com.mydiary.app.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.SyncEntry
import com.mydiary.shared.model.SyncPayload
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Syncs phone diary entries to the watch via Wearable DataClient.
 * Entries are sent as JSON payload; the watch deduplicates on receive.
 */
class PhoneToWatchSyncer(
    private val context: Context,
    private val repository: DiaryRepository
) {
    companion object {
        private const val TAG = "PhoneToWatchSyncer"
        private const val SYNC_PATH = "/mydiary/phone-entries"
    }

    suspend fun syncUnsentEntries() {
        val unsynced = repository.getUnsynced()
        if (unsynced.isEmpty()) return

        val payload = SyncPayload(
            entries = unsynced.map { SyncEntry.fromDiaryEntry(it) }
        )

        val json = Json.encodeToString(payload)

        try {
            val request = PutDataMapRequest.create(SYNC_PATH).apply {
                dataMap.putString("payload", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            repository.markSynced(unsynced.map { it.id })
            Log.i(TAG, "Synced ${unsynced.size} entries to watch")
        } catch (e: Exception) {
            Log.e(TAG, "Sync to watch failed", e)
        }
    }
}
