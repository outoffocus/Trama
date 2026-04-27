package com.trama.app.summary

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DailyInsights(
    val totalTrackedMinutes: Long = 0,
    val firstPlaceName: String? = null,
    val firstPlaceArrival: Long? = null,
    val lastPlaceName: String? = null,
    val lastPlaceArrival: Long? = null,
    val createdTaskCount: Int = 0,
    val completedTaskCount: Int = 0,
    val postponedTaskCount: Int = 0,
    val openTaskCount: Int = 0,
    val placeDurations: List<PlaceDurationInsight> = emptyList()
)

@Serializable
data class PlaceDurationInsight(
    val placeId: Long? = null,
    val placeName: String,
    val durationMinutes: Long,
    val visitCount: Int
)

object DailyInsightsCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(insights: DailyInsights): String = json.encodeToString(DailyInsights.serializer(), insights)

    fun decode(raw: String?): DailyInsights? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(DailyInsights.serializer(), raw) }.getOrNull()
    }
}
