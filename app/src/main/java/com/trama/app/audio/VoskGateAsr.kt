package com.trama.app.audio

import android.content.Context
import android.util.Log
import com.trama.app.speech.PersonalDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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
    private val personalDictionary = PersonalDictionary(appContext)
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
                val grammar = buildGrammarJson()
                val recognizer = if (grammar != null) {
                    Recognizer(model, window.sampleRateHz.toFloat(), grammar)
                } else {
                    Recognizer(model, window.sampleRateHz.toFloat())
                }
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

    private suspend fun buildGrammarJson(): String? {
        val corrections = personalDictionary.corrections.first()
            .sortedByDescending { it.count }

        if (corrections.isEmpty()) return null

        val phrases = linkedSetOf<String>()
        corrections.forEach { correction ->
            val correct = correction.correct.trim().lowercase()
            if (correct.isNotBlank()) {
                phrases += correct
            }
        }

        if (phrases.isEmpty()) return null

        val limited = phrases.take(120)
        return buildJsonArray {
            limited.forEach { add(JsonPrimitive(it)) }
            add(JsonPrimitive("[unk]"))
        }.toString()
    }
}
