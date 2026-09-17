package com.trama.app.summary

import android.content.Context
import android.content.Intent
import android.net.Uri
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
                ActionType.CALL -> createReminder(context, action)
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
        if (CalendarHelper.hasWriteCalendarPermission(context)) {
            val id = CalendarHelper.insertEventFromAction(context, action, isReminder = true)
            if (id != null) {
                Toast.makeText(context, "Evento con aviso creado", Toast.LENGTH_SHORT).show()
                return
            }
        }
        // A clock alarm cannot represent the requested calendar date.
        createCalendarEventViaIntent(context, action)
        Toast.makeText(context, "Revisa la fecha y el aviso en Calendario", Toast.LENGTH_LONG).show()
    }

    private fun createTodo(context: Context, action: SuggestedAction) {
        createCalendarEventViaIntent(context, action)
    }

    private fun sendMessage(context: Context, action: SuggestedAction) {
        val source = listOf(action.title, action.description, action.contact.orEmpty())
            .joinToString(" ")
        if (!EmailActionClassifier.isEmail(source)) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, action.description.ifBlank { action.title })
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Enviar")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val recipient = action.contact
            ?.takeIf { EMAIL_REGEX.matches(it.trim()) }
            ?.trim()
            .orEmpty()
        val body = action.description.ifBlank { action.title }
        val gmailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Uri.encode(recipient)}")
            `package` = GMAIL_PACKAGE
            putExtra(Intent.EXTRA_SUBJECT, action.title)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (gmailIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(gmailIntent)
            Toast.makeText(context, "Revisa y envía el correo en Gmail", Toast.LENGTH_SHORT).show()
            return
        }

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Uri.encode(recipient)}")
            putExtra(Intent.EXTRA_SUBJECT, action.title)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (emailIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(emailIntent)
            Toast.makeText(context, "Revisa y envía el correo", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "No se encontró una app de correo", Toast.LENGTH_LONG).show()
    }

    private fun showNote(context: Context, action: SuggestedAction) {
        val text = action.description.ifBlank { action.title }
        val keepIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            `package` = "com.google.android.keep"
            putExtra(Intent.EXTRA_SUBJECT, action.title)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (keepIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(keepIntent)
            Toast.makeText(context, "Revisa y guarda la nota en Keep", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "Guardar en una app de notas")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private const val GMAIL_PACKAGE = "com.google.android.gm"
    private val EMAIL_REGEX = Regex(
        "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
        RegexOption.IGNORE_CASE
    )
}
