package com.trama.app.audio

import com.trama.shared.speech.IntentPattern

/** Decides whether a final gate match contains only the wake phrase. */
object TriggerFollowUpPolicy {
    fun needsFollowUp(transcript: String, matchedTrigger: String?): Boolean {
        val trigger = matchedTrigger
            ?.let(IntentPattern::normalizeTrigger)
            ?.takeIf(String::isNotBlank)
            ?: return false
        val normalized = IntentPattern.normalizeTrigger(transcript)
        val triggerStart = normalized.indexOf(trigger)
        if (triggerStart < 0) return false
        return normalized
            .substring(triggerStart + trigger.length)
            .trim()
            .isBlank()
    }
}
