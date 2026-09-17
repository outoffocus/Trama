package com.trama.app.summary

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.database.Cursor
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.trama.shared.model.TimelineEvent
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Reads and writes Google Calendar events via CalendarContract.
 *
 * Used by:
 * - SummaryGenerator: reads tomorrow's events to give LLM context
 * - ActionExecutor: writes new events directly (no intent needed)
 */
object CalendarHelper {

    private const val TAG = "CalendarHelper"
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    data class CalendarEvent(
        val id: Long,
        val calendarId: Long,
        val title: String,
        val description: String?,
        val startMillis: Long,
        val endMillis: Long,
        val location: String?,
        val allDay: Boolean
    ) {
        fun toContextString(): String {
            return if (allDay) {
                "- [Todo el día] \"$title\"" + (location?.let { " en $it" } ?: "")
            } else {
                val start = displayFormat.format(startMillis)
                val end = displayFormat.format(endMillis)
                "- $start–$end \"$title\"" +
                    (location?.let { " en $it" } ?: "") +
                    (description?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
            }
        }
    }

    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWriteCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get events for a specific day (used to give the LLM calendar context).
     * Returns events sorted by start time.
     */
    fun getEventsForDay(context: Context, year: Int, month: Int, day: Int): List<CalendarEvent> {
        if (!hasCalendarPermission(context)) {
            Log.w(TAG, "No READ_CALENDAR permission")
            return emptyList()
        }

        val startCal = Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(year, month, day, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }

        return queryEvents(context, startCal.timeInMillis, endCal.timeInMillis)
    }

    fun getEventsForRange(
        context: Context,
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>? = null
    ): List<CalendarEvent> {
        if (!hasCalendarPermission(context)) {
            Log.w(TAG, "No READ_CALENDAR permission")
            return emptyList()
        }
        return queryEvents(context, startMillis, endMillis, calendarIds)
    }

    /** Only successful, complete reads may be used to remove imported events. */
    fun getEventsForRangeResult(
        context: Context,
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>
    ): Result<List<CalendarEvent>> = runCatching {
        check(hasCalendarPermission(context)) { "Calendar read permission unavailable" }
        queryEventsOrThrow(context, startMillis, endMillis, calendarIds)
    }

    fun openEvent(context: Context, event: CalendarEvent) {
        val eventId = event.id
        val directUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val legacyUri = Uri.parse("content://com.android.calendar/events/$eventId")
        val dayUri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(event.startMillis.toString())
            .build()
        val legacyDayUri = Uri.parse("content://com.android.calendar/time/${event.startMillis}")

        val intents = listOf(
            Intent(Intent.ACTION_VIEW).apply {
                data = legacyDayUri
                `package` = "com.google.android.calendar"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = dayUri
                `package` = "com.google.android.calendar"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = legacyDayUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = dayUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = legacyUri
                `package` = "com.google.android.calendar"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = directUri
                `package` = "com.google.android.calendar"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(legacyUri, "vnd.android.cursor.item/event")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = legacyUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_EDIT).apply {
                data = directUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                `package` = "com.google.android.calendar"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        intents.forEach { intent ->
            val canResolve = intent.resolveActivity(context.packageManager) != null
            if (!canResolve) return@forEach
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch calendar event $eventId with intent=$intent", e)
            }
        }

        Toast.makeText(
            context,
            "No se pudo abrir el evento. Abre Calendario manualmente en esa hora.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun openTimelineEvent(context: Context, event: TimelineEvent) {
        val parsedEventId = event.dataJson?.let { payload ->
            runCatching { JSONObject(payload).optLong("eventId").takeIf { it > 0L } }.getOrNull()
        }
        val parsedCalendarId = event.dataJson?.let { payload ->
            runCatching { JSONObject(payload).optLong("calendarId").takeIf { it > 0L } }.getOrNull()
        }
        openEvent(
            context,
            CalendarEvent(
                id = parsedEventId ?: -1L,
                calendarId = parsedCalendarId ?: -1L,
                title = event.title,
                description = event.subtitle,
                startMillis = event.timestamp,
                endMillis = event.endTimestamp ?: event.timestamp,
                location = null,
                allDay = CalendarImportIdentity.allDay(event.dataJson)
            )
        )
    }

    /**
     * Get events for today and tomorrow (calendar context for the LLM prompt).
     */
    fun getUpcomingEvents(context: Context): Pair<List<CalendarEvent>, List<CalendarEvent>> {
        if (!hasCalendarPermission(context)) {
            return Pair(emptyList(), emptyList())
        }

        val now = Calendar.getInstance()

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }

        val tomorrowStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val tomorrowEnd = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }

        val todayEvents = queryEvents(context, todayStart.timeInMillis, todayEnd.timeInMillis)
        val tomorrowEvents = queryEvents(context, tomorrowStart.timeInMillis, tomorrowEnd.timeInMillis)

        return Pair(todayEvents, tomorrowEvents)
    }

    private fun queryEvents(
        context: Context,
        startMs: Long,
        endMs: Long,
        calendarIds: Set<Long>? = null
    ): List<CalendarEvent> = runCatching {
        queryEventsOrThrow(context, startMs, endMs, calendarIds)
    }.getOrElse {
        Log.e(TAG, "Failed to query calendar events", it)
        emptyList()
    }

    private fun queryEventsOrThrow(
        context: Context,
        startMs: Long,
        endMs: Long,
        calendarIds: Set<Long>? = null
    ): List<CalendarEvent> {
        if (calendarIds != null && calendarIds.isEmpty()) {
            return emptyList()
        }

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY
        )

        // Use Instances table for recurring events
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMs.toString())
            .appendPath(endMs.toString())
            .build()

        run {
            val selection = calendarIds
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(
                    separator = ",",
                    prefix = "${CalendarContract.Instances.CALENDAR_ID} IN (",
                    postfix = ")"
                ) { "?" }
            val selectionArgs = calendarIds
                ?.takeIf { it.isNotEmpty() }
                ?.map { it.toString() }
                ?.toTypedArray()
            val cursor = context.contentResolver.query(
                uri, projection, selection, selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC"
            ) ?: error("Calendar provider returned no cursor")
            return readEventCursor(cursor)
        }
    }

    internal fun readEventCursor(cursor: Cursor?): List<CalendarEvent> {
        checkNotNull(cursor) { "Calendar provider returned no cursor" }
        val events = mutableListOf<CalendarEvent>()
        cursor.use {
            while (cursor.moveToNext()) {
                events += CalendarEvent(
                    id = cursor.getLong(0), calendarId = cursor.getLong(1),
                    title = cursor.getString(2) ?: "(sin título)",
                    description = cursor.getString(3), startMillis = cursor.getLong(4),
                    endMillis = cursor.getLong(5), location = cursor.getString(6),
                    allDay = cursor.getInt(7) == 1
                )
            }
        }

        return events
    }

    /**
     * Insert a new calendar event directly via ContentResolver.
     * @param reminderMinutes null means no reminder; zero means at the event start.
     * Returns the event ID if successful, null otherwise.
     */
    fun insertEvent(
        context: Context,
        title: String,
        description: String? = null,
        startMillis: Long,
        endMillis: Long = startMillis + 3600_000, // default 1 hour
        location: String? = null,
        reminderMinutes: Int? = null
    ): Long? {
        if (!hasWriteCalendarPermission(context)) {
            Log.w(TAG, "No WRITE_CALENDAR permission")
            return null
        }

        val calendarId = getPrimaryCalendarId(context)
        if (calendarId == null) {
            Log.e(TAG, "No writable calendar found")
            return null
        }

        return insertEventInCalendar(context, calendarId, title, description,
            startMillis, endMillis, location, reminderMinutes)
    }

    /**
     * Insert event from a SuggestedAction's datetime string.
     * @param isReminder If true, adds a 15-minute notification reminder.
     */
    fun insertEventFromAction(
        context: Context,
        action: SuggestedAction,
        isReminder: Boolean = false
    ): Long? {
        val startMillis = action.datetime?.let {
            try {
                isoFormat.parse(it)?.time
            } catch (_: Exception) { null }
        } ?: return null

        return insertEvent(
            context = context,
            title = action.title,
            description = action.description.ifBlank { null },
            startMillis = startMillis,
            reminderMinutes = if (isReminder) 15 else null
        )
    }

    /**
     * Represents a writable calendar for the picker UI.
     */
    data class WritableCalendar(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val accountType: String,
        val isPrimary: Boolean
    ) {
        /** Short label for the dropdown: "Calendar Name (account@email)" */
        val label: String get() = if (accountName.isNotBlank()) "$displayName ($accountName)" else displayName
    }

    data class ReadableCalendar(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val accountType: String,
        val isPrimary: Boolean
    ) {
        val label: String get() = if (accountName.isNotBlank()) "$displayName ($accountName)" else displayName
    }

    /**
     * Get all writable calendars for the calendar picker UI.
     * Returns sorted: Google calendars first, then primary, then alphabetical.
     */
    fun getWritableCalendars(context: Context): List<WritableCalendar> {
        if (!hasCalendarPermission(context)) return emptyList()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY
        )

        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
            "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"

        val calendars = mutableListOf<WritableCalendar>()

        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection, selection, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    calendars.add(WritableCalendar(
                        id = cursor.getLong(0),
                        displayName = cursor.getString(1) ?: "(sin nombre)",
                        accountName = cursor.getString(2) ?: "",
                        accountType = cursor.getString(3) ?: "",
                        isPrimary = cursor.getInt(4) == 1
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query calendars", e)
        }

        return calendars
            .sortedWith(
                compareByDescending<WritableCalendar> { it.accountType == "com.google" }
                    .thenByDescending { it.isPrimary }
                    .thenBy { it.displayName }
            )
    }

    fun getReadableCalendars(context: Context): List<ReadableCalendar> {
        if (!hasCalendarPermission(context)) return emptyList()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY
        )

        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
            "${CalendarContract.Calendars.CAL_ACCESS_READ}"

        val calendars = mutableListOf<ReadableCalendar>()

        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    calendars.add(
                        ReadableCalendar(
                            id = cursor.getLong(0),
                            displayName = cursor.getString(1) ?: "(sin nombre)",
                            accountName = cursor.getString(2) ?: "",
                            accountType = cursor.getString(3) ?: "",
                            isPrimary = cursor.getInt(4) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query readable calendars", e)
        }

        return calendars
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<ReadableCalendar> { it.accountType == "com.google" }
                    .thenByDescending { it.isPrimary }
                    .thenBy { it.displayName.lowercase(Locale.getDefault()) }
            )
    }

    /**
     * Insert event into a specific calendar.
     */
    fun insertEventInCalendar(
        context: Context,
        calendarId: Long,
        title: String,
        description: String? = null,
        startMillis: Long,
        endMillis: Long = startMillis + 3600_000,
        location: String? = null,
        reminderMinutes: Int? = null
    ): Long? {
        if (!hasWriteCalendarPermission(context)) return null

        findMatchingEventId(
            context, calendarId, title, description, startMillis, endMillis,
            location, reminderMinutes
        )?.let { existingId ->
            Log.i(TAG, "Reusing existing calendar event $existingId for an identical dispatch")
            return existingId
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description ?: "")
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        }

        return try {
            val operations = arrayListOf(
                ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                    .withValues(values).build()
            )
            if (reminderMinutes != null) {
                require(reminderMinutes >= 0) { "Invalid reminder offset" }
                operations += ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                    .withValueBackReference(CalendarContract.Reminders.EVENT_ID, 0)
                    .withValue(CalendarContract.Reminders.MINUTES, reminderMinutes)
                    .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    .build()
            }
            val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
            check(results.size == operations.size && results.all { it.uri != null }) {
                "Calendar provider did not confirm the complete event and reminder"
            }
            ContentUris.parseId(checkNotNull(results[0].uri))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save calendar event and reminder", e)
            null
        }
    }

    /** Makes a repeated confirmation idempotent when the provider already committed it. */
    private fun findMatchingEventId(
        context: Context,
        calendarId: Long,
        title: String,
        description: String?,
        startMillis: Long,
        endMillis: Long,
        location: String?,
        reminderMinutes: Int?
    ): Long? {
        if (!hasCalendarPermission(context)) return null
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION
                ),
                "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                    "${CalendarContract.Events.DTSTART} = ? AND " +
                    "${CalendarContract.Events.TITLE} = ? AND " +
                    "${CalendarContract.Events.DELETED} = 0",
                arrayOf(calendarId.toString(), startMillis.toString(), title),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val samePayload = cursor.getString(1).orEmpty() == description.orEmpty() &&
                        cursor.getLong(2) == endMillis &&
                        cursor.getString(3).orEmpty() == location.orEmpty()
                    if (samePayload && hasExpectedReminder(context, eventId, reminderMinutes)) {
                        return@use eventId
                    }
                }
                null
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to check for an existing calendar event", error)
        }.getOrNull()
    }

    private fun hasExpectedReminder(context: Context, eventId: Long, minutes: Int?): Boolean {
        if (minutes == null) return true
        return context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders._ID),
            "${CalendarContract.Reminders.EVENT_ID} = ? AND " +
                "${CalendarContract.Reminders.MINUTES} = ? AND " +
                "${CalendarContract.Reminders.METHOD} = ?",
            arrayOf(
                eventId.toString(), minutes.toString(),
                CalendarContract.Reminders.METHOD_ALERT.toString()
            ),
            null
        )?.use { it.moveToFirst() } == true
    }

    /**
     * Get the best calendar ID for writing events.
     * Priority: Google account calendar > primary calendar > first writable calendar.
     */
    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.OWNER_ACCOUNT
        )

        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
            "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"

        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection, selection, null, null
            )?.use { cursor ->
                data class CalInfo(val id: Long, val name: String, val accountName: String,
                                   val accountType: String, val isPrimary: Boolean)

                val calendars = mutableListOf<CalInfo>()
                while (cursor.moveToNext()) {
                    val info = CalInfo(
                        id = cursor.getLong(0),
                        name = cursor.getString(1) ?: "",
                        accountName = cursor.getString(2) ?: "",
                        accountType = cursor.getString(3) ?: "",
                        isPrimary = cursor.getInt(4) == 1
                    )
                    calendars.add(info)
                    Log.d(TAG, "Calendar found: id=${info.id}, name='${info.name}', " +
                        "account='${info.accountName}', type='${info.accountType}', primary=${info.isPrimary}")
                }

                if (calendars.isEmpty()) {
                    Log.e(TAG, "No writable calendars found")
                    return null
                }

                // 1. Prefer Google account calendar (com.google)
                val googleCal = calendars.firstOrNull {
                    it.accountType == "com.google"
                }
                if (googleCal != null) {
                    Log.i(TAG, "Using Google calendar: id=${googleCal.id}, name='${googleCal.name}', account='${googleCal.accountName}'")
                    return googleCal.id
                }

                // 2. Prefer primary calendar
                val primaryCal = calendars.firstOrNull { it.isPrimary }
                if (primaryCal != null) {
                    Log.i(TAG, "Using primary calendar: id=${primaryCal.id}, name='${primaryCal.name}'")
                    return primaryCal.id
                }

                // 3. Fallback: first writable calendar
                val fallback = calendars.first()
                Log.i(TAG, "Using fallback calendar: id=${fallback.id}, name='${fallback.name}', type='${fallback.accountType}'")
                return fallback.id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get calendar ID", e)
        }
        return null
    }

    /**
     * Format calendar events for inclusion in the LLM prompt.
     */
    fun formatEventsForPrompt(
        todayEvents: List<CalendarEvent>,
        tomorrowEvents: List<CalendarEvent>,
        tomorrowDateStr: String
    ): String {
        val sb = StringBuilder()

        if (todayEvents.isNotEmpty()) {
            sb.appendLine("\nEventos de hoy en el calendario:")
            todayEvents.forEach { sb.appendLine(it.toContextString()) }
        }

        if (tomorrowEvents.isNotEmpty()) {
            sb.appendLine("\nEventos de mañana ($tomorrowDateStr) en el calendario:")
            tomorrowEvents.forEach { sb.appendLine(it.toContextString()) }
        }

        if (todayEvents.isEmpty() && tomorrowEvents.isEmpty()) {
            sb.appendLine("\nNo hay eventos en el calendario para hoy ni mañana.")
        }

        return sb.toString()
    }

}
