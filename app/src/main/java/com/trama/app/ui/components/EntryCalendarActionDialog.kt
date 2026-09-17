package com.trama.app.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.trama.app.summary.ActionExecutor
import com.trama.app.summary.ActionType
import com.trama.app.summary.CalendarHelper
import com.trama.app.summary.SuggestedAction
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.EntryExternalState
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared Calendar commit contract used from day, actions and detail. */
@Composable
fun EntryCalendarActionDialog(
    entryId: Long,
    action: SuggestedAction,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val scope = rememberCoroutineScope()
    var saving by remember(entryId) { mutableStateOf(false) }
    val isReminder = action.type == ActionType.REMINDER

    CalendarActionDialog(
        action = action,
        dialogTitle = if (isReminder) "Programar recordatorio" else "Añadir al calendario",
        confirmLabel = "Guardar en Calendar",
        onDismiss = { if (!saving) onDismiss() },
        onConfirm = { title, description, date, time, calendarId ->
            if (saving) return@CalendarActionDialog
            saving = true
            val datetime = "${date}T${time}"
            val updatedAction = action.copy(
                title = title,
                description = description,
                datetime = datetime
            )
            val startMillis = runCatching {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).parse(datetime)?.time
            }.getOrNull()
            scope.launch {
                runCatching {
                    val eventId = withContext(Dispatchers.IO) {
                        if (startMillis != null && calendarId != null) {
                            CalendarHelper.insertEventInCalendar(
                                context = context,
                                calendarId = calendarId,
                                title = title,
                                description = description.ifBlank { null },
                                startMillis = startMillis,
                                reminderMinutes = if (isReminder) 15 else null
                            )
                        } else {
                            CalendarHelper.insertEventFromAction(context, updatedAction, isReminder)
                        }
                    }
                    if (eventId != null) {
                        repository.updateExternalState(
                            entryId, EntryExternalState.SCHEDULED, eventId
                        )
                        Toast.makeText(context, "Programada en Calendar", Toast.LENGTH_SHORT).show()
                    } else {
                        ActionExecutor.execute(context, updatedAction)
                        repository.updateExternalState(
                            entryId, EntryExternalState.EDITOR_OPENED, null
                        )
                        Toast.makeText(
                            context,
                            "Revisa y guarda la propuesta en Calendar",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onDismiss()
                }.onFailure {
                    repository.updateExternalState(entryId, EntryExternalState.FAILED, null)
                    Toast.makeText(
                        context,
                        "No se ha guardado. La acción sigue en Trama.",
                        Toast.LENGTH_LONG
                    ).show()
                    saving = false
                }
            }
        }
    )
}
