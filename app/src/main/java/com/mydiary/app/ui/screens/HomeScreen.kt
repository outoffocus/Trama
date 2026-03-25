package com.mydiary.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mydiary.app.service.ServiceController
import com.mydiary.app.ui.DatabaseProvider
import com.mydiary.app.ui.components.EntryCard
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.EntryStatus
import com.mydiary.shared.model.Source
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Categories available for manual entry.
 */
private data class EntryCategory(
    val id: String,
    val emoji: String,
    val label: String
)

private val MANUAL_CATEGORIES = listOf(
    EntryCategory("pendiente", "\uD83D\uDCCB", "Pendiente"),
    EntryCategory("idea", "\uD83D\uDCA1", "Idea"),
    EntryCategory("compra", "\uD83D\uDED2", "Compra"),
    EntryCategory("gasto", "\uD83D\uDCB0", "Gasto"),
    EntryCategory("llamada", "\uD83D\uDCDE", "Llamada"),
    EntryCategory("nota", "\uD83D\uDCDD", "Nota")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onEntryClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSummaryClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val scope = rememberCoroutineScope()
    val serviceRunning by ServiceController.isRunning.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Selection mode
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    // Add manual entry dialog
    var showAddDialog by remember { mutableStateOf(false) }

    // Show/hide completed
    var showCompleted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ServiceController.start(context)
    }

    // Data
    val pendingEntries by repository.getPending().collectAsState(initial = emptyList())
    val completedEntries by repository.getCompleted().collectAsState(initial = emptyList())

    // Stats
    val startOfDay = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val completedToday = completedEntries.count { (it.completedAt ?: 0) >= startOfDay }

    // Group pending
    val now = System.currentTimeMillis()
    val todayEntries = pendingEntries.filter { it.createdAt >= startOfDay }
    val overdueEntries = pendingEntries.filter { entry ->
        val due = entry.dueDate
        due != null && due < now
    }
    val olderEntries = pendingEntries.filter { entry ->
        val due = entry.dueDate
        entry.createdAt < startOfDay && (due == null || due >= now)
    }

    val dateFormat = SimpleDateFormat("dd MMM", Locale("es"))

    // FAB animation
    val fabScale by animateFloatAsState(
        targetValue = if (serviceRunning) 1.1f else 1f, label = "fabScale"
    )
    val fabColor by animateColorAsState(
        targetValue = if (serviceRunning) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.primary, label = "fabColor"
    )

    // Delete selected
    fun deleteSelected() {
        val idsToDelete = selectedIds.toList()
        val entriesToDelete = (pendingEntries + completedEntries).filter { it.id in idsToDelete }
        val count = idsToDelete.size
        scope.launch {
            repository.deleteByIds(idsToDelete)
            selectionMode = false; selectedIds = emptySet()
            val result = snackbarHostState.showSnackbar(
                "$count ${if (count == 1) "entrada borrada" else "entradas borradas"}",
                actionLabel = "DESHACER", duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                entriesToDelete.forEach { repository.insert(it.copy(id = 0)) }
            }
        }
    }

    fun completeSelected() {
        val ids = selectedIds.toList()
        scope.launch {
            repository.markCompletedByIds(ids)
            selectionMode = false; selectedIds = emptySet()
            snackbarHostState.showSnackbar("${ids.size} completadas")
        }
    }

    // Add manual entry dialog
    if (showAddDialog) {
        AddEntryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { text, categoryId ->
                val cat = MANUAL_CATEGORIES.find { it.id == categoryId } ?: MANUAL_CATEGORIES.last()
                scope.launch {
                    repository.insert(
                        DiaryEntry(
                            text = text,
                            keyword = cat.id,
                            category = cat.label,
                            confidence = 1.0f,
                            source = Source.PHONE,
                            duration = 0,
                            isManual = true,
                            cleanText = text
                        )
                    )
                }
                showAddDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} seleccionadas") },
                    navigationIcon = {
                        IconButton(onClick = { selectionMode = false; selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            selectedIds = pendingEntries.map { it.id }.toSet()
                        }) { Text("Todo") }
                        IconButton(onClick = { completeSelected() }, enabled = selectedIds.isNotEmpty()) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Completar",
                                tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { deleteSelected() }, enabled = selectedIds.isNotEmpty()) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar",
                                tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text("MyDiary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        IconButton(onClick = onCalendarClick) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Calendario")
                        }
                        IconButton(onClick = onSummaryClick) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Resumen",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!selectionMode) {
                // Stacked FABs: + on top, mic on bottom
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // + button (add manual entry)
                    SmallFloatingActionButton(
                        onClick = { showAddDialog = true },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir tarea", modifier = Modifier.size(20.dp))
                    }

                    // Mic button (start/stop listening)
                    FloatingActionButton(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@FloatingActionButton
                            }
                            if (serviceRunning) ServiceController.stop(context)
                            else ServiceController.start(context)
                        },
                        modifier = Modifier.scale(fabScale),
                        shape = CircleShape,
                        containerColor = fabColor,
                        contentColor = if (serviceRunning) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = if (serviceRunning) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (serviceRunning) "Detener" else "Escuchar",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Stats bar
            if (pendingEntries.isNotEmpty() || completedToday > 0) {
                StatsBar(
                    pendingCount = pendingEntries.size,
                    completedToday = completedToday,
                    overdueCount = overdueEntries.size,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            if (pendingEntries.isEmpty() && completedEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Edit, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No hay tareas pendientes",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Toca + para añadir una o activa el micro",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Overdue
                    if (overdueEntries.isNotEmpty()) {
                        item(key = "header_overdue") {
                            SectionHeader("Vencidas", overdueEntries.size, MaterialTheme.colorScheme.error)
                        }
                        items(overdueEntries, key = { "overdue_${it.id}" }) { entry ->
                            EntryCardItem(entry, selectionMode, selectedIds, onEntryClick,
                                { scope.launch { repository.markCompleted(entry.id) } },
                                { id, sel -> selectedIds = if (sel) selectedIds + id else selectedIds - id; if (selectedIds.isEmpty()) selectionMode = false },
                                { selectionMode = true; selectedIds = setOf(entry.id) })
                        }
                    }

                    // Today
                    if (todayEntries.isNotEmpty()) {
                        item(key = "header_today") {
                            SectionHeader("Hoy", todayEntries.size, MaterialTheme.colorScheme.primary)
                        }
                        items(todayEntries, key = { "today_${it.id}" }) { entry ->
                            EntryCardItem(entry, selectionMode, selectedIds, onEntryClick,
                                { scope.launch { repository.markCompleted(entry.id) } },
                                { id, sel -> selectedIds = if (sel) selectedIds + id else selectedIds - id; if (selectedIds.isEmpty()) selectionMode = false },
                                { selectionMode = true; selectedIds = setOf(entry.id) })
                        }
                    }

                    // Older pending
                    if (olderEntries.isNotEmpty()) {
                        item(key = "header_older") {
                            SectionHeader("Pendientes anteriores", olderEntries.size, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val grouped = olderEntries.groupBy { dateFormat.format(Date(it.createdAt)) }
                        grouped.forEach { (date, dateEntries) ->
                            item(key = "sub_$date") {
                                Text(date, style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                            }
                            items(dateEntries, key = { "older_${it.id}" }) { entry ->
                                EntryCardItem(entry, selectionMode, selectedIds, onEntryClick,
                                    { scope.launch { repository.markCompleted(entry.id) } },
                                    { id, sel -> selectedIds = if (sel) selectedIds + id else selectedIds - id; if (selectedIds.isEmpty()) selectionMode = false },
                                    { selectionMode = true; selectedIds = setOf(entry.id) })
                            }
                        }
                    }

                    // Completed (collapsible)
                    if (completedEntries.isNotEmpty()) {
                        item(key = "header_completed") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Completadas", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${completedEntries.size}", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { showCompleted = !showCompleted }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        if (showCompleted) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null, modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                        if (showCompleted) {
                            items(completedEntries.take(20), key = { "done_${it.id}" }) { entry ->
                                EntryCardItem(entry, selectionMode, selectedIds, onEntryClick,
                                    { scope.launch { repository.markPending(entry.id) } },
                                    { id, sel -> selectedIds = if (sel) selectedIds + id else selectedIds - id; if (selectedIds.isEmpty()) selectionMode = false },
                                    { selectionMode = true; selectedIds = setOf(entry.id) })
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }
        }
    }
}

// ── Entry Card Item wrapper ──────────────────────────────────────────────────

@Composable
private fun EntryCardItem(
    entry: DiaryEntry,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onEntryClick: (Long) -> Unit,
    onToggleComplete: () -> Unit,
    onSelectionChange: (Long, Boolean) -> Unit,
    onEnterSelectionMode: () -> Unit
) {
    EntryCard(
        entry = entry,
        isSelectionMode = selectionMode,
        isSelected = entry.id in selectedIds,
        onToggleComplete = onToggleComplete,
        onClick = {
            if (selectionMode) onSelectionChange(entry.id, entry.id !in selectedIds)
            else onEntryClick(entry.id)
        },
        onLongClick = { if (!selectionMode) onEnterSelectionMode() }
    )
}

// ── Section Header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.6f))
    }
}

// ── Stats Bar ────────────────────────────────────────────────────────────────

@Composable
private fun StatsBar(pendingCount: Int, completedToday: Int, overdueCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatChip("Pendientes", "$pendingCount", MaterialTheme.colorScheme.primary)
        if (completedToday > 0) StatChip("Hoy", "\u2713 $completedToday", MaterialTheme.colorScheme.tertiary)
        if (overdueCount > 0) StatChip("Vencidas", "$overdueCount", MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun StatChip(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

// ── Add Entry Dialog ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddEntryDialog(
    onDismiss: () -> Unit,
    onSave: (text: String, categoryId: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("nota") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva tarea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Descripcion") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MANUAL_CATEGORIES.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.id,
                            onClick = { selectedCategory = cat.id },
                            label = { Text("${cat.emoji} ${cat.label}", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text.trim(), selectedCategory) },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
