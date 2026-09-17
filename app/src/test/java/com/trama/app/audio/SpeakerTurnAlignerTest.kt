package com.trama.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerTurnAlignerTest {
    @Test
    fun assignsEachTranscriptChunkToSpeakerWithMostOverlap() {
        val chunks = listOf(
            TranscribedAudioChunk(0, 10_000, "Buenos días"),
            TranscribedAudioChunk(10_000, 20_000, "Vamos con el informe")
        )
        val spans = listOf(
            DiarizationSpan(0, 8_000, 0),
            DiarizationSpan(8_000, 20_000, 1)
        )

        val turns = SpeakerTurnAligner.align(chunks, spans)

        assertEquals(listOf(0, 1), turns.map { it.speaker })
        assertEquals("Buenos días", turns.first().text)
        assertEquals("Vamos con el informe", turns.last().text)
    }

    @Test
    fun mergesConsecutiveChunksFromSameSpeaker() {
        val chunks = listOf(
            TranscribedAudioChunk(0, 5_000, "Primera frase."),
            TranscribedAudioChunk(5_000, 10_000, "Segunda frase.")
        )
        val spans = listOf(DiarizationSpan(0, 10_000, 2))

        val turns = SpeakerTurnAligner.align(chunks, spans)

        assertEquals(1, turns.size)
        assertEquals(2, turns.single().speaker)
        assertEquals("Primera frase. Segunda frase.", turns.single().text)
    }

    @Test
    fun preservesSpeakerChangeInsideOneAsrChunk() {
        val chunks = listOf(
            TranscribedAudioChunk(0, 10_000, "uno dos tres cuatro cinco seis")
        )
        val spans = listOf(
            DiarizationSpan(0, 5_000, 0),
            DiarizationSpan(5_000, 10_000, 1)
        )

        val turns = SpeakerTurnAligner.align(chunks, spans)

        assertEquals(listOf(0, 1), turns.map { it.speaker })
        assertEquals("uno dos tres", turns[0].text)
        assertEquals("cuatro cinco seis", turns[1].text)
    }
}
