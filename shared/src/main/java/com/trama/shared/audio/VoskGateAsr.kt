package com.trama.shared.audio

import android.content.Context
import android.util.Log
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
        // Check that the acoustic model file exists AND has real content.
        // A non-empty directory listing alone is not enough — placeholder 0-byte files
        // are committed to VCS and would make listAssets() return non-empty even when
        // the real model hasn't been downloaded yet.
        get() = assetCache.assetSize("$MODEL_DIR/am/final.mdl") > 0L

    override suspend fun transcribe(window: CapturedAudioWindow, languageTag: String): String? {
        val pcm = window.mergedPcm()
        if (pcm.isEmpty() || !isAvailable) return null

        return withContext(Dispatchers.IO) {
            recognizerMutex.withLock {
                val model = model ?: createModel().also { model = it }
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
