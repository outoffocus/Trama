package com.mydiary.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mydiary.app.service.ServiceController
import com.mydiary.app.ui.DatabaseProvider
import com.mydiary.app.ui.SettingsDataStore
import com.mydiary.app.ui.components.CalendarBar
import com.mydiary.app.ui.components.CategoryChip
import com.mydiary.app.ui.components.EntryCard
import com.mydiary.shared.model.CategoryInfo
import com.mydiary.shared.model.DiaryEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onEntryClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val settings = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var calendarExpanded by remember { mutableStateOf(false) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var serviceRunning by remember { mutableStateOf(ServiceController.isRunning(context)) }
    var entryToDelete by remember { mutableStateOf<DiaryEntry?>(null) }

    val categories by settings.categories.collectAsState(initial = CategoryInfo.DEFAULTS)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ServiceController.start(context)
            serviceRunning = true
        }
    }

    val allEntries by repository.getAll().collectAsState(initial = emptyList())

    val entries = allEntries
        .let { list ->
            if (selectedCategoryId != null) list.filter { it.category == selectedCategoryId }
            else list
        }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MyDiary") },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        },
        floatingActionButton = {
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
                    serviceRunning = !serviceRunning
                }
            ) {
                Icon(
                    imageVector = if (serviceRunning) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (serviceRunning) "Detener escucha" else "Iniciar escucha"
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            CalendarBar(
                entries = allEntries,
                categories = categories,
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

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    CategoryChip(
                        category = category,
                        selected = selectedCategoryId == category.id,
                        onClick = {
                            selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedDate != null || selectedCategoryId != null)
                            "No hay entradas con estos filtros."
                        else
                            "No hay entradas aún.\nDi una palabra clave para empezar.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (date, dateEntries) ->
                        item(key = "header_$date") {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(dateEntries, key = { it.id }) { entry ->
                            EntryCard(
                                entry = entry,
                                categories = categories,
                                onClick = { onEntryClick(entry.id) },
                                onLongClick = { entryToDelete = entry }
                            )
                        }
                        item(key = "spacer_$date") { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }

    entryToDelete?.let { entry ->
        val preview = if (entry.text.length > 50) entry.text.take(50) + "..." else entry.text
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Eliminar entrada") },
            text = { Text("¿Eliminar \"$preview\"?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteById(entry.id) }
                    entryToDelete = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
