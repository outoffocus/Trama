package com.trama.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trama.app.service.EntryProcessingState
import com.trama.shared.data.DatabaseProvider
import com.trama.app.ui.SettingsDataStore
import com.trama.app.ui.components.EntryCard
import com.trama.app.ui.theme.timelineAccentColor
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onEntryClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val settings = remember { SettingsDataStore(context) }
    var query by remember { mutableStateOf("") }

    val resultsState by (
        if (query.length >= 2) repository.search(query)
        else flowOf(emptyList())
    ).collectAsState(initial = null)
    val processingEntryIds by EntryProcessingState.processingIds.collectAsState()
    val processingBackends by EntryProcessingState.processingBackends.collectAsState()
    val pendingColorIndex by settings.timelineColorPending.collectAsState(
        initial = SettingsDataStore.DEFAULT_TIMELINE_COLOR_PENDING
    )
    val pendingAccent = remember(pendingColorIndex) { timelineAccentColor(pendingColorIndex) }

    val isSearching = query.length >= 2 && resultsState == null
    val results = resultsState ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                placeholder = { Text("Buscar en entradas...") },
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
                query.length < 2 -> {
                    SearchPlaceholder(
                        title = "Empieza a escribir",
                        subtitle = "Busca por texto capturado, acciones o lugares mencionados."
                    )
                }
                results.isEmpty() -> {
                    SearchPlaceholder(
                        title = "No se encontraron resultados",
                        subtitle = "Prueba con otra palabra o una frase más corta."
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            accentColor = pendingAccent,
                            isProcessing = entry.id in processingEntryIds,
                            processingBackend = processingBackends[entry.id],
                            onClick = { onEntryClick(entry.id) }
                        )
                    }
                }
            }
        }
    }
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
