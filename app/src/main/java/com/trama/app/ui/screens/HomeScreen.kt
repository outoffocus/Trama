// TODO: Extract repository access into a ViewModel. Currently DatabaseProvider.getRepository(context)
//  is called directly inside the composable via remember {}. A ViewModel would provide proper
//  lifecycle-aware state management and enable testability. Requires dependency injection setup (Hilt).
package com.trama.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.trama.app.service.RecordingState
import com.trama.app.service.EntryProcessingState
import com.trama.app.service.ServiceController
import com.trama.app.sync.PhoneToWatchSyncer
import com.trama.shared.data.DatabaseProvider
import com.trama.app.ui.components.EntryCard
import com.trama.app.ui.components.RecordingCard
import com.trama.app.ui.components.RecordingIndicatorBar
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import com.trama.shared.model.StatusSyncEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onCalendarClick: () -> Unit = {},
    onRecordingClick: (Long) -> Unit = {},
    onRecordingsListClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val watchSyncer = remember { PhoneToWatchSyncer(context, repository) }
    val scope = rememberCoroutineScope()

    // Full sync to watch on first open — ensures watch has current statuses
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            watchSyncer.syncAllToWatch()
        }
    }
    val serviceRunning by ServiceController.isRunning.collectAsState()
    val watchActive by ServiceController.isWatchActive.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recording state
    val isRecording by RecordingState.isRecording.collectAsState()
    val recElapsed by RecordingState.elapsedSeconds.collectAsState()
    val savedRecordingId by RecordingState.savedRecordingId.collectAsState()
    val lastError by RecordingState.lastError.collectAsState()
    val processingEntryIds by EntryProcessingState.processingIds.collectAsState()

    // Navigate to recording detail when saved
    LaunchedEffect(savedRecordingId) {
        val id = savedRecordingId
        if (id != null) {
            RecordingState.clearSaved()
            onRecordingClick(id)
        }
    }

    // Show processing errors as snackbar
    LaunchedEffect(lastError) {
        val error = lastError
        if (error != null) {
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            RecordingState.clearError()
        }
    }

    // Selection mode (entries)
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    // Selection mode (recordings)
    var recSelectionMode by remember { mutableStateOf(false) }
    var selectedRecIds by remember { mutableStateOf(setOf<Long>()) }

    // Add manual entry dialog
    var showAddDialog by remember { mutableStateOf(false) }

    // Show/hide completed
    var showCompleted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ServiceController.start(context)
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) RecordingState.startRecording(context)
    }

    // Data
    val allPendingEntries by repository.getPending().collectAsState(initial = emptyList())
    val duplicateEntries by repository.getDuplicates().collectAsState(initial = emptyList())
    val completedEntries by repository.getCompleted().collectAsState(initial = emptyList())
    val recordings by repository.getAllRecordings().collectAsState(initial = emptyList())

    // Filter duplicates out of the main pending list
    val duplicateIds = duplicateEntries.map { it.id }.toSet()
    val pendingEntries = allPendingEntries.filter { it.id !in duplicateIds }

    // Stats
    val startOfDay = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val completedToday = completedEntries.count { (it.completedAt ?: 0) >= startOfDay }

    // Group pending — overdue means due date is from a PREVIOUS day, not today
    val overdueEntries = pendingEntries.filter { entry ->
        val due = entry.dueDate
        due != null && due < startOfDay
    }
    val overdueIds = overdueEntries.map { it.id }.toSet()
    val todayEntries = pendingEntries.filter { it.createdAt >= startOfDay && it.id !in overdueIds }
    val olderEntries = pendingEntries.filter { entry ->
        entry.createdAt < startOfDay && entry.id !in overdueIds
    }

    val dateFormat = SimpleDateFormat("dd MMM", Locale("es"))

    // FAB colors
    val teal = Color(0xFF00897B)
    val recRed = Color(0xFFD32F2F)

    // Helper: sync a completed entry to watch
    fun syncCompleted(entry: DiaryEntry) {
        scope.launch {
            watchSyncer.syncStatusChange(
                completed = listOf(StatusSyncEntry(entry.createdAt, entry.text))
            )
        }
    }

    // Helper: sync a deleted entry to watch
    fun syncDeleted(entry: DiaryEntry) {
        scope.launch {
            watchSyncer.syncStatusChange(
                deleted = listOf(StatusSyncEntry(entry.createdAt, entry.text))
            )
        }
    }

    // Delete selected
    fun deleteSelected() {
        val idsToDelete = selectedIds.toList()
        val entriesToDelete = (pendingEntries + completedEntries).filter { it.id in idsToDelete }
        val count = idsToDelete.size
        scope.launch {
            repository.deleteByIds(idsToDelete)
            // Sync deletions to watch
            watchSyncer.syncStatusChange(
                deleted = entriesToDelete.map { StatusSyncEntry(it.createdAt, it.text) }
            )
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
        val entriesToComplete = (pendingEntries + completedEntries).filter { it.id in ids }
        scope.launch {
            repository.markCompletedByIds(ids)
            // Sync completions to watch
            watchSyncer.syncStatusChange(
                completed = entriesToComplete.map { StatusSyncEntry(it.createdAt, it.text) }
            )
            selectionMode = false; selectedIds = emptySet()
            snackbarHostState.showSnackbar("${ids.size} completadas")
        }
    }

    // Delete selected recordings
    fun deleteSelectedRecordings() {
        val idsToDelete = selectedRecIds.toList()
        val count = idsToDelete.size
        scope.launch {
            repository.deleteRecordingsByIds(idsToDelete)
            recSelectionMode = false; selectedRecIds = emptySet()
            snackbarHostState.showSnackbar(
                "$count ${if (count == 1) "grabación borrada" else "grabaciones borradas"}"
            )
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
            } else if (recSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedRecIds.size} grabaciones") },
                    navigationIcon = {
                        IconButton(onClick = { recSelectionMode = false; selectedRecIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            selectedRecIds = recordings.map { it.id }.toSet()
                        }) { Text("Todas") }
                        IconButton(onClick = { deleteSelectedRecordings() }, enabled = selectedRecIds.isNotEmpty()) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar",
                                tint = if (selectedRecIds.isNotEmpty()) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text("Trama", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        // Add note
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Añadir nota")
                        }
                        // Actions badge
                        IconButton(onClick = onSummaryClick) {
                            Box {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Acciones",
                                    tint = MaterialTheme.colorScheme.primary)
                                if (pendingEntries.isNotEmpty()) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .align(Alignment.TopEnd)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Text(
                                                text = if (pendingEntries.size > 9) "9+" else "${pendingEntries.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onError
                                            )
                                        }
                                    }
                                }
                            }
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
            if (!selectionMode && !recSelectionMode) {
                val watchBlue = Color(0xFF1E88E5)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Keyword toggle (top)
                    SmallFloatingActionButton(
                        onClick = {
                            if (watchActive) return@SmallFloatingActionButton
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@SmallFloatingActionButton
                            }
                            if (serviceRunning) ServiceController.stop(context)
                            else ServiceController.start(context)
                        },
                        shape = CircleShape,
                        containerColor = if (serviceRunning) teal
                                         else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (serviceRunning) Color.White
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(
                            imageVector = if (serviceRunning) Icons.Default.Mic
                                          else Icons.Default.MicOff,
                            contentDescription = if (serviceRunning) "Detener escucha" else "Escuchar",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 2. Record toggle (middle)
                    SmallFloatingActionButton(
                        onClick = {
                            if (watchActive) return@SmallFloatingActionButton
                            if (isRecording) {
                                RecordingState.stopRecording(context)
                            } else {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    ServiceController.startRecording(context)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = if (isRecording) recRed
                                         else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isRecording) Color.White else recRed
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop
                                          else Icons.Default.FiberManualRecord,
                            contentDescription = if (isRecording) "Parar grabación" else "Grabar",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3. Transfer (bottom, primary)
                    FloatingActionButton(
                        onClick = {
                            if (watchActive) {
                                // Take back from watch — start keyword locally
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@FloatingActionButton
                                }
                                ServiceController.start(context)
                            } else if (serviceRunning || isRecording) {
                                // Transfer active mode to watch
                                ServiceController.transferToWatch(context)
                            }
                        },
                        shape = CircleShape,
                        containerColor = if (watchActive) watchBlue
                                         else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (watchActive) Color.White
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Default.Watch,
                            contentDescription = if (watchActive) "Traer de vuelta" else "Transferir al reloj",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Recording indicator bar (visible while recording)
            AnimatedVisibility(
                visible = isRecording,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                RecordingIndicatorBar(
                    elapsedSeconds = recElapsed,
                    onStop = { RecordingState.stopRecording(context) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

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

            if (pendingEntries.isEmpty() && completedEntries.isEmpty() && recordings.isEmpty()) {
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
                    // Possible duplicates (top for visibility)
                    if (duplicateEntries.isNotEmpty()) {
                        item(key = "header_duplicates") {
                            SectionHeader("Posibles duplicados", duplicateEntries.size,
                                MaterialTheme.colorScheme.tertiary)
                        }
                        items(duplicateEntries, key = { "dup_${it.id}" }) { entry ->
                            val originalEntry = allPendingEntries.find { it.id == entry.duplicateOfId }
                            DuplicateCard(
                                entry = entry,
                                originalText = originalEntry?.displayText,
                                onKeep = { scope.launch { repository.clearDuplicate(entry.id) } },
                                onDelete = { scope.launch { repository.deleteById(entry.id); syncDeleted(entry) } }
                            )
                        }
                    }

                    // Overdue
                    if (overdueEntries.isNotEmpty()) {
                        item(key = "header_overdue") {
                            SectionHeader("Vencidas", overdueEntries.size, MaterialTheme.colorScheme.error)
                        }
                        items(overdueEntries, key = { "overdue_${it.id}" }) { entry ->
                            EntryCardItem(entry, selectionMode, selectedIds, onEntryClick,
                                entry.id in processingEntryIds,
                                { scope.launch { repository.markCompleted(entry.id); syncCompleted(entry) } },
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
                                entry.id in processingEntryIds,
                                { scope.launch { repository.markCompleted(entry.id); syncCompleted(entry) } },
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
                                    entry.id in processingEntryIds,
                                    { scope.launch { repository.markCompleted(entry.id); syncCompleted(entry) } },
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
                                    entry.id in processingEntryIds,
                                    { scope.launch { repository.markPending(entry.id) } },
                                    { id, sel -> selectedIds = if (sel) selectedIds + id else selectedIds - id; if (selectedIds.isEmpty()) selectionMode = false },
                                    { selectionMode = true; selectedIds = setOf(entry.id) })
                            }
                        }
                    }

                    // ── Recordings (compact: last 3) ──
                    if (recordings.isNotEmpty()) {
                        item(key = "header_recordings") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Grabaciones", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${recordings.size}", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                        items(recordings.take(3), key = { "rec_${it.id}" }) { recording ->
                            RecordingCard(
                                recording = recording,
                                isSelectionMode = recSelectionMode,
                                isSelected = recording.id in selectedRecIds,
                                onClick = {
                                    if (recSelectionMode) {
                                        selectedRecIds = if (recording.id in selectedRecIds)
                                            selectedRecIds - recording.id else selectedRecIds + recording.id
                                        if (selectedRecIds.isEmpty()) recSelectionMode = false
                                    } else {
                                        onRecordingClick(recording.id)
                                    }
                                },
                                onLongClick = {
                                    if (!recSelectionMode) {
                                        recSelectionMode = true
                                        selectedRecIds = setOf(recording.id)
                                    }
                                }
                            )
                        }
                        if (recordings.size > 3) {
                            item(key = "ver_todas_rec") {
                                TextButton(
                                    onClick = onRecordingsListClick,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Ver todas (${recordings.size})")
                                }
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
    isProcessing: Boolean,
    onToggleComplete: () -> Unit,
    onSelectionChange: (Long, Boolean) -> Unit,
    onEnterSelectionMode: () -> Unit,
) {
    EntryCard(
        entry = entry,
        isSelectionMode = selectionMode,
        isSelected = entry.id in selectedIds,
        isProcessing = isProcessing,
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

// ── Duplicate Card ──────────────────────────────────────────────────────────

@Composable
private fun DuplicateCard(
    entry: DiaryEntry,
    originalText: String?,
    onKeep: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = entry.displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (originalText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Similar a: $originalText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onKeep) {
                    Text("No es duplicado")
                }
            }
        }
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
