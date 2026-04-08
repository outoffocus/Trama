package com.trama.app.audio

interface LightweightGateAsr {
    val name: String
    val isAvailable: Boolean

    suspend fun transcribe(window: CapturedAudioWindow, languageTag: String = "es"): String?
}
