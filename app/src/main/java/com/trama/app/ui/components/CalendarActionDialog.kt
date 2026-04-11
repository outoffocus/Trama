package com.trama.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trama.app.summary.CalendarHelper
import com.trama.app.summary.SuggestedAction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarActionDialog(
    action: SuggestedAction,
    dialogTitle: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, date: String, time: String, calendarId: Long?) -> Unit
) {
    val context = LocalContext.current

    val defaultDate: String
    val defaultTime: String

    if (action.datetime != null && action.datetime.contains("T")) {
        val parts = action.datetime.split("T")
        defaultDate = parts[0]
        defaultTime = parts.getOrElse(1) { "09:00" }
    } else {
        defaultDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        defaultTime = "09:00"
    }

    var title by remember(action.title) { mutableStateOf(action.title) }
    var description by remember(action.description) { mutableStateOf(action.description) }
    var date by remember(defaultDate) { mutableStateOf(defaultDate) }
    var time by remember(defaultTime) { mutableStateOf(defaultTime) }

    val calendars = remember { CalendarHelper.getWritableCalendars(context) }
    var selectedCalendarIndex by remember { mutableStateOf(0) }
    var calendarDropdownExpanded by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(defaultDate)?.time
                ?: System.currentTimeMillis()
        } catch (_: Exception) { System.currentTimeMillis() }
    )

    var showTimePicker by remember { mutableStateOf(false) }
    val defaultHour = defaultTime.substringBefore(":").toIntOrNull() ?: 9
    val defaultMinute = defaultTime.substringAfter(":").toIntOrNull() ?: 0
    val timePickerState = rememberTimePickerState(
        initialHour = defaultHour,
        initialMinute = defaultMinute,
        is24Hour = true
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(millis)
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Seleccionar hora") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    time = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )

                if (calendars.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = calendarDropdownExpanded,
                        onExpandedChange = { calendarDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = calendars.getOrNull(selectedCalendarIndex)?.label ?: "Seleccionar",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Calendario") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = calendarDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = calendarDropdownExpanded,
                            onDismissRequest = { calendarDropdownExpanded = false }
                        ) {
                            calendars.forEachIndexed { index, cal ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(cal.displayName, fontWeight = FontWeight.Medium)
                                            if (cal.accountName.isNotBlank()) {
                                                Text(
                                                    cal.accountName,
                                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedCalendarIndex = index
                                        calendarDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val dateInteraction = remember { MutableInteractionSource() }
                    val timeInteraction = remember { MutableInteractionSource() }

                    LaunchedEffect(dateInteraction) {
                        dateInteraction.interactions.collect { interaction ->
                            if (interaction is PressInteraction.Release) showDatePicker = true
                        }
                    }
                    LaunchedEffect(timeInteraction) {
                        timeInteraction.interactions.collect { interaction ->
                            if (interaction is PressInteraction.Release) showTimePicker = true
                        }
                    }

                    OutlinedTextField(
                        value = formatDateShort(date),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDatePicker = true },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        interactionSource = dateInteraction
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hora") },
                        modifier = Modifier
                            .weight(0.7f)
                            .clickable { showTimePicker = true },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        interactionSource = timeInteraction
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val calId = calendars.getOrNull(selectedCalendarIndex)?.id
                    onConfirm(title, description, date, time, calId)
                },
                enabled = title.isNotBlank() &&
                    date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
                    time.matches(Regex("\\d{1,2}:\\d{2}")),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        shape = RoundedCornerShape(24.dp)
    )
}

private fun formatDateShort(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val d = sdf.parse(dateStr) ?: return dateStr
        SimpleDateFormat("d MMM yyyy", Locale("es")).format(d)
    } catch (_: Exception) { dateStr }
}
