package com.mydiary.wear.speech

import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

class KeywordDetector(private val model: Model, private val keywords: List<String>) {

    private val TAG = "KeywordDetector"

    private val grammar = keywords.joinToString("\", \"", "[\"", "\", \"[unk]\"]")
    private var recognizer: Recognizer? = Recognizer(model, 16000f, grammar)

    fun acceptWaveForm(data: ByteArray, length: Int): String? {
        val rec = recognizer ?: return null
        if (rec.acceptWaveForm(data, length)) {
            return parseKeyword(rec.result)
        } else {
            return parsePartialKeyword(rec.partialResult)
        }
    }

    private fun parseKeyword(json: String): String? {
        return try {
            val text = JSONObject(json).optString("text", "").trim()
            if (text.isNotEmpty() && text != "[unk]" && keywords.any { text.contains(it) }) {
                Log.i(TAG, "Keyword detected: $text")
                keywords.first { text.contains(it) }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePartialKeyword(json: String): String? {
        return try {
            val partial = JSONObject(json).optString("partial", "").trim()
            if (partial.isNotEmpty() && partial != "[unk]" && keywords.any { partial.contains(it) }) {
                Log.i(TAG, "Keyword detected in partial: $partial")
                recognizer?.reset()
                keywords.first { partial.contains(it) }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun pause() {
        recognizer?.close()
        recognizer = null
    }

    fun resume() {
        if (recognizer == null) {
            recognizer = Recognizer(model, 16000f, grammar)
        }
    }

    fun reset() {
        recognizer?.close()
        recognizer = Recognizer(model, 16000f, grammar)
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }
}
