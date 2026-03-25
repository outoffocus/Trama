package com.mydiary.app.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydiary.app.summary.ActionExecutor
import com.mydiary.app.summary.ActionType
import com.mydiary.app.summary.CalendarHelper
import com.mydiary.app.summary.DailySummary
import com.mydiary.app.summary.SuggestedAction
import com.mydiary.app.summary.SummaryGenerator
import com.mydiary.app.ui.DatabaseProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DatabaseProvider.getRepository(context) }

    var summary by remember { mutableStateOf<DailySummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Pending count for header
    val pendingEntries by repository.getPending().collectAsState(initial = emptyList())

    // Calendar event dialog
    var editingAction by remember { mutableStateOf<IndexedAction?>(null) }
    var pendingAction by remember { mutableStateOf<IndexedAction?>(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val writeGranted = results[Manifest.permission.WRITE_CALENDAR] == true
        pendingAction?.let { ia ->
            if (writeGranted) editingAction = ia
            else ActionExecutor.execute(context, ia.action)
            pendingAction = null
        }
    }

    // Load saved summary
    LaunchedEffect(Unit) {
        summary = loadSummary(context)
    }

    // Calendar edit dialog
    editingAction?.let { ia ->
        val action = ia.action
        val isReminder = action.type == ActionType.REMINDER
        CalendarEventDialog(
            action = action,
            dialogTitle = if (isReminder) "Crear recordatorio" else "Añadir al calendario",
            confirmLabel = if (isReminder) "Crear" else "Añadir",
            onDismiss = { editingAction = null },
            onConfirm = { title, description, date, time, calendarId ->
                val datetime = "${date}T${time}"
                val updatedAction = action.copy(title = title, description = description, datetime = datetime)
                val startMillis = try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).parse(datetime)?.time
                } catch (_: Exception) { null }

                val eventId = if (startMillis != null && calendarId != null) {
                    CalendarHelper.insertEventInCalendar(
                        context, calendarId, title, description.ifBlank { null },
                        startMillis, reminderMinutes = if (isReminder) 15 else 0
                    )
                } else {
                    CalendarHelper.insertEventFromAction(context, updatedAction, isReminder = isReminder)
                }
                if (eventId != null) {
                    android.widget.Toast.makeText(context,
                        if (isReminder) "Recordatorio creado" else "Evento añadido", android.widget.Toast.LENGTH_SHORT).show()
                    // Mark as done
                    summary = summary?.let { s ->
                        val newActions = s.actions.toMutableList()
                        newActions[ia.index] = newActions[ia.index].copy(done = true)
                        s.copy(actions = newActions).also { saveSummary(context, it) }
                    }
                } else {
                    android.widget.Toast.makeText(context, "Error, abriendo calendario...", android.widget.Toast.LENGTH_SHORT).show()
                    ActionExecutor.execute(context, updatedAction)
                }
                editingAction = null
            }
        )
    }

    fun executeAction(index: Int, action: SuggestedAction) {
        if (action.type == ActionType.CALENDAR_EVENT || action.type == ActionType.REMINDER) {
            if (CalendarHelper.hasWriteCalendarPermission(context)) {
                editingAction = IndexedAction(index, action)
            } else {
                pendingAction = IndexedAction(index, action)
                calendarPermissionLauncher.launch(arrayOf(
                    Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR
                ))
            }
        } else {
            ActionExecutor.execute(context, action)
            // Mark as done
            summary = summary?.let { s ->
                val newActions = s.actions.toMutableList()
                newActions[index] = newActions[index].copy(done = true)
                s.copy(actions = newActions).also { saveSummary(context, it) }
            }
        }
    }

    fun toggleActionDone(index: Int) {
        summary = summary?.let { s ->
            val newActions = s.actions.toMutableList()
            newActions[index] = newActions[index].copy(done = !newActions[index].done)
            s.copy(actions = newActions).also { saveSummary(context, it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null,
                            modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Acciones sugeridas")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                loading = true; error = null
                                try { summary = generateNow(context) }
                                catch (e: Exception) { error = e.message }
                                loading = false
                            }
                        },
                        enabled = !loading
                    ) { Icon(Icons.Default.Refresh, contentDescription = "Regenerar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Analizando ${pendingEntries.size} pendientes...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Scaffold
            }

            error?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Error: $it", modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            val s = summary
            if (s == null) {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Sin resumen", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Analiza tus notas pendientes y genera acciones concretas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(24.dp))
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    loading = true; error = null
                                    try { summary = generateNow(context) }
                                    catch (e: Exception) { error = e.message }
                                    loading = false
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generar acciones")
                        }
                    }
                }
            } else {
                // Header
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = formatDate(s.date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val doneCount = s.actions.count { it.done }
                    Text(
                        text = "${s.actions.size} acciones, $doneCount completadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action list
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(s.actions) { index, action ->
                        ActionCard(
                            action = action,
                            onExecute = { executeAction(index, action) },
                            onToggleDone = { toggleActionDone(index) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ── Action Card ─────────────────────────────────────────────────────────────

@Composable
private fun ActionCard(
    action: SuggestedAction,
    onExecute: () -> Unit,
    onToggleDone: () -> Unit
) {
    val icon = actionIcon(action.type)
    val accentColor = actionColor(action.type)
    val label = actionLabel(action.type)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (!action.done) onExecute() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (action.done)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (action.done) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                shape = CircleShape,
                color = if (action.done) accentColor.copy(alpha = 0.06f) else accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (action.done) Icons.Default.CheckCircle else icon,
                        contentDescription = null,
                        tint = if (action.done) MaterialTheme.colorScheme.tertiary else accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (action.done) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (action.done) TextDecoration.LineThrough else TextDecoration.None
                )
                if (action.description.isNotBlank()) {
                    Text(
                        text = action.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (action.done) 0.4f else 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    action.datetime?.let {
                        Text(it.replace("T", " "), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    action.contact?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.7f))
                    }
                }
            }

            // Action type label + checkbox
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(6.dp), color = accentColor.copy(alpha = if (action.done) 0.05f else 0.08f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = if (action.done) 0.4f else 0.8f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Checkbox(
                    checked = action.done,
                    onCheckedChange = { onToggleDone() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.tertiary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

// ── Calendar Event Dialog ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarEventDialog(
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

    var title by remember { mutableStateOf(action.title) }
    var description by remember { mutableStateOf(action.description) }
    var date by remember { mutableStateOf(defaultDate) }
    var time by remember { mutableStateOf(defaultTime) }

    // Calendar picker
    val calendars = remember { CalendarHelper.getWritableCalendars(context) }
    var selectedCalendarIndex by remember { mutableStateOf(0) }
    var calendarDropdownExpanded by remember { mutableStateOf(false) }

    // Date picker
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(defaultDate)?.time
                ?: System.currentTimeMillis()
        } catch (_: Exception) { System.currentTimeMillis() }
    )

    // Time picker
    var showTimePicker by remember { mutableStateOf(false) }
    val defaultHour = defaultTime.substringBefore(":").toIntOrNull() ?: 9
    val defaultMinute = defaultTime.substringAfter(":").toIntOrNull() ?: 0
    val timePickerState = rememberTimePickerState(
        initialHour = defaultHour,
        initialMinute = defaultMinute,
        is24Hour = true
    )

    // Date picker dialog
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

    // Time picker dialog
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
                // Title
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))

                // Description
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(12.dp))

                // Calendar selector
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
                                                Text(cal.accountName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                // Date & Time - clickable fields that open pickers
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = formatDateShort(date),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha") },
                        modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        interactionSource = remember { MutableInteractionSource() }.also { source ->
                            LaunchedEffect(source) {
                                source.interactions.collect { interaction ->
                                    if (interaction is PressInteraction.Release) showDatePicker = true
                                }
                            }
                        }
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hora") },
                        modifier = Modifier.weight(0.7f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        interactionSource = remember { MutableInteractionSource() }.also { source ->
                            LaunchedEffect(source) {
                                source.interactions.collect { interaction ->
                                    if (interaction is PressInteraction.Release) showTimePicker = true
                                }
                            }
                        }
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
                enabled = title.isNotBlank() && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && time.matches(Regex("\\d{1,2}:\\d{2}")),
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

/** Format "2026-03-25" → "Mar 25, 2026" for display */
private fun formatDateShort(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val d = sdf.parse(dateStr) ?: return dateStr
        SimpleDateFormat("d MMM yyyy", Locale("es")).format(d)
    } catch (_: Exception) { dateStr }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private data class IndexedAction(val index: Int, val action: SuggestedAction)

private fun actionIcon(type: ActionType): ImageVector = when (type) {
    ActionType.CALENDAR_EVENT -> Icons.Default.CalendarMonth
    ActionType.REMINDER -> Icons.Default.NotificationImportant
    ActionType.TODO -> Icons.Default.TaskAlt
    ActionType.MESSAGE -> Icons.Default.Message
    ActionType.CALL -> Icons.Default.Call
    ActionType.NOTE -> Icons.Default.Notes
}

@Composable
private fun actionColor(type: ActionType): Color = when (type) {
    ActionType.CALENDAR_EVENT -> MaterialTheme.colorScheme.tertiary
    ActionType.REMINDER -> MaterialTheme.colorScheme.error
    ActionType.TODO -> MaterialTheme.colorScheme.primary
    ActionType.MESSAGE -> MaterialTheme.colorScheme.secondary
    ActionType.CALL -> MaterialTheme.colorScheme.tertiary
    ActionType.NOTE -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun actionLabel(type: ActionType): String = when (type) {
    ActionType.CALENDAR_EVENT -> "Calendario"
    ActionType.REMINDER -> "Recordar"
    ActionType.TODO -> "Tarea"
    ActionType.MESSAGE -> "Mensaje"
    ActionType.CALL -> "Llamar"
    ActionType.NOTE -> "Nota"
}

private fun formatDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return dateStr
        SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es")).format(date).replaceFirstChar { it.uppercase() }
    } catch (_: Exception) { dateStr }
}

private fun loadSummary(context: Context): DailySummary? {
    val prefs = context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
    val json = prefs.getString("latest_summary", null) ?: return null
    return try { Json.decodeFromString<DailySummary>(json) } catch (_: Exception) { null }
}

private fun saveSummary(context: Context, summary: DailySummary) {
    val json = Json.encodeToString(summary)
    context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
        .edit().putString("latest_summary", json).apply()
}

private suspend fun generateNow(context: Context): DailySummary {
    val repository = DatabaseProvider.getRepository(context)
    val generator = SummaryGenerator(context)

    val cal = Calendar.getInstance()
    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    val startOfDay = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 1)
    val endOfDay = cal.timeInMillis

    val todaysEntries = repository.byDateRange(startOfDay, endOfDay).first()
    val pendingOlder = repository.getPending().first().filter { it.createdAt < startOfDay }
    val allRelevant = todaysEntries + pendingOlder

    val summary = generator.generate(allRelevant, dateStr)
    saveSummary(context, summary)
    return summary
}
