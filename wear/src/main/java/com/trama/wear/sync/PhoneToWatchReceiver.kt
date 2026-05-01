package com.trama.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.trama.shared.sync.MicCoordinator
import com.trama.wear.service.WatchServiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Receives data and messages from the phone:
 * - /trama/settings: intent patterns + custom keywords (DataClient)
 * - /trama/mic: mic coordination commands PAUSE/RESUME (MessageClient)
 */
class PhoneToWatchReceiver : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneToWatchReceiver"
        private const val SETTINGS_PATH = "/trama/settings"
        private const val MIC_PATH = "/trama/mic"
        private const val CMD_PAUSE = MicCoordinator.CMD_PAUSE
        private const val CMD_RESUME = MicCoordinator.CMD_RESUME
        private const val CMD_START_KEYWORD = MicCoordinator.CMD_START_KEYWORD
        private const val CMD_START_RECORDING = MicCoordinator.CMD_START_RECORDING

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

                when {
                    path == SETTINGS_PATH -> {
                        val patternsJson = dataMap.getString("intent_patterns_json")
                        val keywordsStr = dataMap.getString("keyword_mappings")
                        val accelGate = dataMap.getBoolean("wear_accelerometer_gate", false)
                        handleSettings(patternsJson, keywordsStr, accelGate)
                    }
                }
            }
        }
    }

    private fun handleSettings(patternsJson: String?, keywordsStr: String?, accelGate: Boolean) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()

        if (!patternsJson.isNullOrBlank()) {
            prefs.putString("intent_patterns_json", patternsJson)
            Log.i(TAG, "Intent patterns received from phone")
        }

        if (!keywordsStr.isNullOrBlank()) {
            prefs.putString("keyword_mappings", keywordsStr)
        }

        prefs.putBoolean("wear_accelerometer_gate", accelGate)
        prefs.apply()

        sendBroadcast(android.content.Intent("com.trama.wear.SETTINGS_UPDATED"))
    }

    // ── MessageClient: mic coordination ───────────────────────────────────

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != MIC_PATH) return

        val command = String(messageEvent.data)
        Log.i(TAG, "Mic command received: $command")

        when (command) {
            CMD_PAUSE -> {
                WatchServiceController.notifyPhoneActive(applicationContext)
                WatchServiceController.stopByPhone(applicationContext)
                Log.i(TAG, "Watch stopped (phone is active)")
            }
            CMD_RESUME -> {
                WatchServiceController.notifyPhoneInactive(applicationContext)
                WatchServiceController.resumeIfAllowed(applicationContext)
                Log.i(TAG, "Watch auto-resume attempted (phone released)")
            }
            CMD_START_KEYWORD -> {
                WatchServiceController.notifyPhoneInactive(applicationContext)
                WatchServiceController.startFromRemote(applicationContext)
                Log.i(TAG, "Watch started keyword listening (phone transferred)")
            }
            CMD_START_RECORDING -> {
                WatchServiceController.notifyPhoneInactive(applicationContext)
                WatchServiceController.startRecording(applicationContext, allowBackgroundStart = true)
                Log.i(TAG, "Watch started recording (phone transferred)")
            }
        }
    }
}
