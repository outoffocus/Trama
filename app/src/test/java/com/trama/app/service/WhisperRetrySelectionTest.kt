package com.trama.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperRetrySelectionTest {
    @Test
    fun `focused whisper retry wins when contextual pass loses the intent`() {
        assertEquals(
            "recordarme comprar una camisa mañana",
            chooseWhisperTranscript(
                primaryText = "recuerda darme un trago de camisa mañana",
                primaryIntentConfidence = null,
                retryText = "recordarme comprar una camisa mañana",
                retryIntentConfidence = 0.86f
            )
        )
    }

    @Test
    fun `contextual whisper pass remains preferred when both are credible`() {
        assertEquals(
            "tengo que comprar una camisa mañana",
            chooseWhisperTranscript(
                primaryText = "tengo que comprar una camisa mañana",
                primaryIntentConfidence = 0.88f,
                retryText = "tengo que comprar una camisa",
                retryIntentConfidence = 0.88f
            )
        )
    }
}
