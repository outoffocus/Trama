package com.trama.app.sync

import android.content.Context
import android.util.Log
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.StatusSyncEntry

/**
 * Explicit no-op for phone diary -> watch diary mirroring.
 *
 * The watch does not render phone diary entries, so sending the full diary over
 * Wear Data Layer only wastes bandwidth and can exceed DataItem limits. Keep
 * settings sync and mic coordination in their dedicated paths.
 */
class PhoneToWatchSyncer(
    @Suppress("unused") private val context: Context,
    @Suppress("unused") private val repository: DiaryRepository
) {
    companion object {
        private const val TAG = "PhoneToWatchSyncer"
    }

    suspend fun syncAllToWatch() {
        Log.d(TAG, "Skipping full diary sync: watch does not display phone entries")
    }

    suspend fun syncUnsentEntries() {
        Log.d(TAG, "Skipping incremental diary sync: watch does not display phone entries")
    }

    suspend fun syncStatusChange(
        completed: List<StatusSyncEntry> = emptyList(),
        deleted: List<StatusSyncEntry> = emptyList()
    ) {
        if (completed.isNotEmpty() || deleted.isNotEmpty()) {
            Log.d(
                TAG,
                "Skipping diary status sync: completed=${completed.size}, deleted=${deleted.size}"
            )
        }
    }
}
