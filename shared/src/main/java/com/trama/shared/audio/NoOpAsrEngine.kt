package com.trama.shared.audio

class NoOpAsrEngine : OnDeviceAsrEngine {
    override val name: String = "noop"
    override val isAvailable: Boolean = false
    override suspend fun transcribe(window: CapturedAudioWindow, languageTag: String): AsrTranscript? = null
}
