package com.mydiary.wear.speech

import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Single open-vocabulary recognizer that continuously transcribes audio.
 * Detects keywords in the transcription stream and captures text after them.
 * Stops recording on silence (no speech for SILENCE_TIMEOUT_MS) or max duration.
 */
class SpeechEngine(model: Model, private val keywords: List<String>) {

    private val TAG = "SpeechEngine"
    private var recognizer: Recognizer? = Recognizer(model, 16000f)
    @Volatile
    private var closed = false

    companion object {
        private const val SILENCE_TIMEOUT_MS = 2000L
        private const val MIN_RECORDING_MS = 500L
    }

    data class CapturedEntry(
        val keyword: String,
        val text: String,
        val confidence: Float
    )

    private var state = State.LISTENING
    private var detectedKeyword: String? = null
    private var recordingStartTime = 0L
    private var lastSpeechTime = 0L
    private var recordingDurationMs = 10_000L
    private val collectedTexts = mutableListOf<String>()
    private var hasReceivedSpeech = false

    private enum class State { LISTENING, RECORDING }

    fun setRecordingDuration(seconds: Int) {
        recordingDurationMs = seconds * 1000L
    }

    fun acceptWaveForm(data: ByteArray, length: Int): CapturedEntry? {
        if (closed) return null
        val safeLength = length and 0xFFFFFFFE.toInt()
        if (safeLength < 2) return checkTimeout()
        return processChunk(data, safeLength)
    }

    private fun checkTimeout(): CapturedEntry? {
        if (state != State.RECORDING) return null
        val now = System.currentTimeMillis()
        val elapsed = now - recordingStartTime
        if (elapsed >= recordingDurationMs) return finishRecording()
        if (hasReceivedSpeech && elapsed >= MIN_RECORDING_MS) {
            val silenceDuration = now - lastSpeechTime
            if (silenceDuration >= SILENCE_TIMEOUT_MS) return finishRecording()
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
                val now = System.currentTimeMillis()

                if (accepted) {
                    val text = parseText(rec.result)
                    if (!text.isNullOrBlank()) {
                        collectedTexts.add(text)
                        lastSpeechTime = now
                        hasReceivedSpeech = true
                    }
                } else {
                    val partial = parsePartial(rec.partialResult)
                    if (!partial.isNullOrBlank()) {
                        lastSpeechTime = now
                        hasReceivedSpeech = true
                    }
                }

                val elapsed = now - recordingStartTime
                if (elapsed >= recordingDurationMs) return finishRecording()
                if (hasReceivedSpeech && elapsed >= MIN_RECORDING_MS) {
                    val silenceDuration = now - lastSpeechTime
                    if (silenceDuration >= SILENCE_TIMEOUT_MS) {
                        Log.i(TAG, "Silence detected (${silenceDuration}ms), finishing recording")
                        return finishRecording()
                    }
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
        val now = System.currentTimeMillis()
        recordingStartTime = now
        lastSpeechTime = now
        hasReceivedSpeech = false
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
        hasReceivedSpeech = false

        if (fullText.isBlank()) return null

        return CapturedEntry(
            keyword = keyword,
            text = fullText,
            confidence = 0.8f
        )
    }

    private fun reset(): CapturedEntry? {
        state = State.LISTENING
        detectedKeyword = null
        collectedTexts.clear()
        hasReceivedSpeech = false
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
