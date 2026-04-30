package com.trama.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualCapturePolicyTest {

    @Test
    fun `continuous unmatched speech rotates at 30 seconds`() {
        assertTrue(
            ContextualCapturePolicy.shouldRotateUnmatchedSegment(
                triggerMatched = false,
                capturedSamples = 16_000 * 30,
                sampleRateHz = 16_000
            )
        )
    }

    @Test
    fun `matched trigger does not rotate by unmatched cap`() {
        assertFalse(
            ContextualCapturePolicy.shouldRotateUnmatchedSegment(
                triggerMatched = true,
                capturedSamples = 16_000 * 90,
                sampleRateHz = 16_000
            )
        )
    }

    @Test
    fun `short unmatched speech keeps accumulating`() {
        assertFalse(
            ContextualCapturePolicy.shouldRotateUnmatchedSegment(
                triggerMatched = false,
                capturedSamples = 16_000 * 29,
                sampleRateHz = 16_000
            )
        )
    }
}
