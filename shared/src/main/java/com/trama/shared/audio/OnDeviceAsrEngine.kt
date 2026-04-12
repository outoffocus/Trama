package com.trama.shared.audio

interface OnDeviceAsrEngine {
    val name: String
    val isAvailable: Boolean
        get() = true

    suspend fun transcribe(window: CapturedAudioWindow, languageTag: String = "es"): AsrTranscript?
}
