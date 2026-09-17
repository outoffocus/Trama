package com.trama.app.summary

import android.content.Context
import android.util.Log
import com.trama.app.ui.SettingsDataStore
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventSource
import com.trama.shared.model.TimelineEventType
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

class GoogleCalendarSyncManager(context: Context) {

    private val appContext = context.applicationContext
    private val repository = DatabaseProvider.getRepository(appContext)
    private val settings = SettingsDataStore(appContext)

    suspend fun syncSelectedCalendars() {
        if (!CalendarHelper.hasCalendarPermission(appContext)) return

        val googleCalendars = CalendarHelper.getReadableCalendars(appContext)
            .filter { it.accountType == "com.google" }
        if (googleCalendars.isEmpty()) return

        val preferredIds = settings.visibleCalendarIds.first()
        val selectedIds = (preferredIds ?: googleCalendars.map { it.id }.toSet())
            .intersect(googleCalendars.map { it.id }.toSet())

        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val rangeEnd = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, 60)
        }.timeInMillis

        val events = if (selectedIds.isEmpty()) emptyList() else CalendarHelper.getEventsForRangeResult(
            context = appContext,
            startMillis = todayStart,
            endMillis = rangeEnd,
            calendarIds = selectedIds
        ).getOrElse { error ->
            Log.w(TAG, "Calendar read failed; keeping imported events", error)
            return
        }

        repository.withTransaction {
            val calendarLabels = googleCalendars.associateBy({ it.id }, { it.displayName })
            val importedFutureEvents = repository.getCalendarEventsOverlapping(todayStart, rangeEnd).first()
                .filter {
                    it.type == TimelineEventType.CALENDAR &&
                        it.source == TimelineEventSource.CALENDAR_IMPORT
                }
            val importedByPayload = importedFutureEvents.associateBy { CalendarImportIdentity.key(it.dataJson) }
            val seenPayloads = mutableSetOf<String>()

            events.forEach { event ->
                val payload = buildPayload(event)
                seenPayloads += requireNotNull(CalendarImportIdentity.key(payload))

                val subtitle = buildSubtitle(
                    calendarLabel = calendarLabels[event.calendarId],
                    location = event.location,
                    description = event.description
                )

                val start = if (event.allDay) CalendarImportIdentity.localAllDay(event.startMillis) else event.startMillis
                val end = if (event.allDay) CalendarImportIdentity.localAllDay(event.endMillis) else event.endMillis
                val existing = importedByPayload[CalendarImportIdentity.key(payload)]
                    ?: repository.getTimelineEventByTypeSourceAndDataJson(
                        type = TimelineEventType.CALENDAR,
                        source = TimelineEventSource.CALENDAR_IMPORT,
                        dataJson = payload
                    )

                if (existing == null) {
                    repository.insertTimelineEvent(
                        TimelineEvent(
                            type = TimelineEventType.CALENDAR,
                            timestamp = start,
                            endTimestamp = end,
                            title = event.title,
                            subtitle = subtitle,
                            dataJson = payload,
                            source = TimelineEventSource.CALENDAR_IMPORT
                        )
                    )
                } else {
                    val updated = existing.copy(
                        timestamp = start,
                        endTimestamp = end,
                        title = event.title,
                        subtitle = subtitle,
                        dataJson = payload
                    )
                    if (updated != existing) {
                        repository.updateTimelineEvent(updated)
                    }
                }
            }

            val staleIds = importedFutureEvents
                .filter { event ->
                    val payload = event.dataJson ?: return@filter false
                    val calendarId = parseCalendarId(payload)
                    calendarId != null && (calendarId !in selectedIds || CalendarImportIdentity.key(payload) !in seenPayloads)
                }
                .map { it.id }
            if (staleIds.isNotEmpty()) {
                repository.deleteTimelineEventsByIds(staleIds)
            }

            Log.i(
                TAG,
                "Synced ${events.size} Google Calendar events from ${selectedIds.size} calendars " +
                    "(removed=${staleIds.size})"
            )
        }
    }

    private fun buildPayload(event: CalendarHelper.CalendarEvent): String {
        return JSONObject()
            .put("provider", "google_calendar")
            .put("calendarId", event.calendarId)
            .put("eventId", event.id)
            .put("startMillis", event.startMillis)
            .put("allDay", event.allDay)
            .toString()
    }

    private fun buildSubtitle(
        calendarLabel: String?,
        location: String?,
        description: String?
    ): String? {
        return buildString {
            calendarLabel?.takeIf { it.isNotBlank() }?.let { append(it) }
            location?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
            description?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append("\n")
                append(it.trim().take(220))
            }
        }.ifBlank { null }
    }

    private fun parseCalendarId(payload: String): Long? {
        return try {
            JSONObject(payload).optLong("calendarId")
                .takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "GoogleCalendarSync"
    }
}
