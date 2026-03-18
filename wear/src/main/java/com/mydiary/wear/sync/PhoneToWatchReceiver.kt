package com.mydiary.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.mydiary.shared.model.SyncPayload
import com.mydiary.wear.service.WatchServiceController
import com.mydiary.wear.ui.DatabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Receives data and messages from the phone:
 * - /mydiary/settings: keyword mappings (DataClient)
 * - /mydiary/phone-entries: diary entries from phone (DataClient)
 * - /mydiary/mic: mic coordination commands PAUSE/RESUME (MessageClient)
 */
class PhoneToWatchReceiver : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneToWatchReceiver"
        private const val SETTINGS_PATH = "/mydiary/settings"
        private const val ENTRIES_PATH = "/mydiary/phone-entries"
        private const val MIC_PATH = "/mydiary/mic"
        private const val CMD_PAUSE = "PAUSE"
        private const val CMD_RESUME = "RESUME"

        private const val PREFS = "watch_sync_prefs"
        private const val KEY_PHONE_ACTIVE = "phone_active"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── DataClient: settings and entries ──────────────────────────────────

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: return@forEach
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                when (path) {
                    SETTINGS_PATH -> {
                        val mappingsStr = dataMap.getString("keyword_mappings") ?: return@forEach
                        handleSettings(mappingsStr)
                    }
                    ENTRIES_PATH -> {
                        val json = dataMap.getString("payload") ?: return@forEach
                        handleEntries(json)
                    }
                }
            }
        }
    }

    private fun handleSettings(mappingsStr: String) {
        // Parse "keyword:category,keyword:category"
        val mappings = mappingsStr.split(",").mapNotNull { pair ->
            val parts = pair.trim().split(":")
            if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim()
            else null
        }.toMap()

        if (mappings.isEmpty()) return

        // Store in SharedPreferences for the watch service to pick up
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("keyword_mappings", mappingsStr)
            .apply()

        Log.i(TAG, "Settings received: ${mappings.size} keywords: ${mappings.keys}")

        // Notify the running service via broadcast
        val intent = android.content.Intent("com.mydiary.wear.SETTINGS_UPDATED")
        sendBroadcast(intent)
    }

    private fun handleEntries(json: String) {
        scope.launch {
            try {
                val repository = DatabaseProvider.getRepository(applicationContext)
                val payload = Json.decodeFromString<SyncPayload>(json)
                var inserted = 0
                for (syncEntry in payload.entries) {
                    val entry = syncEntry.toDiaryEntry()
                    if (!repository.existsByCreatedAtAndText(entry.createdAt, entry.text)) {
                        repository.insert(entry)
                        inserted++
                    }
                }
                Log.i(TAG, "Received ${payload.entries.size} entries from phone, inserted $inserted new")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process phone entries", e)
            }
        }
    }

    // ── MessageClient: mic coordination ───────────────────────────────────

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != MIC_PATH) return

        val command = String(messageEvent.data)
        Log.i(TAG, "Mic command received: $command")

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        when (command) {
            CMD_PAUSE -> {
                // Phone is listening → stop watch service
                prefs.edit().putBoolean(KEY_PHONE_ACTIVE, true).apply()
                WatchServiceController.stop(applicationContext)
                Log.i(TAG, "Watch service stopped (phone is active)")
            }
            CMD_RESUME -> {
                // Phone stopped → restart watch service if user had it enabled
                prefs.edit().putBoolean(KEY_PHONE_ACTIVE, false).apply()
                WatchServiceController.resumeIfAllowed(applicationContext)
                Log.i(TAG, "Watch auto-resume attempted (phone released)")
            }
        }
    }
}
