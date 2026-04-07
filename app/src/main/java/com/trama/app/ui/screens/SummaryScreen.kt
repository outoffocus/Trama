package com.trama.app.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trama.app.summary.ActionExecutor
import com.trama.app.summary.ActionType
import com.trama.app.summary.CalendarHelper
import com.trama.app.summary.DailySummary
import com.trama.app.summary.SuggestedAction
import com.trama.app.summary.SummaryGenerator
import com.trama.shared.data.DatabaseProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ── Urgency scoring ─────────────────────────────────────────────────────────

/**
 * Compute urgency score for sorting. Higher = more urgent.
 * Combines: explicit priority, due date proximity, and age of entry.
 */
private fun computeUrgency(action: SuggestedAction): Float {
    if (action.done) return -1f // done items always at bottom

    val now = System.currentTimeMillis()
    var score = 0f

    // 1. Due date proximity (parsed from datetime)
    val dueDateMillis = action.datetime?.let { parseDatetimeToMillis(it) }
    if (dueDateMillis != null) {
        val hoursUntilDue = (dueDateMillis - now).toFloat() / 3_600_000f
        score += when {
            hoursUntilDue < 0 -> 50f    // overdue
            hoursUntilDue < 4 -> 40f    // within 4 hours
            hoursUntilDue < 24 -> 30f   // today
            hoursUntilDue < 48 -> 20f   // tomorrow
            hoursUntilDue < 168 -> 10f  // this week
            else -> 0f
        }
    }

    // 2. Age of captured entry — older pending = more urgent
    val capturedAt = action.capturedAt
    if (capturedAt != null) {
        val daysSinceCaptured = (now - capturedAt).toFloat() / 86_400_000f
        score += when {
            daysSinceCaptured > 7 -> 25f   // over a week old — escalate
            daysSinceCaptured > 3 -> 15f   // stale
            daysSinceCaptured > 1 -> 5f    // yesterday
            else -> 0f
        }
    }

    // 3. Action type inherent urgency
    score += when (action.type) {
        ActionType.CALENDAR_EVENT -> 8f
        ActionType.CALL -> 6f
        ActionType.REMINDER -> 5f
        ActionType.MESSAGE -> 4f
        ActionType.TODO -> 2f
        ActionType.NOTE -> 0f
    }

    return score
}

private fun parseDatetimeToMillis(datetime: String): Long? {
    return try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).parse(datetime)?.time
    } catch (_: Exception) {
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(datetime)?.time
        } catch (_: Exception) { null }
    }
}

// ── Section grouping ────────────────────────────────────────────────────────

private enum class ActionSection(val label: String, val emoji: String) {
    OVERDUE("Atrasadas", "🔴"),
    TODAY("Para hoy", "🟡"),
    THIS_WEEK("Esta semana", "📅"),
    LATER("Pendientes", "📋"),
    DONE("Completadas", "✅")
}

private fun sectionFor(action: SuggestedAction): ActionSection {
    if (action.done) return ActionSection.DONE

    val now = System.currentTimeMillis()
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
    }.timeInMillis
    val endOfWeek = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 7)
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
    }.timeInMillis

    // Check due date from datetime
    val dueDateMillis = action.datetime?.let { parseDatetimeToMillis(it) }
    if (dueDateMillis != null) {
        return when {
            dueDateMillis < now -> ActionSection.OVERDUE
            dueDateMillis <= today -> ActionSection.TODAY
            dueDateMillis <= endOfWeek -> ActionSection.THIS_WEEK
            else -> ActionSection.LATER
        }
    }

    // Check age — captured more than 3 days ago with no date → treat as overdue-ish
    val capturedAt = action.capturedAt
    if (capturedAt != null) {
        val daysSince = (now - capturedAt) / 86_400_000
        if (daysSince > 3) return ActionSection.OVERDUE
        if (daysSince > 1) return ActionSection.TODAY
    }

    // Default: today (keep it visible and urgent)
    return ActionSection.TODAY
}

// ── Main Screen ─────────────────────────────────────────────────────────────

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

    // Load saved summary and enrich with capture timestamps if missing
    LaunchedEffect(Unit) {
        val loaded = loadSummary(context)
        if (loaded != null) {
            val needsEnrich = loaded.actions.any { it.capturedAt == null }
            if (needsEnrich) {
                val allEntries = repository.getPending().first() +
                    repository.getCompleted().first()
                val entryMap = allEntries.associateBy { it.id }
                val enriched = loaded.copy(
                    actions = loaded.actions.map { action ->
                        if (action.capturedAt != null) return@map action
                        if (action.entryIds.isNotEmpty()) {
                            val ts = action.entryIds.firstNotNullOfOrNull { entryMap[it]?.createdAt }
                            action.copy(capturedAt = ts)
                        } else {
                            val matched = allEntries.filter { entry ->
                                val aw = action.title.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
                                val ew = entry.displayText.lowercase().split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
                                aw.isNotEmpty() && ew.isNotEmpty() &&
                                    aw.intersect(ew).size.toFloat() / aw.size >= 0.4f
                            }.minByOrNull { it.createdAt }
                            if (matched != null) action.copy(capturedAt = matched.createdAt, entryIds = listOf(matched.id))
                            else action
                        }
                    }
                )
                saveSummary(context, enriched)
                summary = enriched
            } else {
                summary = loaded
            }
        }
    }

    // ── Action handlers ─────────────────────────────────────────────────────

    fun updateAction(index: Int, transform: (SuggestedAction) -> SuggestedAction) {
        summary = summary?.let { s ->
            val newActions = s.actions.toMutableList()
            newActions[index] = transform(newActions[index])
            s.copy(actions = newActions).also { saveSummary(context, it) }
        }
    }

    fun markDone(index: Int, done: Boolean) {
        val action = summary?.actions?.getOrNull(index) ?: return
        updateAction(index) { it.copy(done = done) }
        if (action.entryIds.isNotEmpty()) {
            scope.launch {
                action.entryIds.forEach { id ->
                    if (done) repository.markCompleted(id) else repository.markPending(id)
                }
            }
        }
    }

    fun openCalendarDialog(index: Int, action: SuggestedAction) {
        if (CalendarHelper.hasWriteCalendarPermission(context)) {
            editingAction = IndexedAction(index, action)
        } else {
            pendingAction = IndexedAction(index, action)
            calendarPermissionLauncher.launch(arrayOf(
                Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR
            ))
        }
    }

    fun executeAction(index: Int, action: SuggestedAction) {
        if (action.type == ActionType.CALENDAR_EVENT || action.type == ActionType.REMINDER) {
            openCalendarDialog(index, action)
        } else {
            // Only open the relevant app (dialer, messaging, etc.)
            // User marks done manually via swipe or ✅ button
            ActionExecutor.execute(context, action)
        }
    }

    fun postponeAction(index: Int) {
        val action = summary?.actions?.getOrNull(index) ?: return
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(tomorrow.time)
        val existingTime = action.datetime?.substringAfter("T", "09:00") ?: "09:00"
        updateAction(index) { it.copy(datetime = "${tomorrowStr}T${existingTime}") }
        android.widget.Toast.makeText(context, "Pospuesto a mañana", android.widget.Toast.LENGTH_SHORT).show()
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
                        if (isReminder) "Recordatorio creado" else "Evento añadido",
                        android.widget.Toast.LENGTH_SHORT).show()
                    markDone(ia.index, true)
                } else {
                    android.widget.Toast.makeText(context, "Error, abriendo calendario...",
                        android.widget.Toast.LENGTH_SHORT).show()
                    ActionExecutor.execute(context, updatedAction)
                }
                editingAction = null
            }
        )
    }

    // ── UI ───────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null,
                            modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Acciones")
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
                        Text("Sin acciones", style = MaterialTheme.typography.titleMedium)
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
                // Sort by urgency and group into sections
                val sortedActions = s.actions
                    .mapIndexed { idx, action -> idx to action }
                    .sortedByDescending { computeUrgency(it.second) }

                val grouped = sortedActions.groupBy { sectionFor(it.second) }
                val sectionOrder = listOf(
                    ActionSection.OVERDUE, ActionSection.TODAY,
                    ActionSection.THIS_WEEK, ActionSection.LATER, ActionSection.DONE
                )

                // Header stats
                val pendingCount = s.actions.count { !it.done }
                val doneCount = s.actions.count { it.done }

                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = formatDate(s.date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$pendingCount pendientes · $doneCount completadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (section in sectionOrder) {
                        val items = grouped[section] ?: continue
                        // Don't show empty sections
                        if (items.isEmpty()) continue

                        // Section header
                        item(key = "header_${section.name}") {
                            SectionHeader(
                                section = section,
                                count = items.size
                            )
                        }

                        // Action cards
                        items(
                            items = items,
                            key = { "action_${it.first}" }
                        ) { (originalIndex, action) ->
                            SwipeableActionCard(
                                action = action,
                                onExecute = { executeAction(originalIndex, action) },
                                onToggleDone = { markDone(originalIndex, !action.done) },
                                onPostpone = { postponeAction(originalIndex) },
                                onAddToCalendar = { openCalendarDialog(originalIndex, action) }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ── Section Header ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(section: ActionSection, count: Int) {
    val color = when (section) {
        ActionSection.OVERDUE -> MaterialTheme.colorScheme.error
        ActionSection.TODAY -> MaterialTheme.colorScheme.primary
        ActionSection.THIS_WEEK -> MaterialTheme.colorScheme.tertiary
        ActionSection.LATER -> MaterialTheme.colorScheme.onSurfaceVariant
        ActionSection.DONE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${section.emoji} ${section.label}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

// ── Swipeable Action Card ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableActionCard(
    action: SuggestedAction,
    onExecute: () -> Unit,
    onToggleDone: () -> Unit,
    onPostpone: () -> Unit,
    onAddToCalendar: () -> Unit
) {
    if (action.done) {
        // Done items: simple card, no swipe
        ActionCardContent(
            action = action,
            onExecute = {},
            onToggleDone = onToggleDone,
            onAddToCalendar = {},
            showQuickActions = false
        )
        return
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onPostpone()
                    false // reset position
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggleDone()
                    false // reset position
                }
                else -> false
            }
        },
        positionalThreshold = { it * 0.35f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection

            // Swipe right → Done (green)
            // Swipe left → Postpone (amber)
            val (bgColor, icon, label, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeInfo(
                    Color(0xFF2E7D32), Icons.Default.CheckCircle, "Hecho", Alignment.CenterStart
                )
                SwipeToDismissBoxValue.EndToStart -> SwipeInfo(
                    Color(0xFFE65100), Icons.Default.Schedule, "Mañana", Alignment.CenterEnd
                )
                else -> SwipeInfo(Color.Transparent, Icons.Default.CheckCircle, "", Alignment.CenterStart)
            }

            val animatedColor by animateColorAsState(
                targetValue = bgColor,
                animationSpec = tween(150),
                label = "swipeBg"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(animatedColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (direction == SwipeToDismissBoxValue.EndToStart) {
                            Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
                        if (direction == SwipeToDismissBoxValue.StartToEnd) {
                            Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        ActionCardContent(
            action = action,
            onExecute = onExecute,
            onToggleDone = onToggleDone,
            onAddToCalendar = onAddToCalendar,
            showQuickActions = true
        )
    }
}

private data class SwipeInfo(
    val color: Color,
    val icon: ImageVector,
    val label: String,
    val alignment: Alignment
)

// ── Action Card Content ─────────────────────────────────────────────────────

@Composable
private fun ActionCardContent(
    action: SuggestedAction,
    onExecute: () -> Unit,
    onToggleDone: () -> Unit,
    onAddToCalendar: () -> Unit,
    showQuickActions: Boolean
) {
    val icon = actionIcon(action.type)
    val accentColor = actionColor(action.type)
    val label = actionLabel(action.type)
    val isDone = action.done

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (!isDone) onExecute() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 12.dp, bottom = if (showQuickActions) 4.dp else 12.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Surface(
                    shape = CircleShape,
                    color = if (isDone) accentColor.copy(alpha = 0.06f) else accentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (isDone) Icons.Default.CheckCircle else icon,
                            contentDescription = null,
                            tint = if (isDone) MaterialTheme.colorScheme.tertiary else accentColor,
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
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                    )
                    if (action.description.isNotBlank()) {
                        Text(
                            text = action.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDone) 0.4f else 0.7f)
                        )
                    }
                    // Metadata line
                    val metaParts = mutableListOf<String>()
                    action.datetime?.let { metaParts.add(formatActionDatetime(it)) }
                    action.contact?.let { metaParts.add(it) }
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDone) 0.4f else 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Type badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = if (isDone) 0.05f else 0.08f)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor.copy(alpha = if (isDone) 0.4f else 0.8f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Swipe hint + calendar shortcut
            if (showQuickActions && !isDone) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 62.dp, end = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "← mañana · hecho →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(1f)
                    )

                    // Calendar shortcut (only for non-calendar actions)
                    if (action.type != ActionType.CALENDAR_EVENT && action.type != ActionType.REMINDER) {
                        IconButton(
                            onClick = onAddToCalendar,
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Añadir al calendario",
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
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
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))

                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2, shape = RoundedCornerShape(12.dp))

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

/** Format "2026-03-25" → "25 mar 2026" for display */
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
    ActionType.NOTE -> Icons.Default.TaskAlt // Notes are now actionable tasks
}

@Composable
private fun actionColor(type: ActionType): Color = when (type) {
    ActionType.CALENDAR_EVENT -> MaterialTheme.colorScheme.tertiary
    ActionType.REMINDER -> MaterialTheme.colorScheme.error
    ActionType.TODO -> MaterialTheme.colorScheme.primary
    ActionType.MESSAGE -> MaterialTheme.colorScheme.secondary
    ActionType.CALL -> MaterialTheme.colorScheme.tertiary
    ActionType.NOTE -> MaterialTheme.colorScheme.primary
}

private fun actionLabel(type: ActionType): String = when (type) {
    ActionType.CALENDAR_EVENT -> "Calendario"
    ActionType.REMINDER -> "Recordar"
    ActionType.TODO -> "Tarea"
    ActionType.MESSAGE -> "Mensaje"
    ActionType.CALL -> "Llamar"
    ActionType.NOTE -> "Tarea" // Notes display as tasks now
}

/**
 * Formats a timestamp as a subtle relative/absolute string.
 * Today → "14:32", yesterday → "ayer 14:32", older → "23 mar 14:32"
 */
/**
 * Format an ISO datetime string (e.g. "2026-03-30T16:00:00") into readable Spanish text.
 * Examples: "Hoy 16:00", "Mañana 09:00", "Lun 5 abr", "5 abr 16:00"
 */
private fun formatActionDatetime(datetime: String): String {
    return try {
        // Parse — support both "yyyy-MM-dd'T'HH:mm:ss" and "yyyy-MM-dd'T'HH:mm" and "yyyy-MM-dd"
        val hasTime = datetime.contains("T")
        val parsed = when {
            datetime.length >= 19 -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(datetime)
            hasTime -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).parse(datetime)
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(datetime)
        } ?: return datetime

        val cal = Calendar.getInstance().apply { time = parsed }
        val now = Calendar.getInstance()
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val nextWeek = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }

        val timePart = if (hasTime) SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsed) else null
        val calDay = Calendar.getInstance().apply { time = parsed; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }

        val datePart = when {
            calDay.timeInMillis == today.timeInMillis -> "Hoy"
            calDay.timeInMillis == tomorrow.timeInMillis -> "Mañana"
            calDay.before(nextWeek) -> SimpleDateFormat("EEE", Locale("es")).format(parsed).replaceFirstChar { it.uppercase() }
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> SimpleDateFormat("d MMM", Locale("es")).format(parsed)
            else -> SimpleDateFormat("d MMM yyyy", Locale("es")).format(parsed)
        }

        if (timePart != null) "$datePart $timePart" else datePart
    } catch (_: Exception) {
        datetime.replace("T", " ").take(16)
    }
}

private fun formatRelativeTime(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val time = SimpleDateFormat("H:mm", Locale("es")).format(millis)

    val nowDay = now.get(Calendar.DAY_OF_YEAR) + now.get(Calendar.YEAR) * 365
    val thenDay = then.get(Calendar.DAY_OF_YEAR) + then.get(Calendar.YEAR) * 365
    val diff = nowDay - thenDay

    return when {
        diff == 0 -> time
        diff == 1 -> "ayer $time"
        diff < 7 -> {
            val day = SimpleDateFormat("EEE", Locale("es")).format(millis)
            "$day $time"
        }
        else -> {
            val date = SimpleDateFormat("d MMM", Locale("es")).format(millis)
            "$date $time"
        }
    }
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
