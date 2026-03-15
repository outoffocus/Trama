package com.mydiary.app.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

class SettingsSyncer(private val context: Context) {

    companion object {
        private const val TAG = "SettingsSyncer"
        private const val SETTINGS_PATH = "/mydiary/settings"
    }

    suspend fun syncSettings(recordingDuration: Int, keywords: List<String>) {
        try {
            val request = PutDataMapRequest.create(SETTINGS_PATH).apply {
                dataMap.putInt("recording_duration", recordingDuration)
                dataMap.putString("keywords", keywords.joinToString(","))
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            Log.i(TAG, "Settings synced to watch")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync settings", e)
        }
    }
}
