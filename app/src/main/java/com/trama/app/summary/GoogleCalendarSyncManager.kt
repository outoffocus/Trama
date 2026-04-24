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
        if (selectedIds.isEmpty()) return

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

        val events = CalendarHelper.getEventsForRange(
            context = appContext,
            startMillis = todayStart,
            endMillis = rangeEnd,
            calendarIds = selectedIds
        )

        val calendarLabels = googleCalendars.associateBy({ it.id }, { it.label })
        val importedFutureEvents = repository.getTimelineEventsByDateRangeOnce(todayStart, rangeEnd)
            .filter {
                it.type == TimelineEventType.CALENDAR &&
                    it.source == TimelineEventSource.CALENDAR_IMPORT
            }
        val importedByPayload = importedFutureEvents.associateBy { it.dataJson.orEmpty() }
        val seenPayloads = mutableSetOf<String>()

        events.forEach { event ->
            val payload = buildPayload(event)
            seenPayloads += payload

            val subtitle = buildSubtitle(
                calendarLabel = calendarLabels[event.calendarId],
                location = event.location,
                description = event.description
            )

            val existing = importedByPayload[payload]
                ?: repository.getTimelineEventByTypeSourceAndDataJson(
                    type = TimelineEventType.CALENDAR,
                    source = TimelineEventSource.CALENDAR_IMPORT,
                    dataJson = payload
                )

            if (existing == null) {
                repository.insertTimelineEvent(
                    TimelineEvent(
                        type = TimelineEventType.CALENDAR,
                        timestamp = event.startMillis,
                        endTimestamp = event.endMillis,
                        title = event.title,
                        subtitle = subtitle,
                        dataJson = payload,
                        source = TimelineEventSource.CALENDAR_IMPORT
                    )
                )
            } else {
                val updated = existing.copy(
                    timestamp = event.startMillis,
                    endTimestamp = event.endMillis,
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
                calendarId in selectedIds && payload !in seenPayloads
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

    private fun buildPayload(event: CalendarHelper.CalendarEvent): String {
        return JSONObject()
            .put("provider", "google_calendar")
            .put("calendarId", event.calendarId)
            .put("eventId", event.id)
            .put("startMillis", event.startMillis)
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
