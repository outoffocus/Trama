package com.trama.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureMetricsTest {
    @Test
    fun `aggregates pipeline counts and likely noise rate`() {
        val events = listOf(
            event(0, CaptureLog.Gate.ASR_GATE, CaptureLog.Result.OK),
            event(100, CaptureLog.Gate.ASR_GATE, CaptureLog.Result.NO_MATCH, text = "gate_eval_skipped", meta = mapOf("reason" to "ambient_backoff")),
            event(200, CaptureLog.Gate.ASR_GATE, CaptureLog.Result.OK, meta = mapOf("reason" to "uncertain_gate_fallback")),
            event(1_000, CaptureLog.Gate.ASR_FINAL, CaptureLog.Result.OK),
            event(2_000, CaptureLog.Gate.INTENT, CaptureLog.Result.OK, meta = mapOf("trigger" to "tengo que comprar")),
            event(3_000, CaptureLog.Gate.SAVE, CaptureLog.Result.OK),
            event(
                3_600_000,
                CaptureLog.Gate.USER_DELETE,
                CaptureLog.Result.OK,
                meta = mapOf("likelyNoise" to "false", "reason" to "noise")
            )
        )

        val metrics = CaptureMetrics.from(events)

        assertEquals(1.0, metrics.observationHours, 0.001)
        assertEquals(1, metrics.whisperTranscriptions)
        assertEquals(1, metrics.gateEvaluations)
        assertEquals(1, metrics.savedEntries)
        assertEquals(1.0, metrics.suspiciousCapturesPerHour, 0.001)
        assertEquals("tengo que comprar" to 1, metrics.topTriggers.single())
    }

    private fun event(
        ts: Long,
        gate: CaptureLog.Gate,
        result: CaptureLog.Result,
        text: String? = null,
        meta: Map<String, String> = emptyMap()
    ) = CaptureLog.Event(ts, gate.name, result.name, text = text, meta = meta)
}
