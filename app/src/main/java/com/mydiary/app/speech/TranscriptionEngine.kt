package com.mydiary.app.speech

import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

class TranscriptionEngine(private val model: Model) {

    private val TAG = "TranscriptionEngine"

    // Open-vocabulary recognizer for full transcription
    private var recognizer = Recognizer(model, 16000f)

    data class TranscriptionResult(
        val text: String,
        val confidence: Float
    )

    /**
     * Feed audio data. Returns partial transcription text for UI feedback.
     */
    fun acceptWaveForm(data: ByteArray, length: Int): String? {
        if (recognizer.acceptWaveForm(data, length)) {
            return parseText(recognizer.result)
        }
        return parsePartialText(recognizer.partialResult)
    }

    /**
     * Call when recording session ends to get the final transcription.
     */
    fun finalResult(): TranscriptionResult {
        val json = recognizer.finalResult
        return parseFinalResult(json)
    }

    private fun parseText(json: String): String? {
        return try {
            val text = JSONObject(json).optString("text", "").trim()
            text.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing result", e)
            null
        }
    }

    private fun parsePartialText(json: String): String? {
        return try {
            val partial = JSONObject(json).optString("partial", "").trim()
            partial.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseFinalResult(json: String): TranscriptionResult {
        return try {
            val obj = JSONObject(json)
            val text = obj.optString("text", "").trim()

            // Extract average confidence from result array if available
            val resultArray = obj.optJSONArray("result")
            val confidence = if (resultArray != null && resultArray.length() > 0) {
                var totalConf = 0f
                for (i in 0 until resultArray.length()) {
                    totalConf += resultArray.getJSONObject(i).optDouble("conf", 0.0).toFloat()
                }
                totalConf / resultArray.length()
            } else {
                0f
            }

            Log.i(TAG, "Final transcription: '$text' (confidence: $confidence)")
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

    fun close() {
        recognizer.close()
    }
}
