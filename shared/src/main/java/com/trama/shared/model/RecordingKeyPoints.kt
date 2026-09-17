package com.trama.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stable storage codec with support for the newline format used by older builds. */
object RecordingKeyPoints {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(points: List<String>): String? {
        val normalized = points.map { it.trim() }.filter { it.isNotEmpty() }
        return normalized.takeIf { it.isNotEmpty() }?.let(json::encodeToString)
    }

    fun decode(stored: String?): List<String> {
        val value = stored?.trim().orEmpty()
        if (value.isEmpty()) return emptyList()
        val decoded = runCatching { json.decodeFromString<List<String>>(value) }.getOrNull()
        return (decoded ?: value.lineSequence().toList())
            .map { it.trim().removePrefix("- ").removePrefix("• ").trim() }
            .filter { it.isNotEmpty() }
    }
}
