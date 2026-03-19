package com.mydiary.app.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydiary.app.summary.ActionExecutor
import com.mydiary.app.summary.ActionType
import com.mydiary.app.summary.CalendarHelper
import com.mydiary.app.summary.DailySummary
import com.mydiary.app.summary.EntryGroup
import com.mydiary.app.summary.SuggestedAction
import com.mydiary.app.summary.SummaryGenerator
import com.mydiary.app.ui.DatabaseProvider
import com.mydiary.app.ui.theme.CategoryColors
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

    var summary by remember { mutableStateOf<DailySummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Edit dialog state for calendar events & reminders
    var editingAction by remember { mutableStateOf<SuggestedAction?>(null) }

    // Calendar permission flow
    var pendingAction by remember { mutableStateOf<SuggestedAction?>(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val writeGranted = results[Manifest.permission.WRITE_CALENDAR] == true
        pendingAction?.let { action ->
            if (writeGranted) {
                editingAction = action
            } else {
                ActionExecutor.execute(context, action)
            }
            pendingAction = null
        }
    }

    // Load saved summary on launch
    LaunchedEffect(Unit) {
        summary = loadLatestSummary(context)
    }

    // Edit dialog for calendar events and reminders
    editingAction?.let { action ->
        val isReminder = action.type == ActionType.REMINDER
        CalendarEventDialog(
            action = action,
            dialogTitle = if (isReminder) "Crear recordatorio" else "Añadir evento al calendario",
            confirmLabel = if (isReminder) "Crear" else "Añadir",
            showReminderNote = isReminder,
            onDismiss = { editingAction = null },
            onConfirm = { title, description, date, time ->
                editingAction = null
                val datetime = "${date}T${time}"
                val updatedAction = action.copy(
                    title = title,
                    description = description,
                    datetime = datetime
                )
                val eventId = CalendarHelper.insertEventFromAction(
                    context, updatedAction, isReminder = isReminder
                )
                if (eventId != null) {
                    val msg = if (isReminder) "Recordatorio creado"
                             else "Evento añadido al calendario"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(
                        context, "Error al crear evento, abriendo calendario...",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    ActionExecutor.execute(context, updatedAction)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resumen del dia")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (summary != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    loading = true
                                    error = null
                                    try {
                                        summary = generateSummaryNow(context)
                                    } catch (e: Exception) {
                                        error = e.message
                                    }
                                    loading = false
                                }
                            },
                            enabled = !loading
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (loading) {
                LoadingState()
                return@Scaffold
            }

            error?.let {
                ErrorCard(it)
                Spacer(modifier = Modifier.height(16.dp))
            }

            val s = summary
            if (s == null) {
                EmptyState(onGenerate = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            summary = generateSummaryNow(context)
                        } catch (e: Exception) {
                            error = e.message
                        }
                        loading = false
                    }
                })
            } else {
                SummaryContent(
                    summary = s,
                    onActionExecute = { action ->
                        if (action.type == ActionType.CALENDAR_EVENT ||
                            action.type == ActionType.REMINDER
                        ) {
                            if (CalendarHelper.hasWriteCalendarPermission(context)) {
                                editingAction = action
                            } else {
                                pendingAction = action
                                calendarPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            }
                        } else {
                            ActionExecutor.execute(context, action)
                        }
                    }
                )
            }
        }
    }
}

// ── Summary Content ─────────────────────────────────────────────────────────

@Composable
private fun SummaryContent(
    summary: DailySummary,
    onActionExecute: (SuggestedAction) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(8.dp))

        // Date header
        Text(
            text = formatDate(summary.date),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${summary.entryCount} entradas capturadas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Narrative card with gradient
        NarrativeCard(summary.narrative)

        // Entry groups
        if (summary.groups.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            summary.groups.forEachIndexed { index, group ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    GroupCard(
                        group = group,
                        colorIndex = index
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Actions
        if (summary.actions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Acciones sugeridas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            summary.actions.forEach { action ->
                ActionCard(
                    action = action,
                    onExecute = { onActionExecute(action) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Sin acciones sugeridas para hoy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Narrative Card ──────────────────────────────────────────────────────────

@Composable
private fun NarrativeCard(narrative: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        )
                    )
                )
        ) {
            Text(
                text = narrative,
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ── Group Card ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(
    group: EntryGroup,
    colorIndex: Int
) {
    val accentColor = CategoryColors[colorIndex % CategoryColors.size]

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with emoji + label + count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Text(
                    text = group.emoji,
                    fontSize = 22.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${group.items.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            // Items
            group.items.forEach { item ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

// ── Action Card ─────────────────────────────────────────────────────────────

@Composable
private fun ActionCard(
    action: SuggestedAction,
    onExecute: () -> Unit
) {
    val icon = actionIcon(action.type)
    val accentColor = actionColor(action.type)
    val actionLabel = actionLabel(action.type)

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onExecute,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon circle
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (action.description.isNotBlank()) {
                    Text(
                        text = action.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    action.datetime?.let {
                        Text(
                            text = it.replace("T", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    action.contact?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    }
                }
            }

            // Action label chip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.1f)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

// ── Empty State ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(onGenerate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Sin resumen todavia",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Genera un resumen inteligente de tus notas del dia, con categorias automaticas y acciones sugeridas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        FilledTonalButton(
            onClick = onGenerate,
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generar resumen")
        }
    }
}

// ── Loading State ───────────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Analizando tus notas...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Error Card ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Error: $message",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

// ── Calendar Event Edit Dialog ──────────────────────────────────────────────

@Composable
private fun CalendarEventDialog(
    action: SuggestedAction,
    dialogTitle: String = "Añadir evento al calendario",
    confirmLabel: String = "Añadir",
    showReminderNote: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, date: String, time: String) -> Unit
) {
    val defaultDate: String
    val defaultTime: String

    if (action.datetime != null && action.datetime.contains("T")) {
        val parts = action.datetime.split("T")
        defaultDate = parts[0]
        defaultTime = parts.getOrElse(1) { "09:00" }
    } else {
        val titleLower = action.title.lowercase()
        val dateCal = Calendar.getInstance()
        if (titleLower.contains("mañana")) {
            dateCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        defaultDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(dateCal.time)

        val timeRegex = Regex("""a las (\d{1,2})(?::(\d{2}))?""")
        val timeMatch = timeRegex.find(titleLower)
        defaultTime = if (timeMatch != null) {
            val hour = timeMatch.groupValues[1].toIntOrNull() ?: 9
            val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
            String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        } else {
            "09:00"
        }
    }

    var title by remember { mutableStateOf(action.title) }
    var description by remember { mutableStateOf(action.description) }
    var date by remember { mutableStateOf(defaultDate) }
    var time by remember { mutableStateOf(defaultTime) }

    val displayDate = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val parsed = sdf.parse(date)
        if (parsed != null) {
            SimpleDateFormat("EEE, d MMM yyyy", Locale("es")).format(parsed)
        } else date
    } catch (_: Exception) { date }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = if (showReminderNote)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                else
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        if (showReminderNote) Icons.Default.NotificationImportant
                        else Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = if (showReminderNote)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titulo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripcion (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Fecha") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        supportingText = { Text(displayDate) },
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("Hora") },
                        modifier = Modifier.weight(0.7f),
                        singleLine = true,
                        placeholder = { Text("HH:mm") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (showReminderNote) {
                    Text(
                        text = "Se creara un evento con notificacion 15 min antes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, description, date, time) },
                enabled = title.isNotBlank() && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
                    && time.matches(Regex("\\d{1,2}:\\d{2}")),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// ── Helpers ─────────────────────────────────────────────────────────────────

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
        val display = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es"))
        display.format(date).replaceFirstChar { it.uppercase() }
    } catch (_: Exception) {
        dateStr
    }
}

private fun loadLatestSummary(context: Context): DailySummary? {
    val prefs = context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
    val json = prefs.getString("latest_summary", null) ?: return null
    return try {
        Json.decodeFromString<DailySummary>(json)
    } catch (_: Exception) {
        null
    }
}

private suspend fun generateSummaryNow(context: Context): DailySummary {
    val repository = DatabaseProvider.getRepository(context)
    val generator = SummaryGenerator(context)

    val cal = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dateStr = dateFormat.format(cal.time)

    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfDay = cal.timeInMillis

    cal.add(Calendar.DAY_OF_YEAR, 1)
    val endOfDay = cal.timeInMillis

    val entries = repository.byDateRange(startOfDay, endOfDay).first()
    val summary = generator.generate(entries, dateStr)

    // Save it
    val jsonStr = Json.encodeToString(summary)
    context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
        .edit()
        .putString("latest_summary", jsonStr)
        .apply()

    return summary
}
