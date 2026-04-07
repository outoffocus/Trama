package com.trama.app.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.trama.app.service.RecordingState
import com.trama.app.service.ServiceController
import com.trama.shared.sync.MicCoordinator
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.Recording
import com.trama.shared.model.RecordingStatus
import com.trama.shared.model.SyncPayload
import com.trama.app.summary.RecordingProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Receives diary entries and mic coordination from the watch.
 * - DataClient /trama/sync: entry sync
 * - MessageClient /trama/mic: PAUSE/RESUME commands
 */
class WatchDataReceiverService : WearableListenerService() {

    companion object {
        private const val TAG = "WatchDataReceiver"
        private const val SYNC_PATH = "/trama/sync"
        private const val MIC_PATH = "/trama/mic"
        private const val SYNC_REQUEST_PATH = "/trama/request-full-sync"
        private const val CMD_PAUSE = MicCoordinator.CMD_PAUSE
        private const val CMD_RESUME = MicCoordinator.CMD_RESUME
        private const val CMD_START_KEYWORD = MicCoordinator.CMD_START_KEYWORD
        private const val CMD_START_RECORDING = MicCoordinator.CMD_START_RECORDING
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
        when (messageEvent.path) {
            MIC_PATH -> {
                val command = String(messageEvent.data)
                Log.i(TAG, "Mic command from watch: $command")

                when (command) {
                    CMD_PAUSE -> {
                        ServiceController.notifyWatchActive()
                        ServiceController.stopByWatch(applicationContext)
                        Log.i(TAG, "Phone stopped (watch is active)")
                    }
                    CMD_RESUME -> {
                        ServiceController.notifyWatchInactive()
                        if (ServiceController.shouldBeRunning(applicationContext)) {
                            ServiceController.start(applicationContext)
                            Log.i(TAG, "Phone service resumed (watch released)")
                        }
                    }
                    CMD_START_KEYWORD -> {
                        ServiceController.notifyWatchInactive()
                        ServiceController.start(applicationContext)
                        Log.i(TAG, "Phone started keyword listening (watch transferred)")
                    }
                    CMD_START_RECORDING -> {
                        ServiceController.notifyWatchInactive()
                        ServiceController.startRecording(applicationContext)
                        Log.i(TAG, "Phone started recording (watch transferred)")
                    }
                }
            }
            SYNC_REQUEST_PATH -> {
                Log.i(TAG, "Full sync requested by watch")
                scope.launch {
                    val repository = DatabaseProvider.getRepository(applicationContext)
                    val syncer = PhoneToWatchSyncer(applicationContext, repository)
                    syncer.syncAllToWatch()
                }
            }
        }
    }

    private fun handleEntries(json: String, repository: DiaryRepository) {
        scope.launch {
            try {
                val payload = Json.decodeFromString<SyncPayload>(json)

                // Sync diary entries
                var insertedEntries = 0
                for (syncEntry in payload.entries) {
                    val entry = syncEntry.toDiaryEntry()
                    if (!repository.existsByCreatedAtAndText(entry.createdAt, entry.text)) {
                        repository.insert(entry)
                        insertedEntries++
                    }
                }

                // Sync recordings and process them with Gemini
                var insertedRecordings = 0
                for (syncRecording in payload.recordings) {
                    if (!repository.existsRecordingByCreatedAt(syncRecording.createdAt)) {
                        val recording = syncRecording.toRecording()
                        val recordingId = repository.insertRecording(recording)
                        insertedRecordings++
                        Log.i(TAG, "Received recording from watch (id=$recordingId), processing...")

                        // Process with Gemini (or local fallback)
                        try {
                            val processor = RecordingProcessor(applicationContext)
                            processor.process(recordingId, repository)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process recording $recordingId", e)
                            repository.updateRecordingStatus(recordingId, RecordingStatus.FAILED)
                        }
                    }
                }

                Log.i(TAG, "Received from watch: ${payload.entries.size} entries (inserted $insertedEntries), " +
                        "${payload.recordings.size} recordings (inserted $insertedRecordings)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process sync data", e)
            }
        }
    }
}
