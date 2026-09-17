package com.trama.app.audio

import com.trama.shared.audio.CapturedAudioWindow

/** Keeps only the onset needed by VAD and removes unrelated contextual pre-roll. */
internal fun CapturedAudioWindow.forFinalAsr(
    maxPreRollMs: Long = 500L,
    maxDurationMs: Long = 20_000L
): CapturedAudioWindow {
    if (sampleRateHz <= 0) return this
    val keepPreRollSamples = ((maxPreRollMs * sampleRateHz) / 1_000L)
        .toInt()
        .coerceAtLeast(0)
    val focused = copy(
        preRollPcm = if (preRollPcm.size > keepPreRollSamples) {
            preRollPcm.copyOfRange(preRollPcm.size - keepPreRollSamples, preRollPcm.size)
        } else {
            preRollPcm.copyOf()
        },
        livePcm = livePcm.copyOf()
    )
    return if (focused.durationMs() > maxDurationMs) {
        focused.tailWindow(maxDurationMs)
    } else {
        focused
    }
}
