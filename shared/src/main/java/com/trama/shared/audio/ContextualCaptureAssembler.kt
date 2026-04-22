package com.trama.shared.audio

/**
 * Collects a pre-roll snapshot from the ring buffer plus a bounded post-roll window.
 *
 * This class is intentionally engine-agnostic: capture and ASR are decoupled so we can
 * wire a dedicated backend later without rewriting the app-facing logic.
 */
class ContextualCaptureAssembler(
    private val config: ContextualCaptureConfig
) {
    private val postRollChunks = mutableListOf<ShortArray>()
    private var postRollSamples = 0
    private var droppedSamples = 0

    private val maxPostRollSamples: Int =
        config.maxCaptureSeconds.coerceAtLeast(1) * config.sampleRateHz

    /** Number of samples dropped on the current capture due to the safety cap. 0 when healthy. */
    val droppedSampleCount: Int get() = droppedSamples

    fun beginCapture(buffer: CircularAudioBuffer): CapturedAudioWindow {
        postRollChunks.clear()
        postRollSamples = 0
        droppedSamples = 0
        return CapturedAudioWindow(
            preRollPcm = buffer.snapshotLast(config.preRollSeconds, config.sampleRateHz),
            livePcm = shortArrayOf(),
            sampleRateHz = config.sampleRateHz
        )
    }

    fun appendPostRoll(chunk: ShortArray) {
        if (chunk.isEmpty()) return
        if (postRollSamples >= maxPostRollSamples) {
            droppedSamples += chunk.size
            return
        }
        val remaining = maxPostRollSamples - postRollSamples
        val accepted = if (chunk.size <= remaining) chunk else chunk.copyOf(remaining)
        postRollChunks += accepted
        postRollSamples += accepted.size
        if (accepted.size < chunk.size) droppedSamples += chunk.size - accepted.size
    }

    fun finalizeWindow(preRollWindow: CapturedAudioWindow): CapturedAudioWindow =
        preRollWindow.copy(livePcm = mergePostRoll())

    private fun mergePostRoll(): ShortArray {
        if (postRollSamples <= 0) return shortArrayOf()
        if (postRollChunks.size == 1) return postRollChunks.first()

        val merged = ShortArray(postRollSamples)
        var offset = 0
        for (chunk in postRollChunks) {
            chunk.copyInto(merged, destinationOffset = offset)
            offset += chunk.size
        }
        return merged
    }
}
