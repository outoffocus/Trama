package com.trama.shared.speech

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SimpleVADTest {

    private lateinit var vad: SimpleVAD

    @Before
    fun setUp() {
        vad = SimpleVAD()
    }

    private fun makeSpeechBuffer(amplitude: Short = 500): ShortArray {
        return ShortArray(160) { amplitude }
    }

    private fun makeSilenceBuffer(): ShortArray {
        return ShortArray(160) { 5 }
    }

    @Test
    fun `initial state is not speaking`() {
        assertFalse(vad.isSpeaking)
    }

    @Test
    fun `processFrame skips frames based on FRAME_SKIP`() {
        val buffer = makeSpeechBuffer()
        // First frame should be skipped (frameCounter=1, 1%2!=0)
        val processed1 = vad.processFrame(buffer, buffer.size)
        assertFalse(processed1)
        // Second frame should be processed (frameCounter=2, 2%2==0)
        val processed2 = vad.processFrame(buffer, buffer.size)
        assertTrue(processed2)
    }

    @Test
    fun `reset clears all state`() {
        val buffer = makeSpeechBuffer()
        // Process some frames
        repeat(10) { vad.processFrame(buffer, buffer.size) }
        vad.reset()
        assertFalse(vad.isSpeaking)
    }

    @Test
    fun `onVoiceStart callback fires when speech detected`() {
        var callbackFired = false
        vad.onVoiceStart = { callbackFired = true }

        val buffer = makeSpeechBuffer(amplitude = 1000)
        // Need SPEECH_FRAMES_REQUIRED (2) processed frames of speech
        // With FRAME_SKIP=2, need 4 raw frames to get 2 processed
        repeat(4) { vad.processFrame(buffer, buffer.size) }

        assertTrue(callbackFired)
        assertTrue(vad.isSpeaking)
    }

    @Test
    fun `FRAME_SKIP constant is 2`() {
        assertEquals(2, SimpleVAD.FRAME_SKIP)
    }

    @Test
    fun `INTER_FRAME_DELAY_MS constant is 10`() {
        assertEquals(10L, SimpleVAD.INTER_FRAME_DELAY_MS)
    }

    @Test
    fun `FALLBACK_TIMEOUT_FRAMES constant is 357`() {
        assertEquals(357, SimpleVAD.FALLBACK_TIMEOUT_FRAMES)
    }
}
