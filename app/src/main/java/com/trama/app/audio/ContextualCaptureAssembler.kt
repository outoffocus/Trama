package com.trama.app.audio

/**
 * Collects a pre-roll snapshot from the ring buffer plus a bounded post-roll window.
 *
 * This class is intentionally engine-agnostic: capture and ASR are decoupled so we can
 * wire a dedicated backend later without rewriting the app-facing logic.
 */
class ContextualCaptureAssembler(
    private val config: ContextualCaptureConfig
) {
    private var postRoll = ShortArray(0)

    fun beginCapture(buffer: CircularAudioBuffer): CapturedAudioWindow {
        postRoll = shortArrayOf()
        return CapturedAudioWindow(
            preRollPcm = buffer.snapshotLast(config.preRollSeconds, config.sampleRateHz),
            livePcm = shortArrayOf(),
            sampleRateHz = config.sampleRateHz
        )
    }

    fun appendPostRoll(chunk: ShortArray) {
        if (chunk.isEmpty()) return
        val maxSamples = config.postRollSeconds * config.sampleRateHz
        val current = postRoll.size
        if (current >= maxSamples) return

        val accepted = chunk.copyOf(minOf(chunk.size, maxSamples - current))
        val merged = ShortArray(current + accepted.size)
        postRoll.copyInto(merged, 0)
        accepted.copyInto(merged, current)
        postRoll = merged
    }

    fun finalizeWindow(preRollWindow: CapturedAudioWindow): CapturedAudioWindow =
        preRollWindow.copy(livePcm = postRoll)
}
