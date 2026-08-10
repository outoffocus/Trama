package com.trama.app.ambient

import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventSource
import com.trama.shared.model.TimelineEventType
import com.trama.shared.util.DayRange
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sqrt

class AmbientContextRecorder(
    private val repository: DiaryRepository
) {
    sealed interface Result {
        data class Recorded(val eventId: Long, val merged: Boolean) : Result
        data class Suppressed(val reason: String) : Result
    }

    suspend fun record(
        signal: AmbientContextSignal,
        config: AmbientContextConfig,
        nowMs: Long,
        windowMs: Long,
        deviceMediaPlaying: Boolean
    ): Result {
        if (signal.confidence < MIN_CONFIDENCE) return Result.Suppressed("low_confidence")

        val currentPlace = resolveCurrentPlace()
        val policy = AmbientContextPolicy.evaluate(
            config = config,
            hourOfDay = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY),
            place = currentPlace.kind,
            deviceMediaPlaying = deviceMediaPlaying
        )
        if (policy is AmbientContextPolicy.Decision.Suppress) {
            return Result.Suppressed(policy.reason)
        }

        val day = DayRange.of(nowMs)
        val todayEvents = repository.getTimelineEventsByDateRangeOnce(day.startMs, day.endInclusiveMs)
            .filter { it.type == TimelineEventType.AMBIENT_CONTEXT }
        val latest = repository.getLatestTimelineEventByType(TimelineEventType.AMBIENT_CONTEXT)
            ?.takeIf { it.timestamp in day.startMs until day.endExclusiveMs }
        return when (
            val aggregation = AmbientContextAggregation.decide(
                latest = latest,
                category = signal.category,
                nowMs = nowMs,
                blocksToday = todayEvents.size
            )
        ) {
            AmbientContextAggregation.Decision.Insert -> {
                val start = (nowMs - windowMs.coerceAtLeast(0L)).coerceAtLeast(day.startMs)
                val id = repository.insertTimelineEvent(
                    TimelineEvent(
                        type = TimelineEventType.AMBIENT_CONTEXT,
                        timestamp = start,
                        endTimestamp = nowMs,
                        title = signal.category.title,
                        subtitle = "Procesado en el dispositivo · sin transcripción guardada",
                        dataJson = AmbientContextAggregation.dataJson(signal.category, samples = 1),
                        placeId = currentPlace.placeId,
                        source = TimelineEventSource.AMBIENT_LOCAL
                    )
                )
                Result.Recorded(id, merged = false)
            }
            is AmbientContextAggregation.Decision.Merge -> {
                val existing = aggregation.event
                val samples = AmbientContextAggregation.samplesFromData(existing.dataJson) + 1
                repository.updateTimelineEvent(
                    existing.copy(
                        endTimestamp = maxOf(existing.endTimestamp ?: existing.timestamp, nowMs),
                        dataJson = AmbientContextAggregation.dataJson(signal.category, samples),
                        placeId = existing.placeId ?: currentPlace.placeId
                    )
                )
                Result.Recorded(existing.id, merged = true)
            }
            is AmbientContextAggregation.Decision.Suppress -> Result.Suppressed(aggregation.reason)
        }
    }

    private suspend fun resolveCurrentPlace(): CurrentPlace {
        val dwell = repository.getDwellDetectionState()
        if (dwell?.active != true) return CurrentPlace()
        val lat = dwell.anchorLat ?: return CurrentPlace()
        val lon = dwell.anchorLon ?: return CurrentPlace()
        val deltaLat = PLACE_RADIUS_METERS / 111_320.0
        val deltaLon = PLACE_RADIUS_METERS /
            (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.1))
        val place = repository.findPlacesInBoundingBox(
            minLat = lat - deltaLat,
            maxLat = lat + deltaLat,
            minLon = lon - deltaLon,
            maxLon = lon + deltaLon
        ).map { candidate ->
            val dy = (candidate.latitude - lat) * 111_320.0
            val dx = (candidate.longitude - lon) * 111_320.0 * cos(Math.toRadians(lat))
            candidate to sqrt(dx * dx + dy * dy)
        }.minByOrNull { (_, distance) -> distance }
            ?.takeIf { (_, distance) -> distance <= PLACE_RADIUS_METERS }
            ?.first
            ?: return CurrentPlace()
        val kind = when {
            place.isHome -> AmbientPlaceKind.HOME
            place.isWork -> AmbientPlaceKind.WORK
            else -> AmbientPlaceKind.OTHER
        }
        return CurrentPlace(place.id, kind)
    }

    private data class CurrentPlace(
        val placeId: Long? = null,
        val kind: AmbientPlaceKind = AmbientPlaceKind.UNKNOWN
    )

    private companion object {
        const val MIN_CONFIDENCE = 0.82f
        const val PLACE_RADIUS_METERS = 100.0
    }
}
