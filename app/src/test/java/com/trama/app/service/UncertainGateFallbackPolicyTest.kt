package com.trama.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UncertainGateFallbackPolicyTest {

    @Test
    fun `allows uncertain fallback when gate is empty and cooldown elapsed`() {
        val decision = decide(
            gateTranscript = "",
            nowMs = 10 * MINUTE_MS,
            lastAllowedAtMs = 0
        )

        assertTrue(decision.allowed)
        assertEquals(null, decision.blockedReason)
    }

    @Test
    fun `blocks uncertain fallback during normal cooldown`() {
        val decision = decide(
            gateTranscript = "tengo que",
            nowMs = 6 * MINUTE_MS,
            lastAllowedAtMs = 2 * MINUTE_MS
        )

        assertFalse(decision.allowed)
        assertEquals("cooldown", decision.blockedReason)
    }

    @Test
    fun `blocks uncertain fallback below battery threshold when not charging`() {
        val decision = decide(
            gateTranscript = "",
            nowMs = 10 * MINUTE_MS,
            lastAllowedAtMs = 0,
            batteryPct = 19,
            charging = false
        )

        assertFalse(decision.allowed)
        assertEquals("battery_low", decision.blockedReason)
    }

    @Test
    fun `uses shorter cooldown while charging`() {
        val decision = decide(
            gateTranscript = "",
            nowMs = 3 * MINUTE_MS,
            lastAllowedAtMs = 0,
            batteryPct = 10,
            charging = true
        )

        assertTrue(decision.allowed)
        assertEquals(2 * MINUTE_MS, decision.cooldownMs)
    }

    private fun decide(
        gateTranscript: String,
        nowMs: Long,
        lastAllowedAtMs: Long,
        batteryPct: Int = 100,
        charging: Boolean = false
    ) = UncertainGateFallbackPolicy.decide(
        windowMs = 10_000L,
        gateTranscript = gateTranscript,
        nowMs = nowMs,
        lastAllowedAtMs = lastAllowedAtMs,
        batteryPct = batteryPct,
        charging = charging,
        normalCooldownMs = 5 * MINUTE_MS,
        minWindowMs = 2_500L,
        maxWindowMs = 15_000L
    )

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
