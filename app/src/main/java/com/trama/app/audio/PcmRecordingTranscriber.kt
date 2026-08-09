package com.trama.app.audio

import com.trama.app.diagnostics.CaptureLog
import com.trama.app.summary.AsrHallucinationDetector
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

class PcmRecordingTranscriber(
    private val asrEngine: SherpaWhisperAsrEngine
) {
    data class Result(
        val text: String,
        val totalDecodeMs: Long,
        val chunkCount: Int,
        val acceptedChunks: Int,
        val rejectedChunks: Int,
        val rejectReason: String?
    )

    companion object {
        const val CHUNK_MS = 25_000L
        private const val MIN_CHUNK_MS = 700L
    }

    suspend fun transcribe(
        file: File,
        sampleRateHz: Int = PcmRecordingStorage.SAMPLE_RATE_HZ,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Result {
        val bytesPerChunk = (sampleRateHz *
            PcmRecordingStorage.BYTES_PER_SAMPLE * CHUNK_MS / 1000L).coerceAtLeast(1L)
        val expectedChunks = ceil(file.length().toDouble() / bytesPerChunk).toInt().coerceAtLeast(1)
        var totalDecodeMs = 0L
        var acceptedChunks = 0
        var rejectedChunks = 0
        var visitedChunks = 0
        val acceptedText = mutableListOf<String>()
        val rejectReasons = mutableListOf<String>()

        PcmRecordingStorage.readWindows(file, CHUNK_MS, sampleRateHz).forEachIndexed { index, chunk ->
            if (chunk.durationMs() < MIN_CHUNK_MS && index > 0) return@forEachIndexed
            visitedChunks += 1
            onProgress(visitedChunks, expectedChunks)
            val pcm = chunk.livePcm
            val stats = audioStats(pcm)
            val startedAt = System.currentTimeMillis()
            val text = asrEngine.transcribe(chunk, languageTag = "es")?.text?.trim().orEmpty()
            val decodeMs = System.currentTimeMillis() - startedAt
            totalDecodeMs += decodeMs
            val hallucinationReason = AsrHallucinationDetector.detect(
                text,
                singleWordIsHallucination = false
            )
            val rejectReason = when {
                text.isBlank() -> "blank"
                hallucinationReason != null -> hallucinationReason
                else -> null
            }
            if (rejectReason == null) {
                acceptedChunks += 1
                acceptedText += text
            } else {
                rejectedChunks += 1
                rejectReasons += rejectReason
            }
            CaptureLog.event(
                gate = CaptureLog.Gate.RECORDING,
                result = if (rejectReason == null) CaptureLog.Result.OK else CaptureLog.Result.NO_MATCH,
                text = if (rejectReason == null) text else "offline_asr_chunk_rejected",
                meta = mapOf(
                    "engine" to asrEngine.name,
                    "chunkIndex" to index,
                    "chunkCount" to expectedChunks,
                    "windowMs" to chunk.durationMs(),
                    "decodeMs" to decodeMs,
                    "transcriptPreview" to text.take(120),
                    "rejectReason" to (rejectReason ?: ""),
                    "rms" to "%.1f".format(stats.rms),
                    "peak" to stats.peak,
                    "nonZeroRatio" to "%.3f".format(stats.nonZeroRatio)
                )
            )
        }

        val text = acceptedText.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        return Result(
            text = text,
            totalDecodeMs = totalDecodeMs,
            chunkCount = visitedChunks,
            acceptedChunks = acceptedChunks,
            rejectedChunks = rejectedChunks,
            rejectReason = if (text.isBlank()) {
                rejectReasons.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                    ?: "no_chunks"
            } else {
                null
            }
        )
    }

    private data class AudioStats(val rms: Double, val peak: Int, val nonZeroRatio: Double)

    private fun audioStats(pcm: ShortArray): AudioStats {
        if (pcm.isEmpty()) return AudioStats(0.0, 0, 0.0)
        var sumSquares = 0.0
        var peak = 0
        var nonZero = 0
        pcm.forEach { sample ->
            val value = sample.toInt()
            peak = maxOf(peak, abs(value))
            if (value != 0) nonZero += 1
            sumSquares += value.toDouble() * value.toDouble()
        }
        return AudioStats(
            rms = sqrt(sumSquares / pcm.size),
            peak = peak,
            nonZeroRatio = nonZero.toDouble() / pcm.size
        )
    }
}
