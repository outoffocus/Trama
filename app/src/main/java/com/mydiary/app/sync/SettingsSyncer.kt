package com.mydiary.app.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Syncs settings from phone to watch via Wearable DataClient.
 * Sends keyword→category mappings so the watch uses the same keywords.
 */
class SettingsSyncer(private val context: Context) {

    companion object {
        private const val TAG = "SettingsSyncer"
        private const val SETTINGS_PATH = "/mydiary/settings"
    }

    /**
     * Sync keyword mappings to watch.
     * @param keywordMappings Map of keyword → categoryId (e.g. "recordar" → "TODO")
     */
    suspend fun syncSettings(keywordMappings: Map<String, String>) {
        try {
            // Serialize as "keyword:category,keyword:category"
            val mappingsStr = keywordMappings.entries
                .joinToString(",") { "${it.key}:${it.value}" }

            val request = PutDataMapRequest.create(SETTINGS_PATH).apply {
                dataMap.putString("keyword_mappings", mappingsStr)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            Log.i(TAG, "Settings synced to watch: ${keywordMappings.size} keywords")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync settings", e)
        }
    }
}
