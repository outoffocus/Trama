package com.trama.app.summary

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Executes suggested actions by launching appropriate Android intents.
 */
object ActionExecutor {

    private const val TAG = "ActionExecutor"
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())

    /**
     * Returns true if this action needs calendar write permission that isn't granted yet.
     */
    fun needsCalendarPermission(context: Context, action: SuggestedAction): Boolean {
        return action.type == ActionType.CALENDAR_EVENT &&
            !CalendarHelper.hasWriteCalendarPermission(context)
    }

    fun execute(context: Context, action: SuggestedAction) {
        try {
            when (action.type) {
                ActionType.CALENDAR_EVENT -> createCalendarEvent(context, action)
                ActionType.REMINDER -> createReminder(context, action)
                ActionType.TODO -> createTodo(context, action)
                ActionType.MESSAGE -> sendMessage(context, action)
                ActionType.CALL -> makeCall(context, action)
                ActionType.NOTE -> showNote(context, action)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute action: ${action.type}", e)
            Toast.makeText(context, "No se pudo ejecutar la acción: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createCalendarEvent(context: Context, action: SuggestedAction) {
        // Try direct insert first if we have permission
        if (CalendarHelper.hasWriteCalendarPermission(context)) {
            val eventId = CalendarHelper.insertEventFromAction(context, action)
            if (eventId != null) {
                Toast.makeText(context, "Evento creado: ${action.title}", Toast.LENGTH_SHORT).show()
                return
            }
            Log.w(TAG, "Direct insert failed (datetime=${action.datetime}), trying intent")
        }

        // Fallback: open calendar app via intent
        createCalendarEventViaIntent(context, action)
    }

    private fun createCalendarEventViaIntent(context: Context, action: SuggestedAction) {
        // Parse datetime
        var beginTime: Long? = null
        action.datetime?.let { dt ->
            try {
                beginTime = isoFormat.parse(dt)?.time
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse datetime: $dt", e)
            }
        }

        // Method 1: explicit Google Calendar
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                `package` = "com.google.android.calendar"
                putExtra(CalendarContract.Events.TITLE, action.title)
                if (action.description.isNotBlank()) {
                    putExtra(CalendarContract.Events.DESCRIPTION, action.description)
                }
                beginTime?.let { bt ->
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, bt)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, bt + 3600_000)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.i(TAG, "Calendar event launched via explicit Google Calendar intent")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Explicit Google Calendar insert failed", e)
        }

        // Method 2: ACTION_INSERT with CalendarContract (standard Android)
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, action.title)
                if (action.description.isNotBlank()) {
                    putExtra(CalendarContract.Events.DESCRIPTION, action.description)
                }
                beginTime?.let { bt ->
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, bt)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, bt + 3600_000)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.i(TAG, "Calendar event intent launched via ACTION_INSERT")
                return
            }
            Log.w(TAG, "No activity found for ACTION_INSERT CalendarContract")
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_INSERT failed", e)
        }

        // Method 3: ACTION_EDIT (works on some Samsung devices)
        try {
            val intent = Intent(Intent.ACTION_EDIT).apply {
                type = "vnd.android.cursor.item/event"
                putExtra(CalendarContract.Events.TITLE, action.title)
                if (action.description.isNotBlank()) {
                    putExtra(CalendarContract.Events.DESCRIPTION, action.description)
                }
                beginTime?.let { bt ->
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, bt)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, bt + 3600_000)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "Calendar event intent launched via ACTION_EDIT")
            return
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_EDIT failed", e)
        }

        // Method 4: Google Calendar web deep link
        try {
            val sb = StringBuilder("https://calendar.google.com/calendar/render?action=TEMPLATE")
            sb.append("&text=${Uri.encode(action.title)}")
            if (action.description.isNotBlank()) {
                sb.append("&details=${Uri.encode(action.description)}")
            }
            beginTime?.let { bt ->
                val df = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault())
                sb.append("&dates=${df.format(bt)}/${df.format(bt + 3600_000)}")
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sb.toString()))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "Calendar event launched via Google Calendar URL")
            return
        } catch (e: Exception) {
            Log.w(TAG, "Google Calendar URL failed", e)
        }

        Toast.makeText(context, "No se encontró una app de calendario", Toast.LENGTH_LONG).show()
    }

    private fun createReminder(context: Context, action: SuggestedAction) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, action.title)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)

            action.datetime?.let { dt ->
                try {
                    val date = isoFormat.parse(dt)
                    if (date != null) {
                        val cal = java.util.Calendar.getInstance().apply { time = date }
                        putExtra(AlarmClock.EXTRA_HOUR, cal.get(java.util.Calendar.HOUR_OF_DAY))
                        putExtra(AlarmClock.EXTRA_MINUTES, cal.get(java.util.Calendar.MINUTE))
                    }
                } catch (_: Exception) {}
            }
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun createTodo(context: Context, action: SuggestedAction) {
        createCalendarEventViaIntent(context, action)
    }

    private fun sendMessage(context: Context, action: SuggestedAction) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, action.description.ifBlank { action.title })
        }
        val chooser = Intent.createChooser(intent, "Enviar mensaje a ${action.contact ?: "..."}")
        context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun makeCall(context: Context, action: SuggestedAction) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://contacts/people/")
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Toast.makeText(context, "Busca a: ${action.contact ?: action.title}", Toast.LENGTH_LONG).show()
    }

    private fun showNote(context: Context, action: SuggestedAction) {
        Toast.makeText(context, action.title, Toast.LENGTH_LONG).show()
    }
}
