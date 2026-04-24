package com.trama.app.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.trama.shared.speech.IntentPattern
import kotlinx.coroutines.tasks.await

/**
 * Syncs intent patterns and custom keywords from phone to watch via Wearable DataClient.
 */
class SettingsSyncer(private val context: Context) {

    companion object {
        private const val TAG = "SettingsSyncer"
        private const val SETTINGS_PATH = "/trama/settings"
        private const val PREFS = "watch_settings_sync"
        private const val KEY_LAST_SIGNATURE = "last_signature"
    }

    /**
     * Sync custom keywords to watch (backward compat).
     */
    suspend fun syncSettings(keywords: List<String>, force: Boolean = false) {
        if (!shouldSyncToWatch()) return
        try {
            val keywordsStr = keywords.joinToString(",")
            val signature = "keywords:$keywordsStr"
            if (!force && isAlreadySynced(signature)) {
                Log.d(TAG, "Skipping watch keyword sync: unchanged")
                return
            }

            val request = PutDataMapRequest.create(SETTINGS_PATH).apply {
                dataMap.putString("keyword_mappings", keywordsStr)
                dataMap.putLong("updated_at", System.currentTimeMillis())
            }.asPutDataRequest()

            Wearable.getDataClient(context).putDataItem(request).await()
            markSynced(signature)
            Log.i(TAG, "Settings synced to watch: ${keywords.size} keywords")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync settings", e)
        }
    }

    /**
     * Sync full intent patterns + custom keywords to watch.
     */
    suspend fun syncPatterns(
        patterns: List<IntentPattern>,
        customKeywords: List<String>,
        force: Boolean = false
    ) {
        if (!shouldSyncToWatch()) return
        try {
            val patternsJson = IntentPattern.serialize(patterns)
            val keywordsStr = customKeywords.joinToString(",")
            val signature = "patterns:${patternsJson.hashCode()}:keywords:${keywordsStr.hashCode()}"
            if (!force && isAlreadySynced(signature)) {
                Log.d(TAG, "Skipping watch settings sync: unchanged")
                return
            }

            val request = PutDataMapRequest.create(SETTINGS_PATH).apply {
                dataMap.putString("intent_patterns_json", patternsJson)
                dataMap.putString("keyword_mappings", keywordsStr)
                dataMap.putLong("updated_at", System.currentTimeMillis())
            }.asPutDataRequest()

            Wearable.getDataClient(context).putDataItem(request).await()
            markSynced(signature)
            Log.i(TAG, "Synced to watch: ${patterns.size} patterns, ${customKeywords.size} keywords")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync patterns", e)
        }
    }

    private suspend fun shouldSyncToWatch(): Boolean {
        val hasConnectedWatch = runCatching {
            Wearable.getNodeClient(context).connectedNodes.await().isNotEmpty()
        }.getOrElse {
            Log.w(TAG, "Failed to query connected watch nodes", it)
            false
        }

        if (!hasConnectedWatch) {
            Log.d(TAG, "Skipping watch settings sync: no connected watch")
        }
        return hasConnectedWatch
    }

    private fun isAlreadySynced(signature: String): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SIGNATURE, null) == signature
    }

    private fun markSynced(signature: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SIGNATURE, signature)
            .apply()
    }
}
