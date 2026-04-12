package com.trama.shared.audio

object NoOpLightweightGateAsr : LightweightGateAsr {
    override val name: String = "noop-gate"
    override val isAvailable: Boolean = false
    override suspend fun transcribe(window: CapturedAudioWindow, languageTag: String): String? = null
}
