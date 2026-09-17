package com.trama.app.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsrHallucinationDetectorTest {
    @Test
    fun `short capture rejects multi speaker dialogue`() {
        assertEquals(
            "multi_speaker_dialog",
            AsrHallucinationDetector.detect("- ¿Vienes mañana? - Sí, después de comer.")
        )
    }

    @Test
    fun `meeting transcription retains multi speaker dialogue`() {
        assertNull(
            AsrHallucinationDetector.detect(
                "- ¿Vienes mañana? - Sí, después de comer.",
                singleWordIsHallucination = false,
                conversationalSpeechIsHallucination = false
            )
        )
    }

    @Test
    fun `meeting transcription still rejects repeated hallucination`() {
        assertEquals(
            "repetition:no",
            AsrHallucinationDetector.detect(
                "no no no no no no no no no no",
                singleWordIsHallucination = false,
                conversationalSpeechIsHallucination = false
            )
        )
    }
}
