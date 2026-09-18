package com.trama.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.EntryActionType
import com.trama.shared.model.EntryStatus
import com.trama.shared.model.Source
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MemoryFilter(val label: String) {
    ALL("Todo"),
    NOTES("Notas y tareas"),
    PLACES("Lugares"),
    MEETINGS("Reuniones")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onEntryClick: (Long) -> Unit,
    onPlaceClick: (Long) -> Unit,
    onRecordingClick: (Long) -> Unit,
    onAsk: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale("es")) }
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(MemoryFilter.ALL) }
    val queryTerms = remember(query) { SearchQuery.terms(query) }

    val allEntries by repository.getAll().collectAsState(initialValue = emptyList())
    val allPlaces by repository.getPlaces().collectAsState(initialValue = emptyList())
    val allRecordings by repository.getAllRecordings().collectAsState(initialValue = emptyList())

    val entryResultsState by (
        if (query.isNotBlank()) intersectingSearch(queryTerms, repository::search) { it.id }
        else flowOf(emptyList())
    ).collectAsState(initialValue = null)
    val placeResultsState by (
        if (query.isNotBlank()) intersectingSearch(queryTerms, repository::searchPlaces) { it.id }
        else flowOf(emptyList())
    ).collectAsState(initialValue = null)
    val recordingResultsState by (
        if (query.isNotBlank()) intersectingSearch(queryTerms, repository::searchRecordings) { it.id }
        else flowOf(emptyList())
    ).collectAsState(initialValue = null)
    val isSearching = query.isNotBlank() &&
        (entryResultsState == null || placeResultsState == null || recordingResultsState == null)
    val recentEntries = remember(allEntries) {
        val sourceEntriesWithDerivedAction = allEntries.mapNotNull { it.parentEntryId }.toSet()
        allEntries
            .asSequence()
            .filter { it.status != EntryStatus.DISCARDED }
            .filter { it.duplicateOfId == null }
            .filter { it.id !in sourceEntriesWithDerivedAction }
            .take(12)
            .toList()
    }
    val showingRecent = query.isBlank()
    val entries = (if (showingRecent) recentEntries else entryResultsState.orEmpty()).takeIf {
        selectedFilter == MemoryFilter.ALL || selectedFilter == MemoryFilter.NOTES
    }.orEmpty()
    val places = (if (showingRecent) allPlaces.take(8) else placeResultsState.orEmpty()).takeIf {
        selectedFilter == MemoryFilter.ALL || selectedFilter == MemoryFilter.PLACES
    }.orEmpty()
    val recordings = (if (showingRecent) allRecordings.take(8) else recordingResultsState.orEmpty()).takeIf {
        selectedFilter == MemoryFilter.ALL || selectedFilter == MemoryFilter.MEETINGS
    }.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recuerdos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(
                        onClick = { onAsk(query) }
                    ) {
                        Text(if (query.isBlank()) "Preguntar a Trama" else "Preguntar sobre esta búsqueda")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar lugares, reuniones o recuerdos") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MemoryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) }
                    )
                }
            }

            when {
                isSearching -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Buscando...",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                entries.isEmpty() && places.isEmpty() && recordings.isEmpty() -> {
                    SearchPlaceholder(
                        title = if (showingRecent) "Todavía no hay recuerdos" else "No se encontraron resultados",
                        subtitle = if (showingRecent) {
                            "Cuando guardes notas, lugares o reuniones aparecerán aquí."
                        } else {
                            "Prueba con otra palabra o una frase más corta."
                        }
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showingRecent) {
                        item("recent_header") { ResultHeader("Recientes") }
                    }
                    if (places.isNotEmpty()) {
                        item("places_header") { ResultHeader("Lugares") }
                    }
                    items(places, key = { "place_${it.id}" }) { place ->
                        ListItem(
                            headlineContent = { Text(place.name) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        place.locality,
                                        place.opinionSummary ?: place.type,
                                        dateFormat.format(Date(place.lastVisitAt ?: place.updatedAt))
                                    ).joinToString(" · ").ifBlank { "Lugar visitado" }
                                )
                            },
                            modifier = Modifier.clickable { onPlaceClick(place.id) }
                        )
                    }
                    if (recordings.isNotEmpty()) {
                        item("recordings_header") { ResultHeader("Reuniones y grabaciones") }
                    }
                    items(recordings, key = { "recording_${it.id}" }) { recording ->
                        ListItem(
                            headlineContent = { Text(recording.title ?: "Grabación") },
                            supportingContent = {
                                Text(
                                    listOf(
                                        dateFormat.format(Date(recording.createdAt)),
                                        sourceLabel(recording.source),
                                        recording.summary ?: recording.transcription
                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                    maxLines = 2
                                )
                            },
                            modifier = Modifier.clickable { onRecordingClick(recording.id) }
                        )
                    }
                    if (entries.isNotEmpty()) {
                        item("entries_header") { ResultHeader("Notas y tareas") }
                    }
                    items(entries, key = { "entry_${it.id}" }) { entry ->
                        ListItem(
                            overlineContent = {
                                Text(
                                    if (entry.status == EntryStatus.SUGGESTED) "Sugerida"
                                    else EntryActionType.label(entry.actionType)
                                )
                            },
                            headlineContent = {
                                Text(entry.displayText, maxLines = 2)
                            },
                            supportingContent = {
                                Text(
                                    "${dateFormat.format(Date(entry.createdAt))} · ${sourceLabel(entry.source)}"
                                )
                            },
                            modifier = Modifier.clickable { onEntryClick(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

private fun sourceLabel(source: Source): String = when (source) {
    Source.PHONE -> "Teléfono"
    Source.WATCH -> "Reloj"
    Source.SCREENSHOT -> "Captura de pantalla"
}

private fun <T, K> intersectingSearch(
    terms: List<String>,
    search: (String) -> Flow<List<T>>,
    key: (T) -> K
): Flow<List<T>> {
    if (terms.isEmpty()) return flowOf(emptyList())
    return combine(terms.map(search)) { batches ->
        SearchQuery.intersect(batches.toList(), key)
    }
}

@Composable
private fun ResultHeader(title: String) {
    Text(
        text = title,
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun SearchPlaceholder(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(title)
        Text(
            text = subtitle,
            modifier = Modifier.padding(top = 8.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
