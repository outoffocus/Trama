package com.mydiary.app.speech

import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Single open-vocabulary recognizer that continuously transcribes audio.
 * Detects keywords in the transcription stream and captures text after them.
 */
class SpeechEngine(model: Model, private val keywords: List<String>) {

    private val TAG = "SpeechEngine"
    private var recognizer: Recognizer? = Recognizer(model, 16000f)
    @Volatile
    private var closed = false

    data class CapturedEntry(
        val keyword: String,
        val text: String,
        val confidence: Float
    )

    private var state = State.LISTENING
    private var detectedKeyword: String? = null
    private var recordingStartTime = 0L
    private var recordingDurationMs = 10_000L
    private val collectedTexts = mutableListOf<String>()

    private enum class State { LISTENING, RECORDING }

    fun setRecordingDuration(seconds: Int) {
        recordingDurationMs = seconds * 1000L
    }

    /**
     * Feed audio data. Returns a CapturedEntry when recording finishes, null otherwise.
     */
    fun acceptWaveForm(data: ByteArray, length: Int): CapturedEntry? {
        if (closed) return null
        // Ensure even byte count (16-bit PCM = 2 bytes per sample)
        val safeLength = length and 0xFFFFFFFE.toInt()
        if (safeLength < 2) return checkRecordingTimeout()
        return processChunk(data, safeLength)
    }

    private fun checkRecordingTimeout(): CapturedEntry? {
        if (state == State.RECORDING &&
            System.currentTimeMillis() - recordingStartTime >= recordingDurationMs) {
            return finishRecording()
        }
        return null
    }

    private fun processChunk(data: ByteArray, length: Int): CapturedEntry? {
        val rec = recognizer ?: return null
        val accepted = rec.acceptWaveForm(data, length)

        when (state) {
            State.LISTENING -> {
                if (accepted) {
                    val text = parseText(rec.result)
                    val keyword = text?.let { findKeyword(it) }
                    if (keyword != null) {
                        return startRecording(keyword)
                    }
                } else {
                    val partial = parsePartial(rec.partialResult)
                    val keyword = partial?.let { findKeyword(it) }
                    if (keyword != null) {
                        rec.reset()
                        return startRecording(keyword)
                    }
                }
            }
            State.RECORDING -> {
                if (accepted) {
                    val text = parseText(rec.result)
                    if (!text.isNullOrBlank()) {
                        collectedTexts.add(text)
                    }
                }

                if (System.currentTimeMillis() - recordingStartTime >= recordingDurationMs) {
                    return finishRecording()
                }
            }
        }
        return null
    }

    val isRecording: Boolean get() = state == State.RECORDING

    private fun startRecording(keyword: String): CapturedEntry? {
        Log.i(TAG, "Keyword detected: $keyword — recording started")
        state = State.RECORDING
        detectedKeyword = keyword
        recordingStartTime = System.currentTimeMillis()
        collectedTexts.clear()
        return null
    }

    private fun finishRecording(): CapturedEntry? {
        val rec = recognizer ?: return reset()
        val finalText = parseText(rec.finalResult)
        if (!finalText.isNullOrBlank()) {
            collectedTexts.add(finalText)
        }

        val keyword = detectedKeyword ?: return reset()
        val fullText = collectedTexts.joinToString(" ").trim()

        Log.i(TAG, "Recording finished. Text: '$fullText'")

        state = State.LISTENING
        detectedKeyword = null
        collectedTexts.clear()

        if (fullText.isBlank()) {
            return null
        }

        return CapturedEntry(
            keyword = keyword,
            text = fullText,
            confidence = 0.8f // Vosk small model doesn't give reliable per-word confidence
        )
    }

    private fun reset(): CapturedEntry? {
        state = State.LISTENING
        detectedKeyword = null
        collectedTexts.clear()
        return null
    }

    private fun findKeyword(text: String): String? {
        return keywords.firstOrNull { text.contains(it, ignoreCase = true) }
    }

    private fun parseText(json: String): String? {
        return try {
            JSONObject(json).optString("text", "").trim().ifEmpty { null }
        } catch (e: Exception) { null }
    }

    private fun parsePartial(json: String): String? {
        return try {
            JSONObject(json).optString("partial", "").trim().ifEmpty { null }
        } catch (e: Exception) { null }
    }

    fun close() {
        closed = true
        recognizer?.close()
        recognizer = null
    }
}
