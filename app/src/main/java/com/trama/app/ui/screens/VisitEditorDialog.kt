package com.trama.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trama.shared.model.Place
import com.trama.shared.model.TimelineEvent
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun VisitEditorDialog(
    event: TimelineEvent?, initialPlace: Place, places: List<Place>, saving: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (Long, Long, Long) -> Unit
) {
    val context = LocalContext.current
    var start by rememberSaveable { mutableStateOf(event?.timestamp ?: System.currentTimeMillis() - 3_600_000) }
    var end by rememberSaveable { mutableStateOf(event?.endTimestamp ?: System.currentTimeMillis()) }
    var placeId by rememberSaveable { mutableStateOf(initialPlace.id) }
    var placeMenu by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("d MMM yyyy · HH:mm", Locale("es")) }
    fun pick(value: Long, update: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = value }
        DatePickerDialog(context, { _, year, month, day ->
            c.set(year, month, day)
            TimePickerDialog(context, { _, hour, minute ->
                c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute)
                c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                update(c.timeInMillis)
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (event == null) "Añadir visita" else "Corregir visita") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    TextButton(onClick = { placeMenu = true }, enabled = !saving) {
                        Text(places.find { it.id == placeId }?.name ?: initialPlace.name)
                    }
                    DropdownMenu(expanded = placeMenu, onDismissRequest = { placeMenu = false }) {
                        places.forEach { place ->
                            DropdownMenuItem(text = { Text(place.name) }, onClick = { placeId = place.id; placeMenu = false })
                        }
                    }
                }
                TextButton(onClick = { pick(start) { start = it } }, enabled = !saving) { Text("Llegada: ${format.format(Date(start))}") }
                TextButton(onClick = { pick(end) { end = it } }, enabled = !saving) { Text("Salida: ${format.format(Date(end))}") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(placeId, start, end) }, enabled = !saving) { Text(if (saving) "Guardando…" else "Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } }
    )
}
