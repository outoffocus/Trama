package com.trama.wear.audio

import android.content.Context
import android.util.Log
import com.trama.shared.audio.AssetFileCache
import com.trama.shared.audio.CapturedAudioWindow
import com.trama.shared.audio.LightweightGateAsr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer

class VoskGateAsr(
    context: Context
) : LightweightGateAsr {

    companion object {
        private const val TAG = "VoskGateAsr"
        private const val MODEL_DIR = "asr/vosk/model"
    }

    private val appContext = context.applicationContext
    private val assetCache = AssetFileCache(appContext)
    private val recognizerMutex = Mutex()
    private var model: Model? = null

    override val name: String
        get() = if (isAvailable) "vosk-gate:model" else "vosk-gate:unavailable"

    override val isAvailable: Boolean
        get() = assetCache.listAssets(MODEL_DIR).isNotEmpty()

    override suspend fun transcribe(window: CapturedAudioWindow, languageTag: String): String? {
        val pcm = window.mergedPcm()
        if (pcm.isEmpty() || !isAvailable) return null

        return withContext(Dispatchers.IO) {
            recognizerMutex.withLock {
                val model = model ?: createModel().also { model = it }
                // Use the constructor that takes only Model and sample rate to avoid ambiguity
                val recognizer = Recognizer(model, window.sampleRateHz.toFloat())
                try {
                    recognizer.acceptWaveForm(pcm, pcm.size)
                    extractText(recognizer.finalResult)
                } catch (e: Exception) {
                    Log.w(TAG, "Vosk transcription failed", e)
                    null
                } finally {
                    recognizer.close()
                }
            }
        }
    }

    private fun createModel(): Model {
        val path = assetCache.ensureDirectoryCopied(MODEL_DIR)
        Log.i(TAG, "Initializing Vosk model from $path")
        return Model(path)
    }

    private fun extractText(resultJson: String?): String? {
        val raw = resultJson?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            Json.parseToJsonElement(raw)
                .jsonObject["text"]
                ?.jsonPrimitive
                ?.content
                ?.trim()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
