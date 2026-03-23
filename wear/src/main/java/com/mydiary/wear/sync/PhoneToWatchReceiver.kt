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
 * - /mydiary/settings: intent patterns + custom keywords (DataClient)
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

        const val PREFS = "watch_sync_prefs"
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
                        val patternsJson = dataMap.getString("intent_patterns_json")
                        val keywordsStr = dataMap.getString("keyword_mappings")
                        val speakerProfileJson = dataMap.getString("speaker_profile_json")
                        handleSettings(patternsJson, keywordsStr, speakerProfileJson)
                    }
                    ENTRIES_PATH -> {
                        val json = dataMap.getString("payload") ?: return@forEach
                        handleEntries(json)
                    }
                }
            }
        }
    }

    private fun handleSettings(patternsJson: String?, keywordsStr: String?, speakerProfileJson: String?) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()

        if (!patternsJson.isNullOrBlank()) {
            prefs.putString("intent_patterns_json", patternsJson)
            Log.i(TAG, "Intent patterns received from phone")
        }

        if (!keywordsStr.isNullOrBlank()) {
            prefs.putString("keyword_mappings", keywordsStr)
        }

        if (!speakerProfileJson.isNullOrBlank()) {
            prefs.putString("speaker_profile_json", speakerProfileJson)
            Log.i(TAG, "Speaker profile received from phone")
        }

        prefs.apply()

        sendBroadcast(android.content.Intent("com.mydiary.wear.SETTINGS_UPDATED"))
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
                prefs.edit().putBoolean(KEY_PHONE_ACTIVE, true).apply()
                WatchServiceController.stop(applicationContext)
                Log.i(TAG, "Watch service stopped (phone is active)")
            }
            CMD_RESUME -> {
                prefs.edit().putBoolean(KEY_PHONE_ACTIVE, false).apply()
                WatchServiceController.resumeIfAllowed(applicationContext)
                Log.i(TAG, "Watch auto-resume attempted (phone released)")
            }
        }
    }
}
