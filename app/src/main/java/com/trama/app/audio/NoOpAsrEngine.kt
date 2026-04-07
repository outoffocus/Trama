package com.trama.app.audio

/**
 * Placeholder until a dedicated on-device ASR backend is integrated.
 */
class NoOpAsrEngine : OnDeviceAsrEngine {
    override val name: String = "noop"
    override val isAvailable: Boolean = false

    override suspend fun transcribe(window: CapturedAudioWindow, languageTag: String): AsrTranscript? = null
}
