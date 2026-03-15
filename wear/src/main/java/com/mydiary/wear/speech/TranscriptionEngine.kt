package com.mydiary.wear.speech

import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

class TranscriptionEngine(private val model: Model) {

    private val TAG = "TranscriptionEngine"
    private var recognizer = Recognizer(model, 16000f)

    data class TranscriptionResult(val text: String, val confidence: Float)

    fun acceptWaveForm(data: ByteArray, length: Int): String? {
        if (recognizer.acceptWaveForm(data, length)) {
            return parseText(recognizer.result)
        }
        return parsePartialText(recognizer.partialResult)
    }

    fun finalResult(): TranscriptionResult {
        val json = recognizer.finalResult
        return parseFinalResult(json)
    }

    private fun parseText(json: String): String? {
        return try {
            JSONObject(json).optString("text", "").trim().ifEmpty { null }
        } catch (e: Exception) { null }
    }

    private fun parsePartialText(json: String): String? {
        return try {
            JSONObject(json).optString("partial", "").trim().ifEmpty { null }
        } catch (e: Exception) { null }
    }

    private fun parseFinalResult(json: String): TranscriptionResult {
        return try {
            val obj = JSONObject(json)
            val text = obj.optString("text", "").trim()
            val resultArray = obj.optJSONArray("result")
            val confidence = if (resultArray != null && resultArray.length() > 0) {
                var total = 0f
                for (i in 0 until resultArray.length()) {
                    total += resultArray.getJSONObject(i).optDouble("conf", 0.0).toFloat()
                }
                total / resultArray.length()
            } else 0f
            TranscriptionResult(text, confidence)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing final result", e)
            TranscriptionResult("", 0f)
        }
    }

    fun reset() {
        recognizer.close()
        recognizer = Recognizer(model, 16000f)
    }

    fun close() { recognizer.close() }
}
