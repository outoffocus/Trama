package com.mydiary.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.mydiary.app.ui.SettingsDataStore
import com.mydiary.app.ui.theme.HighlightColor
import com.mydiary.app.ui.theme.NoteColor
import com.mydiary.app.ui.theme.ReminderColor
import com.mydiary.app.ui.theme.TodoColor
import com.mydiary.shared.model.Category
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()

    val duration by settings.recordingDuration.collectAsState(initial = SettingsDataStore.DEFAULT_DURATION)
    val autoStart by settings.autoStart.collectAsState(initial = false)
    val mappings by settings.keywordMappings.collectAsState(initial = emptyMap())

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Recording duration ---
            Text("Duración de grabación", style = MaterialTheme.typography.titleMedium)
            Text(
                "$duration segundos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = duration.toFloat(),
                onValueChange = { scope.launch { settings.setRecordingDuration(it.roundToInt()) } },
                valueRange = 5f..30f,
                steps = 24,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Auto-start ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Inicio automático", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Iniciar el servicio al encender el dispositivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoStart,
                    onCheckedChange = { scope.launch { settings.setAutoStart(it) } }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Keywords & Categories ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Palabras clave", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadir")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (mappings.isEmpty()) {
                Text(
                    "No hay palabras clave configuradas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                mappings.forEach { (keyword, category) ->
                    KeywordMappingCard(
                        keyword = keyword,
                        category = category,
                        onCategoryChange = { newCategory ->
                            scope.launch {
                                val updated = mappings.toMutableMap()
                                updated[keyword] = newCategory
                                settings.setKeywordMappings(updated)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                val updated = mappings.toMutableMap()
                                updated.remove(keyword)
                                settings.setKeywordMappings(updated)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Category overview ---
            Text("Categorías", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Category.entries.forEach { category ->
                val categoryKeywords = mappings.filter { it.value == category }.keys
                CategoryOverviewCard(
                    category = category,
                    keywords = categoryKeywords.toList()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    if (showAddDialog) {
        AddKeywordDialog(
            existingKeywords = mappings.keys,
            onConfirm = { keyword, category ->
                scope.launch {
                    val updated = mappings.toMutableMap()
                    updated[keyword.trim().lowercase()] = category
                    settings.setKeywordMappings(updated)
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeywordMappingCard(
    keyword: String,
    category: Category,
    onCategoryChange: (Category) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = categoryLabel(category),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    Category.entries.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(categoryLabel(cat)) },
                            onClick = {
                                onCategoryChange(cat)
                                expanded = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CategoryOverviewCard(category: Category, keywords: List<String>) {
    val color = when (category) {
        Category.TODO -> TodoColor
        Category.REMINDER -> ReminderColor
        Category.HIGHLIGHT -> HighlightColor
        Category.NOTE -> NoteColor
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryLabel(category),
                style = MaterialTheme.typography.titleSmall,
                color = color,
                modifier = Modifier.width(110.dp)
            )
            if (keywords.isEmpty()) {
                Text(
                    text = "Sin palabras clave",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = keywords.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddKeywordDialog(
    existingKeywords: Set<String>,
    onConfirm: (String, Category) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(Category.NOTE) }
    var expanded by remember { mutableStateOf(false) }
    val trimmedKeyword = keyword.trim().lowercase()
    val isDuplicate = trimmedKeyword in existingKeywords

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir palabra clave") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Palabra clave") },
                    singleLine = true,
                    isError = isDuplicate,
                    supportingText = if (isDuplicate) {
                        { Text("Esta palabra clave ya existe") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = categoryLabel(selectedCategory),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        Category.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(categoryLabel(cat)) },
                                onClick = {
                                    selectedCategory = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedKeyword, selectedCategory) },
                enabled = trimmedKeyword.isNotBlank() && !isDuplicate
            ) {
                Text("Añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun categoryLabel(category: Category): String = when (category) {
    Category.TODO -> "Por hacer"
    Category.REMINDER -> "Recordatorio"
    Category.HIGHLIGHT -> "Destacado"
    Category.NOTE -> "Nota"
}
