package com.trama.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SpeakerTurn(
    val speaker: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
) {
    val label: String
        get() = "Interlocutor ${speaker + 1}"
}

object SpeakerTurns {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(turns: List<SpeakerTurn>): String? =
        turns.takeIf { it.isNotEmpty() }?.let(json::encodeToString)

    fun decode(value: String?): List<SpeakerTurn> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<SpeakerTurn>>(value) }
            .getOrDefault(emptyList())
            .filter { it.speaker >= 0 && it.startMs >= 0 && it.endMs > it.startMs && it.text.isNotBlank() }
            .sortedBy { it.startMs }
    }
}
