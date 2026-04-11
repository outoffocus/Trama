package com.trama.app.summary

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
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

    fun getEventsForRange(context: Context, startMillis: Long, endMillis: Long): List<CalendarEvent> {
        if (!hasCalendarPermission(context)) {
            Log.w(TAG, "No READ_CALENDAR permission")
            return emptyList()
        }
        return queryEvents(context, startMillis, endMillis)
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

    private fun queryEvents(context: Context, startMs: Long, endMs: Long): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
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

        try {
            context.contentResolver.query(
                uri, projection, null, null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    events.add(
                        CalendarEvent(
                            id = cursor.getLong(0),
                            title = cursor.getString(1) ?: "(sin título)",
                            description = cursor.getString(2),
                            startMillis = cursor.getLong(3),
                            endMillis = cursor.getLong(4),
                            location = cursor.getString(5),
                            allDay = cursor.getInt(6) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query calendar events", e)
        }

        return events
    }

    /**
     * Insert a new calendar event directly via ContentResolver.
     * @param reminderMinutes If > 0, adds a reminder notification N minutes before the event.
     * Returns the event ID if successful, null otherwise.
     */
    fun insertEvent(
        context: Context,
        title: String,
        description: String? = null,
        startMillis: Long,
        endMillis: Long = startMillis + 3600_000, // default 1 hour
        location: String? = null,
        reminderMinutes: Int = 0
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
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.let { ContentUris.parseId(it) }
            Log.i(TAG, "Calendar event created: id=$eventId, title='$title'")

            // Add reminder if requested
            if (eventId != null && reminderMinutes > 0) {
                addReminder(context, eventId, reminderMinutes)
            }

            eventId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert calendar event", e)
            null
        }
    }

    /**
     * Add a reminder (notification) to an existing calendar event.
     */
    private fun addReminder(context: Context, eventId: Long, minutesBefore: Int) {
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        try {
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            Log.i(TAG, "Reminder added: $minutesBefore min before event $eventId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add reminder", e)
        }
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
            reminderMinutes = if (isReminder) 15 else 0
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

        // Only Google Calendar accounts
        return calendars
            .filter { it.accountType == "com.google" }
            .sortedWith(compareByDescending<WritableCalendar> { it.isPrimary }.thenBy { it.displayName })
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
        reminderMinutes: Int = 0
    ): Long? {
        if (!hasWriteCalendarPermission(context)) return null

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
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.let { ContentUris.parseId(it) }
            Log.i(TAG, "Calendar event created in cal=$calendarId: id=$eventId, title='$title'")
            if (eventId != null && reminderMinutes > 0) addReminder(context, eventId, reminderMinutes)
            eventId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert calendar event", e)
            null
        }
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
