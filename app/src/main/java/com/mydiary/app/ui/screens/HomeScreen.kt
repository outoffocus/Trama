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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mydiary.app.service.ServiceController
import com.mydiary.app.ui.DatabaseProvider
import com.mydiary.app.ui.components.CalendarBar
import com.mydiary.app.ui.components.EntryCard
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.Source
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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
    onSummaryClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val scope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var calendarExpanded by remember { mutableStateOf(false) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val serviceRunning by ServiceController.isRunning.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Selection mode
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    // Quick entry bar
    var quickEntryText by remember { mutableStateOf("") }
    var quickEntryExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("nota") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ServiceController.start(context)
        }
    }

    val allEntries by repository.getAll().collectAsState(initial = emptyList())

    val entries = allEntries
        .let { list ->
            if (selectedDate != null) list.filter { entry ->
                Instant.ofEpochMilli(entry.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate() == selectedDate
            }
            else list
        }

    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("es"))
    val grouped = entries.groupBy { dateFormat.format(Date(it.createdAt)) }

    // FAB animation
    val fabScale by animateFloatAsState(
        targetValue = if (serviceRunning) 1.1f else 1f,
        label = "fabScale"
    )
    val fabColor by animateColorAsState(
        targetValue = if (serviceRunning)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.primary,
        label = "fabColor"
    )

    // Function to handle batch delete with undo
    fun deleteSelected() {
        val idsToDelete = selectedIds.toList()
        val entriesToDelete = entries.filter { it.id in idsToDelete }
        val count = idsToDelete.size

        scope.launch {
            repository.deleteByIds(idsToDelete)
            selectionMode = false
            selectedIds = emptySet()

            val result = snackbarHostState.showSnackbar(
                message = "$count ${if (count == 1) "entrada borrada" else "entradas borradas"}",
                actionLabel = "DESHACER",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                // Undo: re-insert entries
                entriesToDelete.forEach { entry ->
                    repository.insert(entry.copy(id = 0))
                }
            }
        }
    }

    // Function to save manual entry
    fun saveManualEntry() {
        val text = quickEntryText.trim()
        if (text.isBlank()) return
        val cat = MANUAL_CATEGORIES.find { it.id == selectedCategory } ?: MANUAL_CATEGORIES.last()

        scope.launch {
            repository.insert(
                DiaryEntry(
                    text = text,
                    keyword = cat.id,
                    category = cat.label,
                    confidence = 1.0f,
                    source = Source.PHONE,
                    duration = 0,
                    isManual = true
                )
            )
            quickEntryText = ""
            quickEntryExpanded = false
            selectedCategory = "nota"
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                // Selection mode toolbar
                TopAppBar(
                    title = {
                        Text("${selectedIds.size} seleccionadas")
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar selección")
                        }
                    },
                    actions = {
                        // Select all
                        TextButton(onClick = {
                            selectedIds = entries.map { it.id }.toSet()
                        }) {
                            Text("Todo")
                        }
                        IconButton(
                            onClick = { deleteSelected() },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Borrar",
                                tint = if (selectedIds.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "MyDiary",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = onSummaryClick) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Resumen del dia",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = {
                        val hasAudioPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@FloatingActionButton
                        }

                        if (serviceRunning) {
                            ServiceController.stop(context)
                        } else {
                            ServiceController.start(context)
                        }
                    },
                    modifier = Modifier.scale(fabScale),
                    shape = CircleShape,
                    containerColor = fabColor,
                    contentColor = if (serviceRunning)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = if (serviceRunning) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (serviceRunning) "Detener escucha" else "Iniciar escucha",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Quick Entry Bar
            QuickEntryBar(
                text = quickEntryText,
                onTextChange = {
                    quickEntryText = it
                    if (it.isNotBlank()) quickEntryExpanded = true
                },
                expanded = quickEntryExpanded,
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                onSave = { saveManualEntry() },
                onCancel = {
                    quickEntryText = ""
                    quickEntryExpanded = false
                    selectedCategory = "nota"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            CalendarBar(
                entries = allEntries,
                selectedDate = selectedDate,
                expanded = calendarExpanded,
                currentMonth = currentMonth,
                onToggleExpanded = { calendarExpanded = !calendarExpanded },
                onDateSelected = { date ->
                    selectedDate = date
                    if (date != null) {
                        currentMonth = YearMonth.from(date)
                    }
                },
                onMonthChange = { currentMonth = it }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedDate != null)
                                "No hay entradas en esta fecha"
                            else
                                "No hay entradas aun",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedDate == null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Escribe una nota arriba o activa el micro",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (date, dateEntries) ->
                        item(key = "header_$date") {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(dateEntries, key = { it.id }) { entry ->
                            EntryCard(
                                entry = entry,
                                isSelectionMode = selectionMode,
                                isSelected = entry.id in selectedIds,
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = if (entry.id in selectedIds) {
                                            val newSet = selectedIds - entry.id
                                            if (newSet.isEmpty()) {
                                                selectionMode = false
                                            }
                                            newSet
                                        } else {
                                            selectedIds + entry.id
                                        }
                                    } else {
                                        onEntryClick(entry.id)
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedIds = setOf(entry.id)
                                    }
                                }
                            )
                        }
                        item(key = "spacer_$date") { Spacer(modifier = Modifier.height(4.dp)) }
                    }
                }
            }
        }
    }
}

// ── Quick Entry Bar ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickEntryBar(
    text: String,
    onTextChange: (String) -> Unit,
    expanded: Boolean,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            "Añadir nota...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = !expanded,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (text.isNotBlank()) onSave()
                    })
                )

                if (expanded && text.isNotBlank()) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancelar",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && text.isNotBlank(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Category chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MANUAL_CATEGORIES.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat.id,
                                onClick = { onCategorySelected(cat.id) },
                                label = {
                                    Text(
                                        "${cat.emoji} ${cat.label}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onCancel) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onSave,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Guardar")
                        }
                    }
                }
            }
        }
    }
}
