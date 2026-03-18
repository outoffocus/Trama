package com.mydiary.app.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.mydiary.app.service.ServiceController
import com.mydiary.app.ui.DatabaseProvider
import com.mydiary.shared.data.DiaryRepository
import com.mydiary.shared.model.SyncPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Receives diary entries and mic coordination from the watch.
 * - DataClient /mydiary/sync: entry sync
 * - MessageClient /mydiary/mic: PAUSE/RESUME commands
 */
class WatchDataReceiverService : WearableListenerService() {

    companion object {
        private const val TAG = "WatchDataReceiver"
        private const val SYNC_PATH = "/mydiary/sync"
        private const val MIC_PATH = "/mydiary/mic"
        private const val CMD_PAUSE = "PAUSE"
        private const val CMD_RESUME = "RESUME"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val repository = DatabaseProvider.getRepository(applicationContext)

        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: return@forEach
                if (path == SYNC_PATH) {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val json = dataMap.getString("payload") ?: return@forEach
                    handleEntries(json, repository)
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != MIC_PATH) return

        val command = String(messageEvent.data)
        Log.i(TAG, "Mic command from watch: $command")

        when (command) {
            CMD_PAUSE -> {
                // Watch is listening → stop phone service
                if (ServiceController.isRunning.value) {
                    ServiceController.stopByWatch(applicationContext)
                    Log.i(TAG, "Phone service paused (watch is active)")
                }
            }
            CMD_RESUME -> {
                // Watch stopped → resume phone service if user had it enabled
                if (ServiceController.shouldBeRunning(applicationContext)) {
                    ServiceController.start(applicationContext)
                    Log.i(TAG, "Phone service resumed (watch released)")
                }
            }
        }
    }

    private fun handleEntries(json: String, repository: DiaryRepository) {
        scope.launch {
            try {
                val payload = Json.decodeFromString<SyncPayload>(json)
                var inserted = 0
                for (syncEntry in payload.entries) {
                    val entry = syncEntry.toDiaryEntry()
                    if (!repository.existsByCreatedAtAndText(entry.createdAt, entry.text)) {
                        repository.insert(entry)
                        inserted++
                    }
                }
                Log.i(TAG, "Received ${payload.entries.size} entries from watch, inserted $inserted new")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process sync data", e)
            }
        }
    }
}
