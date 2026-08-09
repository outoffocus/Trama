package com.trama.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureGateResultPolicyTest {
    @Test
    fun `late result from previous capture is rejected`() {
        assertFalse(CaptureGateResultPolicy.belongsToActiveCapture(42L, 41L))
        assertTrue(CaptureGateResultPolicy.belongsToActiveCapture(42L, 42L))
    }

    @Test
    fun `trigger position uses evaluated snapshot instead of later read position`() {
        val evaluatedThrough = 32_000
        val readLoopPositionWhenResultReturns = 64_000

        val triggerAt = CaptureGateResultPolicy.firstTriggerAtSample(
            currentValue = -1,
            matched = true,
            coveredThroughSample = evaluatedThrough
        )

        assertEquals(evaluatedThrough, triggerAt)
        assertFalse(triggerAt == readLoopPositionWhenResultReturns)
    }

    @Test
    fun `first trigger position cannot be overwritten by a later result`() {
        assertEquals(
            32_000,
            CaptureGateResultPolicy.firstTriggerAtSample(
                currentValue = 32_000,
                matched = true,
                coveredThroughSample = 48_000
            )
        )
    }
}
