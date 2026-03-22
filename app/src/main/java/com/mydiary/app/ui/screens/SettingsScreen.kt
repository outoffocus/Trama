package com.mydiary.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mydiary.app.speech.PersonalDictionary
import com.mydiary.app.ui.DatabaseProvider
import com.mydiary.app.ui.SettingsDataStore
import com.mydiary.shared.model.CategoryInfo
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context) }
    val repository = remember { DatabaseProvider.getRepository(context) }
    val dictionary = remember { PersonalDictionary(context) }
    val scope = rememberCoroutineScope()

    val duration by settings.recordingDuration.collectAsState(initial = SettingsDataStore.DEFAULT_DURATION)
    val autoStart by settings.autoStart.collectAsState(initial = false)
    val categories by settings.categories.collectAsState(initial = CategoryInfo.DEFAULTS)
    val mappings by settings.keywordMappings.collectAsState(initial = emptyMap())
    val corrections by dictionary.corrections.collectAsState(initial = emptyList())
    val geminiEnabled by settings.geminiEnabled.collectAsState(initial = false)

    var showAddKeywordDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CategoryInfo?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryInfo?>(null) }

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
            Text("Duración máxima de grabación", style = MaterialTheme.typography.titleMedium)
            Text(
                "$duration segundos (se detiene antes al dejar de hablar)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = duration.toFloat(),
                onValueChange = { scope.launch { settings.setRecordingDuration(it.roundToInt()) } },
                valueRange = 5f..60f,
                steps = 10,
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

            // --- Gemini Nano ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gemini Nano", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "IA on-device: categoriza automáticamente y corrige texto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = geminiEnabled,
                    onCheckedChange = { scope.launch { settings.setGeminiEnabled(it) } }
                )
            }
            if (geminiEnabled) {
                Text(
                    "Reinicia el servicio de escucha para aplicar cambios",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Categories ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Categorías", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { showAddCategoryDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            categories.forEach { category ->
                val catKeywords = mappings.filter { it.value == category.id }.keys.toList()
                CategoryCard(
                    category = category,
                    keywords = catKeywords,
                    canDelete = categories.size > 1,
                    onEdit = { categoryToEdit = category },
                    onDelete = { categoryToDelete = category }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Keywords ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Palabras clave", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { showAddKeywordDialog = true }) {
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
                mappings.forEach { (keyword, categoryId) ->
                    KeywordMappingCard(
                        keyword = keyword,
                        categoryId = categoryId,
                        categories = categories,
                        onCategoryChange = { newCatId ->
                            scope.launch {
                                val updated = mappings.toMutableMap()
                                updated[keyword] = newCatId
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

            // --- Personal Dictionary ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Diccionario personal", style = MaterialTheme.typography.titleMedium)
                if (corrections.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { dictionary.clearAll() } }) {
                        Text("Borrar todo", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Text(
                "Correcciones aprendidas al editar transcripciones",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (corrections.isEmpty()) {
                Text(
                    "Sin correcciones. Edita una entrada transcrita para que aprenda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                corrections.forEach { correction ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "\"${correction.wrong}\" → \"${correction.correct}\"",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Visto ${correction.count}×",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                scope.launch { dictionary.removeCorrection(correction.wrong) }
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // --- Dialogs ---

    if (showAddKeywordDialog) {
        AddKeywordDialog(
            existingKeywords = mappings.keys,
            categories = categories,
            onConfirm = { keyword, categoryId ->
                scope.launch {
                    val updated = mappings.toMutableMap()
                    updated[keyword.trim().lowercase()] = categoryId
                    settings.setKeywordMappings(updated)
                }
                showAddKeywordDialog = false
            },
            onDismiss = { showAddKeywordDialog = false }
        )
    }

    if (showAddCategoryDialog) {
        CategoryEditDialog(
            initial = null,
            existingIds = categories.map { it.id }.toSet(),
            onConfirm = { newCat ->
                scope.launch {
                    settings.setCategories(categories + newCat)
                }
                showAddCategoryDialog = false
            },
            onDismiss = { showAddCategoryDialog = false }
        )
    }

    categoryToEdit?.let { cat ->
        CategoryEditDialog(
            initial = cat,
            existingIds = categories.map { it.id }.toSet(),
            onConfirm = { updated ->
                scope.launch {
                    val newList = categories.map { if (it.id == cat.id) updated else it }
                    settings.setCategories(newList)
                }
                categoryToEdit = null
            },
            onDismiss = { categoryToEdit = null }
        )
    }

    categoryToDelete?.let { cat ->
        val fallback = categories.first { it.id != cat.id }
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Eliminar categoría") },
            text = {
                Text("Se eliminará \"${cat.label}\". Las entradas y palabras clave asignadas pasarán a \"${fallback.label}\".")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        // Reassign entries in DB
                        repository.reassignCategory(cat.id, fallback.id)
                        // Reassign keyword mappings
                        val updatedMappings = mappings.mapValues {
                            if (it.value == cat.id) fallback.id else it.value
                        }
                        settings.setKeywordMappings(updatedMappings)
                        // Remove category
                        settings.setCategories(categories.filter { it.id != cat.id })
                    }
                    categoryToDelete = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun CategoryCard(
    category: CategoryInfo,
    keywords: List<String>,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = Color(category.colorHex.toLong(16))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = color
                )
                if (keywords.isNotEmpty()) {
                    Text(
                        text = keywords.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditDialog(
    initial: CategoryInfo?,
    existingIds: Set<String>,
    onConfirm: (CategoryInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val isEdit = initial != null
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var selectedColor by remember { mutableStateOf(initial?.colorHex ?: CategoryInfo.PRESET_COLORS.first()) }
    val id = initial?.id ?: label.trim().uppercase().replace(" ", "_")
    val isDuplicate = !isEdit && id in existingIds

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Editar categoría" else "Nueva categoría") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    isError = isDuplicate,
                    supportingText = if (isDuplicate) {
                        { Text("Ya existe una categoría con este nombre") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Color", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryInfo.PRESET_COLORS.forEach { colorHex ->
                        val c = Color(colorHex.toLong(16))
                        val isSelected = colorHex == selectedColor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(CategoryInfo(id = id, label = label.trim(), colorHex = selectedColor))
                },
                enabled = label.trim().isNotBlank() && !isDuplicate
            ) {
                Text(if (isEdit) "Guardar" else "Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeywordMappingCard(
    keyword: String,
    categoryId: String,
    categories: List<CategoryInfo>,
    onCategoryChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val catInfo = categories.find { it.id == categoryId }
    val catLabel = catInfo?.label ?: categoryId

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
                    value = catLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.label) },
                            onClick = {
                                onCategoryChange(cat.id)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddKeywordDialog(
    existingKeywords: Set<String>,
    categories: List<CategoryInfo>,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.lastOrNull()?.id ?: "NOTE") }
    var expanded by remember { mutableStateOf(false) }
    val trimmedKeyword = keyword.trim().lowercase()
    val isDuplicate = trimmedKeyword in existingKeywords
    val selectedLabel = categories.find { it.id == selectedCategoryId }?.label ?: selectedCategoryId

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
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label) },
                                onClick = {
                                    selectedCategoryId = cat.id
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
                onClick = { onConfirm(trimmedKeyword, selectedCategoryId) },
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
