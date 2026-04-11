package com.trama.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class WatchAudioSyncMetadata(
    val createdAt: Long,
    val durationSeconds: Int,
    val sampleRateHz: Int,
    val source: String,
    val kind: String,
    val triggerText: String? = null,
    val intentId: String? = null,
    val label: String? = null
)
