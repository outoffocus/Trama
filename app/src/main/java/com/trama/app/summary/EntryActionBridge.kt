package com.trama.app.summary

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Send
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
        if (entry.status != com.trama.shared.model.EntryStatus.PENDING &&
            entry.status != com.trama.shared.model.EntryStatus.SUGGESTED
        ) return null

        val title = entry.displayText.trim().ifBlank { return null }
        val datetime = entry.dueDate?.let { due ->
            isoFormat.format(Date(due))
        }
        val reminderLike = looksLikeReminder(entry)

        return when (entry.actionType) {
            EntryActionType.EVENT -> EntryQuickAction(
                label = "Calendario",
                icon = Icons.Default.CalendarMonth,
                action = SuggestedAction(
                    type = ActionType.CALENDAR_EVENT,
                    title = title,
                    description = entry.text,
                    datetime = datetime
                )
            )

            EntryActionType.CALL -> EntryQuickAction(
                label = "Llamar",
                icon = Icons.Default.Call,
                action = SuggestedAction(
                    type = ActionType.CALL,
                    title = title,
                    contact = extractContact(title)
                )
            )

            EntryActionType.SEND,
            EntryActionType.TALK_TO -> EntryQuickAction(
                label = "Mensaje",
                icon = Icons.AutoMirrored.Filled.Send,
                action = SuggestedAction(
                    type = ActionType.MESSAGE,
                    title = title,
                    description = title,
                    contact = extractContact(title)
                )
            )

            EntryActionType.BUY,
            EntryActionType.REVIEW,
            EntryActionType.GENERIC -> {
                val type = when {
                    datetime == null -> ActionType.TODO
                    reminderLike -> ActionType.REMINDER
                    else -> ActionType.CALENDAR_EVENT
                }
                EntryQuickAction(
                    label = when (type) {
                        ActionType.CALENDAR_EVENT -> "Calendario"
                        ActionType.REMINDER -> "Recordatorio"
                        else -> "Tarea"
                    },
                    icon = when (type) {
                        ActionType.CALENDAR_EVENT -> Icons.Default.CalendarMonth
                        ActionType.REMINDER -> Icons.Default.Alarm
                        else -> Icons.Default.AddTask
                    },
                    action = SuggestedAction(
                        type = type,
                        title = title,
                        description = entry.text,
                        datetime = datetime
                    )
                )
            }

            else -> null
        }
    }

    private fun looksLikeReminder(entry: DiaryEntry): Boolean {
        val lower = buildString {
            append(entry.text)
            append(' ')
            append(entry.displayText)
        }.lowercase(Locale.getDefault())

        return listOf(
            "recordar",
            "recuerdame",
            "recuérdame",
            "acordarme",
            "no olvidar",
            "no olvidarme",
            "tengo que",
            "hay que",
            "debo",
            "deberia",
            "debería",
            "mañana",
            "manana",
            "pasado mañana",
            "pasado manana",
            "esta tarde",
            "esta noche",
            "esta mañana",
            "esta manana",
            "lunes",
            "martes",
            "miércoles",
            "miercoles",
            "jueves",
            "viernes",
            "sábado",
            "sabado",
            "domingo",
            "fin de semana",
            "finde"
        ).any { marker -> lower.contains(marker) }
    }

    private fun extractContact(text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        val markers = listOf("llamar a ", "hablar con ", "enviar a ", "mandar a ", "decir a ")
        val marker = markers.firstOrNull { lower.contains(it) } ?: return null
        val start = lower.indexOf(marker)
        if (start < 0) return null
        val raw = text.substring(start + marker.length)
            .substringBefore(" mañana")
            .substringBefore(" hoy")
            .substringBefore(" luego")
            .trim()
        return raw.takeIf { it.isNotBlank() }
    }
}
