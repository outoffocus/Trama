package com.trama.app.summary

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.ui.graphics.vector.ImageVector
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.EntryActionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EntryQuickAction(
    val label: String,
    val icon: ImageVector,
    val action: SuggestedAction
)

object EntryActionBridge {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())

    fun build(entry: DiaryEntry): EntryQuickAction? {
        // A suggestion must be explicitly accepted before it can trigger an
        // external side effect such as creating a calendar event or Keep note.
        if (entry.status != com.trama.shared.model.EntryStatus.PENDING) return null

        val title = entry.displayText.trim().ifBlank { return null }
        val datetime = entry.dueDate?.let { due ->
            isoFormat.format(Date(due))
        }
        // Calls are reminders to contact someone, so Calendar handles them even
        // when the model did not resolve a date yet (the review dialog asks for it).
        // Emails keep their direct destination; other dated work also uses Calendar.
        return when (entry.actionType) {
            EntryActionType.CALL -> calendarAction(entry, title, datetime)
            EntryActionType.SEND -> emailAction(entry, title)
            EntryActionType.EVENT -> calendarAction(entry, title, datetime)
            else -> if (datetime != null) {
                calendarAction(entry, title, datetime)
            } else {
                keepAction(entry, title)
            }
        }
    }

    private fun emailAction(entry: DiaryEntry, title: String): EntryQuickAction {
        val source = "$title ${entry.text}"
        return EntryQuickAction(
            label = if (EmailActionClassifier.isEmail(source)) "Gmail" else "Enviar",
            icon = Icons.Default.Email,
            action = SuggestedAction(
                type = ActionType.MESSAGE,
                title = title,
                description = entry.text,
                contact = EmailActionClassifier.recipient(source)
            )
        )
    }

    private fun calendarAction(
        entry: DiaryEntry,
        title: String,
        datetime: String?
    ): EntryQuickAction {
        val type = if (entry.actionType == EntryActionType.EVENT) {
            ActionType.CALENDAR_EVENT
        } else {
            ActionType.REMINDER
        }
        return EntryQuickAction(
            label = if (type == ActionType.REMINDER) "Programar" else "Calendar",
            icon = if (type == ActionType.REMINDER) Icons.Default.Alarm else Icons.Default.CalendarMonth,
            action = SuggestedAction(
                type = type,
                title = title,
                description = entry.text,
                datetime = datetime
            )
        )
    }

    private fun keepAction(entry: DiaryEntry, title: String): EntryQuickAction {
        val sourceText = entry.text.trim()
        val noteBody = if (sourceText.isBlank() || sourceText.equals(title, ignoreCase = true)) {
            title
        } else {
            "$title\n\nContexto original:\n$sourceText"
        }
        return EntryQuickAction(
            label = "Keep",
            icon = Icons.AutoMirrored.Filled.NoteAdd,
            action = SuggestedAction(
                type = ActionType.NOTE,
                title = title,
                description = noteBody
            )
        )
    }

}
