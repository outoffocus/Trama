package com.trama.app.service

internal object UncertainGateFallbackPolicy {
    const val MIN_BATTERY_PCT = 20
    const val CHARGING_COOLDOWN_MS = 2L * 60L * 1000L

    data class Decision(
        val allowed: Boolean,
        val blockedReason: String?,
        val cooldownMs: Long
    )

    fun decide(
        windowMs: Long,
        gateTranscript: String,
        nowMs: Long,
        lastAllowedAtMs: Long,
        batteryPct: Int,
        charging: Boolean,
        normalCooldownMs: Long,
        minWindowMs: Long,
        maxWindowMs: Long
    ): Decision {
        val words = gateTranscript.split("\\s+".toRegex()).count { it.isNotBlank() }
        val gateLooksUnreliable = gateTranscript.isBlank() || words <= 2
        val windowLooksUseful = windowMs in minWindowMs..maxWindowMs
        val cooldownMs = if (charging) CHARGING_COOLDOWN_MS else normalCooldownMs
        val blockedReason = when {
            !gateLooksUnreliable -> "gate_transcript_not_uncertain"
            !windowLooksUseful -> "window_not_useful"
            !charging && batteryPct in 1 until MIN_BATTERY_PCT -> "battery_low"
            nowMs - lastAllowedAtMs < cooldownMs -> "cooldown"
            else -> null
        }
        return Decision(
            allowed = blockedReason == null,
            blockedReason = blockedReason,
            cooldownMs = cooldownMs
        )
    }
}
