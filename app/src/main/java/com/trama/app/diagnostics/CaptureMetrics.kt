package com.trama.app.diagnostics

import kotlin.math.max

/** Aggregate suitable for local tuning; it never needs raw audio. */
data class CaptureMetrics(
    val observationHours: Double,
    val gateEvaluations: Int,
    val whisperTranscriptions: Int,
    val intentionalTranscriptions: Int,
    val ambientTranscriptions: Int,
    val noIntentTranscriptions: Int,
    val savedEntries: Int,
    val suggestedEntries: Int,
    val deviceMediaPauses: Int,
    val cappedSegments: Int,
    val likelyNoiseDeletes: Int,
    val shadowCandidates: Int,
    val suspiciousCapturesPerHour: Double,
    val topTriggers: List<Pair<String, Int>>
) {
    companion object {
        fun from(events: List<CaptureLog.Event>): CaptureMetrics {
            val ordered = events.sortedBy { it.ts }
            val elapsedHours = if (ordered.size < 2) 0.0 else {
                (ordered.last().ts - ordered.first().ts).toDouble() / 3_600_000.0
            }
            val heartbeatHours = events.count {
                it.gate == CaptureLog.Gate.SERVICE.name && it.text == "heartbeat"
            } * 0.25
            val hours = if (heartbeatHours > 0.0) heartbeatHours else elapsedHours
            val gateEvaluations = events.count { it.gate == CaptureLog.Gate.ASR_GATE.name }
            val whisper = events.count {
                it.gate == CaptureLog.Gate.ASR_FINAL.name && it.result == CaptureLog.Result.OK.name
            }
            val intentionalTranscriptions = events.count {
                it.gate == CaptureLog.Gate.ASR_FINAL.name &&
                    it.result == CaptureLog.Result.OK.name &&
                    it.meta["source"] == "trigger"
            }
            val ambientTranscriptions = events.count {
                it.gate == CaptureLog.Gate.ASR_FINAL.name &&
                    it.result == CaptureLog.Result.OK.name &&
                    it.meta["source"] == "uncertain_fallback"
            }
            val noIntent = events.count {
                it.gate == CaptureLog.Gate.INTENT.name &&
                    it.result == CaptureLog.Result.NO_MATCH.name &&
                    it.meta["shadow"] != "true"
            }
            val saved = events.count {
                it.gate == CaptureLog.Gate.SAVE.name && it.result == CaptureLog.Result.OK.name
            }
            val suggested = events.count {
                it.gate == CaptureLog.Gate.LLM.name && it.meta["route"] == "SUGGESTED"
            }
            val deviceMediaPauses = events.count {
                it.gate == CaptureLog.Gate.SERVICE.name && it.text == "media_playback_pause"
            }
            val cappedSegments = events.count {
                it.gate == CaptureLog.Gate.ASR_GATE.name &&
                    it.text == "segment_finalized" &&
                    it.meta["reason"] == "unmatched_segment_cap"
            }
            val likelyNoise = events.count {
                it.gate == CaptureLog.Gate.USER_DELETE.name && it.meta["likelyNoise"] == "true"
            }
            val shadowCandidates = events.count {
                it.gate == CaptureLog.Gate.INTENT.name && it.meta["shadow"] == "true"
            }
            val topTriggers = events.asSequence()
                .filter { it.gate == CaptureLog.Gate.INTENT.name && it.result == CaptureLog.Result.OK.name }
                .mapNotNull { it.meta["trigger"]?.takeIf(String::isNotBlank) }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(5)
                .map { it.key to it.value }
            return CaptureMetrics(
                observationHours = hours,
                gateEvaluations = gateEvaluations,
                whisperTranscriptions = whisper,
                intentionalTranscriptions = intentionalTranscriptions,
                ambientTranscriptions = ambientTranscriptions,
                noIntentTranscriptions = noIntent,
                savedEntries = saved,
                suggestedEntries = suggested,
                deviceMediaPauses = deviceMediaPauses,
                cappedSegments = cappedSegments,
                likelyNoiseDeletes = likelyNoise,
                shadowCandidates = shadowCandidates,
                suspiciousCapturesPerHour = if (hours <= 0.0) 0.0 else likelyNoise / max(hours, 0.25),
                topTriggers = topTriggers
            )
        }
    }
}
