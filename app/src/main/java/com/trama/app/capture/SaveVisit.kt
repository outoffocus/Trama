package com.trama.app.capture

import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventSource
import com.trama.shared.model.TimelineEventType
import kotlinx.coroutines.flow.first

class SaveVisit(
    private val repository: DiaryRepository,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(eventId: Long?, placeId: Long, start: Long, end: Long): Long {
        validateVisitInterval(start, end, now())
        return repository.withTransaction {
            val place = checkNotNull(getPlaceByIdOnce(placeId)) { "Lugar no encontrado" }
            val old = eventId?.let {
                checkNotNull(getTimelineEventByIdOnce(it)) { "Visita no encontrada" }
            }
            require(old == null || old.type == TimelineEventType.DWELL) { "No es una visita" }
            val visit = old?.copy(
                timestamp = start,
                endTimestamp = end,
                placeId = place.id,
                title = place.name,
                source = TimelineEventSource.MANUAL
            ) ?: TimelineEvent(
                type = TimelineEventType.DWELL,
                timestamp = start,
                endTimestamp = end,
                title = place.name,
                placeId = place.id,
                source = TimelineEventSource.MANUAL
            )
            val id = if (old != null) {
                updateTimelineEvent(visit)
                old.id
            } else {
                insertTimelineEvent(visit)
            }
            setOfNotNull(old?.placeId, place.id).forEach { affectedId ->
                val affected = checkNotNull(getPlaceByIdOnce(affectedId))
                val visits = getTimelineEventsByPlaceId(affectedId)
                    .first()
                    .filter { it.type == TimelineEventType.DWELL }
                updatePlace(
                    affected.copy(
                        visitCount = visits.size,
                        lastVisitAt = visits.maxOfOrNull { it.timestamp }
                    )
                )
            }
            id
        }
    }
}

internal fun validateVisitInterval(start: Long, end: Long, now: Long) {
    require(start <= end) { "La salida debe ser posterior a la llegada" }
    require(end <= now) { "Una visita registrada no puede estar en el futuro" }
}
