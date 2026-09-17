package com.trama.app.audio

import com.trama.shared.model.SpeakerTurn

data class DiarizationSpan(
    val startMs: Long,
    val endMs: Long,
    val speaker: Int
)

object SpeakerTurnAligner {
    fun align(
        chunks: List<TranscribedAudioChunk>,
        spans: List<DiarizationSpan>
    ): List<SpeakerTurn> {
        if (chunks.isEmpty() || spans.isEmpty()) return emptyList()
        val turns = chunks.flatMap { chunk -> alignChunk(chunk, spans) }

        return turns.fold(mutableListOf()) { merged, turn ->
            val previous = merged.lastOrNull()
            if (previous != null && previous.speaker == turn.speaker && turn.startMs - previous.endMs <= 1_000L) {
                merged[merged.lastIndex] = previous.copy(
                    endMs = turn.endMs,
                    text = "${previous.text} ${turn.text}".replace(Regex("\\s+"), " ").trim()
                )
            } else {
                merged += turn
            }
            merged
        }
    }

    /**
     * Whisper returns clean text for a bounded audio block, while Sherpa returns the
     * actual speaker intervals. Distribute words across that interval so a speaker
     * change inside one ASR block is retained instead of assigning all 25 seconds to
     * whichever person spoke longest.
     */
    private fun alignChunk(
        chunk: TranscribedAudioChunk,
        allSpans: List<DiarizationSpan>
    ): List<SpeakerTurn> {
        val words = chunk.text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val relevant = allSpans.filter { it.endMs > chunk.startMs && it.startMs < chunk.endMs }
        if (relevant.isEmpty()) return listOf(SpeakerTurn(0, chunk.startMs, chunk.endMs, chunk.text.trim()))
        val duration = (chunk.endMs - chunk.startMs).coerceAtLeast(1L)
        val wordTurns = words.mapIndexed { index, word ->
            val start = chunk.startMs + duration * index / words.size
            val end = chunk.startMs + duration * (index + 1) / words.size
            val midpoint = start + (end - start) / 2
            val speaker = relevant
                .filter { midpoint in it.startMs until it.endMs }
                .minByOrNull { kotlin.math.abs((it.startMs + it.endMs) / 2 - midpoint) }
                ?.speaker
                ?: relevant.minByOrNull {
                    minOf(kotlin.math.abs(midpoint - it.startMs), kotlin.math.abs(midpoint - it.endMs))
                }?.speaker
                ?: 0
            SpeakerTurn(speaker, start, end, word)
        }
        return wordTurns.fold(mutableListOf()) { merged, word ->
            val previous = merged.lastOrNull()
            if (previous != null && previous.speaker == word.speaker) {
                merged[merged.lastIndex] = previous.copy(
                    endMs = word.endMs,
                    text = "${previous.text} ${word.text}"
                )
            } else {
                merged += word
            }
            merged
        }
    }
}
