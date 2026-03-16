package com.mydiary.app.speech

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.mydiary.shared.model.CategoryInfo

/**
 * Uses Gemini Nano on-device to:
 * 1. Auto-categorize transcribed entries based on content
 * 2. Clean up speech recognition errors
 *
 * Falls back gracefully when Gemini is unavailable.
 */
class GeminiProcessor {

    companion object {
        private const val TAG = "GeminiProcessor"
    }

    private var model: GenerativeModel? = null
    private var available = false

    suspend fun initialize(): Boolean {
        return try {
            val client = Generation.getClient()
            val status = client.checkStatus()
            Log.i(TAG, "Gemini Nano status: $status")

            when (status) {
                FeatureStatus.AVAILABLE -> {
                    client.warmup()
                    model = client
                    available = true
                    true
                }
                FeatureStatus.DOWNLOADABLE -> {
                    Log.i(TAG, "Gemini Nano model needs download, starting...")
                    client.download().collect { downloadStatus ->
                        when (downloadStatus) {
                            is DownloadStatus.DownloadStarted ->
                                Log.i(TAG, "Download started")
                            is DownloadStatus.DownloadProgress ->
                                Log.i(TAG, "Downloaded ${downloadStatus.totalBytesDownloaded} bytes")
                            DownloadStatus.DownloadCompleted ->
                                Log.i(TAG, "Download completed")
                            is DownloadStatus.DownloadFailed ->
                                Log.e(TAG, "Download failed", downloadStatus.e)
                        }
                    }
                    if (client.checkStatus() == FeatureStatus.AVAILABLE) {
                        client.warmup()
                        model = client
                        available = true
                        true
                    } else {
                        false
                    }
                }
                else -> {
                    Log.w(TAG, "Gemini Nano not available: $status")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemini Nano", e)
            false
        }
    }

    fun isAvailable(): Boolean = available

    /**
     * Categorize text based on content using the user's categories.
     * Returns the category ID, or null if classification fails.
     */
    suspend fun categorize(text: String, categories: List<CategoryInfo>): String? {
        val m = model ?: return null
        if (categories.isEmpty()) return null

        val categoryList = categories.joinToString(", ") { "${it.id} (${it.label})" }

        val prompt = """Classify this Spanish text into exactly one category.
Categories: $categoryList

Text: "$text"

Reply with ONLY the category ID, nothing else."""

        return try {
            val response = m.generateContent(prompt)
            val result = response.candidates.firstOrNull()?.text?.trim()?.uppercase()
            val matched = categories.find { it.id.equals(result, ignoreCase = true) }
            if (matched != null) {
                Log.i(TAG, "Categorized '$text' → ${matched.id}")
                matched.id
            } else {
                Log.w(TAG, "Gemini returned unknown category: '$result'")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Categorization failed", e)
            null
        }
    }

    /**
     * Clean up speech recognition errors in Spanish text.
     * Returns corrected text, or original if correction fails.
     */
    suspend fun correctText(text: String): String {
        val m = model ?: return text

        val prompt = """Fix speech recognition errors in this Spanish text. Keep the same meaning and words, only fix obvious misspellings and wrong words from speech-to-text. Do NOT add punctuation or change the structure.

Text: "$text"

Reply with ONLY the corrected text, nothing else."""

        return try {
            val response = m.generateContent(prompt)
            val result = response.candidates.firstOrNull()?.text?.trim()
            if (!result.isNullOrBlank() && result.length < text.length * 3) {
                Log.i(TAG, "Corrected: '$text' → '$result'")
                result
            } else {
                text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text correction failed", e)
            text
        }
    }

    fun close() {
        model?.close()
        model = null
        available = false
    }
}
