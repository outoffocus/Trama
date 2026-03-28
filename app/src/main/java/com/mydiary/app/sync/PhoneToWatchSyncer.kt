package com.mydiary.app.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.StatusSyncEntry
import com.mydiary.shared.model.StatusSyncPayload
import com.mydiary.shared.model.SyncEntry
import com.mydiary.shared.model.SyncPayload
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Syncs phone diary entries to the watch via Wearable DataClient.
 * - New entries: sent as JSON payload; the watch deduplicates on receive.
 * - Status changes: completed/deleted entries synced so watch stays in sync.
 */
class PhoneToWatchSyncer(
    private val context: Context,
    private val repository: DiaryRepository
) {
    companion object {
        private const val TAG = "PhoneToWatchSyncer"
        private const val SYNC_PATH = "/mydiary/phone-entries"
        private const val STATUS_SYNC_PATH = "/mydiary/status-sync"
    }

    /**
     * Full sync: send ALL entries (with current status) to watch.
     * The watch will insert new ones and update statuses of existing ones.
     */
    suspend fun syncAllToWatch() {
        val all = repository.getAllOnce()
        if (all.isEmpty()) return

        val payload = SyncPayload(
            entries = all.map { SyncEntry.fromDiaryEntry(it) }
        )
        val json = Json.encodeToString(payload)

        try {
            val request = PutDataMapRequest.create(SYNC_PATH).apply {
                dataMap.putString("payload", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            Log.i(TAG, "Full sync to watch: ${all.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Full sync to watch failed", e)
        }
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

    /**
     * Sync status changes to watch. Call this when entries are completed or deleted on phone.
     */
    suspend fun syncStatusChange(
        completed: List<StatusSyncEntry> = emptyList(),
        deleted: List<StatusSyncEntry> = emptyList()
    ) {
        if (completed.isEmpty() && deleted.isEmpty()) return

        val payload = StatusSyncPayload(completed = completed, deleted = deleted)
        val json = Json.encodeToString(payload)

        try {
            val request = PutDataMapRequest.create(STATUS_SYNC_PATH).apply {
                dataMap.putString("payload", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            Log.i(TAG, "Status sync to watch: ${completed.size} completed, ${deleted.size} deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Status sync to watch failed", e)
        }
    }
}
