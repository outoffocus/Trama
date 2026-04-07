package com.trama.app.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.trama.shared.speech.IntentPattern
import com.trama.shared.speech.SpeakerProfile
import kotlinx.coroutines.tasks.await

/**
 * Syncs intent patterns and custom keywords from phone to watch via Wearable DataClient.
 */
class SettingsSyncer(private val context: Context) {

    companion object {
        private const val TAG = "SettingsSyncer"
        private const val SETTINGS_PATH = "/trama/settings"
    }

    /**
     * Sync custom keywords to watch (backward compat).
     */
    suspend fun syncSettings(keywords: List<String>) {
        try {
            val keywordsStr = keywords.joinToString(",")

            val request = PutDataMapRequest.create(SETTINGS_PATH).apply {
                dataMap.putString("keyword_mappings", keywordsStr)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            Log.i(TAG, "Settings synced to watch: ${keywords.size} keywords")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync settings", e)
        }
    }

    /**
     * Sync full intent patterns + custom keywords + speaker profile to watch.
     */
    suspend fun syncPatterns(
        patterns: List<IntentPattern>,
        customKeywords: List<String>,
        speakerProfile: SpeakerProfile? = null
    ) {
        try {
            val patternsJson = IntentPattern.serialize(patterns)
            val keywordsStr = customKeywords.joinToString(",")

            val request = PutDataMapRequest.create(SETTINGS_PATH).apply {
                dataMap.putString("intent_patterns_json", patternsJson)
                dataMap.putString("keyword_mappings", keywordsStr)
                if (speakerProfile != null) {
                    dataMap.putString("speaker_profile_json", SpeakerProfile.serialize(speakerProfile))
                }
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            Log.i(TAG, "Synced to watch: ${patterns.size} patterns, ${customKeywords.size} keywords" +
                if (speakerProfile != null) ", speaker profile" else "")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync patterns", e)
        }
    }
}
