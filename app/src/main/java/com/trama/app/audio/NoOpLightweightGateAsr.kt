package com.trama.app.audio

object NoOpLightweightGateAsr : LightweightGateAsr {
    override val name: String = "gate-asr:none"
    override val isAvailable: Boolean = false

    override suspend fun transcribe(window: CapturedAudioWindow, languageTag: String): String? = null
}
