package com.trama.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualCapturePolicyTest {

    @Test
    fun `continuous unmatched speech rotates at the configured cap`() {
        val capSeconds = (ContextualCapturePolicy.UNMATCHED_SEGMENT_CAP_MS / 1000L).toInt()
        assertTrue(
            ContextualCapturePolicy.shouldRotateUnmatchedSegment(
                triggerMatched = false,
                capturedSamples = 16_000 * capSeconds,
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
        val justBelowCapSamples =
            ((ContextualCapturePolicy.UNMATCHED_SEGMENT_CAP_MS - 1_000L) * 16_000L / 1000L).toInt()
        assertFalse(
            ContextualCapturePolicy.shouldRotateUnmatchedSegment(
                triggerMatched = false,
                capturedSamples = justBelowCapSamples,
                sampleRateHz = 16_000
            )
        )
    }
}
