package com.mydiary.app.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mydiary.app.speech.IntentPattern
import com.mydiary.app.speech.PersonalDictionary
import com.mydiary.app.speech.SpeakerEnrollment
import com.mydiary.app.summary.SummaryScheduler
import com.mydiary.app.ui.SettingsDataStore
import com.mydiary.app.ui.theme.CategoryColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context) }
    val dictionary = remember { PersonalDictionary(context) }
    val scope = rememberCoroutineScope()

    val duration by settings.recordingDuration.collectAsState(initial = SettingsDataStore.DEFAULT_DURATION)
    val autoStart by settings.autoStart.collectAsState(initial = false)
    val summaryEnabled by settings.summaryEnabled.collectAsState(initial = true)
    val summaryHour by settings.summaryHour.collectAsState(initial = SettingsDataStore.DEFAULT_SUMMARY_HOUR)
    val intentPatterns by settings.intentPatterns.collectAsState(initial = IntentPattern.DEFAULTS)
    val customKeywords by settings.customKeywords.collectAsState(initial = emptyList())
    val corrections by dictionary.corrections.collectAsState(initial = emptyList())
    val speakerEnrollment = remember { SpeakerEnrollment(context) }
    var speakerEnabled by remember { mutableStateOf(speakerEnrollment.isEnabled()) }
    var isEnrolled by remember { mutableStateOf(speakerEnrollment.isEnrolled()) }
    var enrollmentCount by remember { mutableStateOf(speakerEnrollment.getEnrollmentCount()) }
    var enrollmentStatus by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    val summaryPrefs = remember { context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE) }
    var geminiApiKey by remember { mutableStateOf(summaryPrefs.getString("gemini_api_key", "") ?: "") }

    var showAddKeywordDialog by remember { mutableStateOf(false) }
    var showAddPatternDialog by remember { mutableStateOf(false) }
    var editingPattern by remember { mutableStateOf<IntentPattern?>(null) }
    var showDeletePatternDialog by remember { mutableStateOf<IntentPattern?>(null) }
    // Test phrase
    var showTestDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Recording ────────────────────────────────────────────────
            SectionHeader("Grabacion")
            Text(
                "$duration segundos",
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

            Spacer(modifier = Modifier.height(16.dp))

            SettingToggle(
                title = "Inicio automatico",
                subtitle = "Iniciar al encender el dispositivo",
                checked = autoStart,
                onCheckedChange = { scope.launch { settings.setAutoStart(it) } }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Speaker Verification ─────────────────────────────────────
            SectionHeader("Verificacion de voz")
            Text(
                "Filtra voces de radio, TV y otras personas. Solo guarda notas que coincidan con tu voz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingToggle(
                title = "Activar verificacion",
                subtitle = if (isEnrolled) "Perfil de voz configurado" else "Necesita entrenamiento",
                checked = speakerEnabled,
                onCheckedChange = {
                    speakerEnabled = it
                    speakerEnrollment.setEnabled(it)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Enrollment status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isEnrolled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Status indicator
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isEnrolled)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEnrolled) "Perfil de voz listo" else "Sin perfil de voz",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isEnrolled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isEnrolled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Muestras: $enrollmentCount / ${SpeakerEnrollment.REQUIRED_SAMPLES}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Current enrollment phrase
                    if (!isEnrolled && enrollmentCount < SpeakerEnrollment.REQUIRED_SAMPLES) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "Di: \"${SpeakerEnrollment.ENROLLMENT_PHRASES[enrollmentCount]}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    enrollmentStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.contains("Error") || status.contains("No se"))
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Record / Enroll button
                        if (!isEnrolled) {
                            FilledTonalButton(
                                onClick = {
                                    if (!isRecording) {
                                        isRecording = true
                                        enrollmentStatus = "Grabando... habla ahora (5 seg)"
                                        speakerEnrollment.recordEnrollmentSample { result ->
                                            isRecording = false
                                            when (result) {
                                                is SpeakerEnrollment.EnrollmentResult.SampleRecorded -> {
                                                    enrollmentCount = result.current
                                                    enrollmentStatus = "Muestra ${result.current}/${result.required} grabada"
                                                }
                                                is SpeakerEnrollment.EnrollmentResult.Complete -> {
                                                    isEnrolled = true
                                                    enrollmentCount = result.totalSamples
                                                    enrollmentStatus = "Perfil de voz configurado correctamente"
                                                    speakerEnrollment.setEnabled(true)
                                                    speakerEnabled = true
                                                }
                                                is SpeakerEnrollment.EnrollmentResult.Error -> {
                                                    enrollmentStatus = result.message
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = !isRecording,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isRecording) "Grabando..." else "Grabar muestra")
                            }
                        }

                        // Re-train button (always available)
                        OutlinedButton(
                            onClick = {
                                speakerEnrollment.resetEnrollment()
                                isEnrolled = false
                                enrollmentCount = 0
                                enrollmentStatus = "Entrenamiento reiniciado"
                                speakerEnabled = false
                                speakerEnrollment.setEnabled(false)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isEnrolled) "Repetir entrenamiento" else "Reiniciar")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Summary ──────────────────────────────────────────────────
            SettingToggle(
                title = "Resumen diario",
                subtitle = "Resumen con IA y acciones sugeridas",
                checked = summaryEnabled,
                onCheckedChange = {
                    scope.launch {
                        settings.setSummaryEnabled(it)
                        if (it) SummaryScheduler.schedule(context, summaryHour)
                        else SummaryScheduler.cancel(context)
                    }
                }
            )

            if (summaryEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Hora: ${summaryHour}:00", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = summaryHour.toFloat(),
                    onValueChange = {
                        val h = it.roundToInt()
                        scope.launch {
                            settings.setSummaryHour(h)
                            SummaryScheduler.schedule(context, h)
                        }
                    },
                    valueRange = 6f..23f,
                    steps = 16,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = geminiApiKey,
                    onValueChange = {
                        geminiApiKey = it
                        summaryPrefs.edit().putString("gemini_api_key", it.trim()).apply()
                    },
                    label = { Text("Clave API Gemini") },
                    supportingText = { Text("Gratis en aistudio.google.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Intent Patterns ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader("Patrones de captura")
                    Text(
                        "Frases que activan la captura automatica. Toca para editar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Test + Add buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showTestDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Probar frase")
                }
                FilledTonalButton(
                    onClick = { showAddPatternDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nuevo patron")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            intentPatterns.forEachIndexed { index, pattern ->
                IntentPatternCard(
                    pattern = pattern,
                    colorIndex = index,
                    onToggle = { enabled ->
                        scope.launch {
                            settings.updatePattern(
                                pattern.copy(enabled = enabled),
                                intentPatterns
                            )
                        }
                    },
                    onEdit = { editingPattern = pattern },
                    onDelete = if (pattern.isCustom) {
                        { showDeletePatternDialog = pattern }
                    } else null
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Custom Keywords ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Palabras clave extra", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Frases simples no cubiertas por los patrones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { showAddKeywordDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (customKeywords.isEmpty()) {
                Text(
                    "Sin palabras clave extra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                customKeywords.forEach { keyword ->
                    KeywordChipRow(
                        keyword = keyword,
                        onDelete = {
                            scope.launch {
                                settings.setCustomKeywords(customKeywords.filter { it != keyword })
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Dictionary ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Diccionario personal", style = MaterialTheme.typography.titleMedium)
                if (corrections.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { dictionary.clearAll() } }) {
                        Text("Borrar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (corrections.isEmpty()) {
                Text(
                    "Edita una transcripcion para que aprenda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                corrections.forEach { correction ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "\"${correction.wrong}\" → \"${correction.correct}\"",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${correction.count}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { scope.launch { dictionary.removeCorrection(correction.wrong) } },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Eliminar",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    editingPattern?.let { pattern ->
        PatternEditDialog(
            pattern = pattern,
            onDismiss = { editingPattern = null },
            onSave = { updated ->
                scope.launch { settings.updatePattern(updated, intentPatterns) }
                editingPattern = null
            }
        )
    }

    showDeletePatternDialog?.let { pattern ->
        AlertDialog(
            onDismissRequest = { showDeletePatternDialog = null },
            title = { Text("Eliminar \"${pattern.label}\"?") },
            text = { Text("Se eliminara este patron y sus ${pattern.triggers.size} frases.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { settings.removePattern(pattern.id, intentPatterns) }
                    showDeletePatternDialog = null
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePatternDialog = null }) { Text("Cancelar") }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showAddPatternDialog) {
        NewPatternDialog(
            existingIds = intentPatterns.map { it.id }.toSet(),
            onDismiss = { showAddPatternDialog = false },
            onSave = { pattern ->
                scope.launch { settings.addPattern(pattern, intentPatterns) }
                showAddPatternDialog = false
            }
        )
    }

    if (showAddKeywordDialog) {
        AddKeywordDialog(
            existingKeywords = customKeywords.toSet(),
            onConfirm = { keyword ->
                scope.launch { settings.setCustomKeywords(customKeywords + keyword.trim().lowercase()) }
                showAddKeywordDialog = false
            },
            onDismiss = { showAddKeywordDialog = false }
        )
    }

    if (showTestDialog) {
        TestPhraseDialog(
            patterns = intentPatterns,
            customKeywords = customKeywords,
            onDismiss = { showTestDialog = false }
        )
    }
}

// ── Pattern Card ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntentPatternCard(
    pattern: IntentPattern,
    colorIndex: Int,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val accentColor = CategoryColors[colorIndex % CategoryColors.size]
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pattern.enabled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        )
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Label chip
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = if (pattern.enabled) 0.15f else 0.06f)
                ) {
                    Text(
                        text = pattern.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pattern.enabled) accentColor else accentColor.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Trigger count
                Text(
                    text = "${pattern.triggers.size} frases",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                if (pattern.isCustom) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "custom",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Expand/collapse
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Switch(
                    checked = pattern.enabled,
                    onCheckedChange = onToggle
                )
            }

            // Expanded: show triggers + edit button
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // Trigger chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        pattern.triggers.forEach { trigger ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "\"$trigger\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onEdit,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Editar")
                        }

                        onDelete?.let {
                            OutlinedButton(
                                onClick = it,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Eliminar", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Pattern Edit Dialog ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PatternEditDialog(
    pattern: IntentPattern,
    onDismiss: () -> Unit,
    onSave: (IntentPattern) -> Unit
) {
    var label by remember { mutableStateOf(pattern.label) }
    var triggers by remember { mutableStateOf(pattern.triggers) }
    var newTrigger by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar patron") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Label
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nombre del patron") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Current triggers
                Text(
                    "Frases de activacion (${triggers.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    triggers.forEach { trigger ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(trigger, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Eliminar",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            triggers = triggers.filter { it != trigger }
                                        }
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }

                // Add trigger
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTrigger,
                        onValueChange = { newTrigger = it },
                        label = { Text("Nueva frase") },
                        placeholder = { Text("ej: tendria que") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilledTonalButton(
                        onClick = {
                            val t = newTrigger.trim().lowercase()
                            if (t.isNotBlank() && t !in triggers) {
                                triggers = triggers + t
                                newTrigger = ""
                            }
                        },
                        enabled = newTrigger.trim().isNotBlank() &&
                            newTrigger.trim().lowercase() !in triggers,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir", modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(pattern.copy(
                        label = label.trim().ifBlank { pattern.label },
                        triggers = triggers
                    ))
                },
                enabled = triggers.isNotEmpty() && label.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// ── New Pattern Dialog ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewPatternDialog(
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (IntentPattern) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var triggers by remember { mutableStateOf<List<String>>(emptyList()) }
    var newTrigger by remember { mutableStateOf("") }

    val id = label.trim().lowercase()
        .replace("\\s+".toRegex(), "_")
        .replace("[^a-z0-9_]".toRegex(), "")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo patron") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nombre (ej: Trabajo, Salud...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (triggers.isNotEmpty()) {
                    Text(
                        "Frases de activacion (${triggers.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        triggers.forEach { trigger ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(trigger) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Eliminar",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { triggers = triggers.filter { it != trigger } }
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTrigger,
                        onValueChange = { newTrigger = it },
                        label = { Text("Frase de activacion") },
                        placeholder = { Text("ej: en el trabajo") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilledTonalButton(
                        onClick = {
                            val t = newTrigger.trim().lowercase()
                            if (t.isNotBlank() && t !in triggers) {
                                triggers = triggers + t
                                newTrigger = ""
                            }
                        },
                        enabled = newTrigger.trim().isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir", modifier = Modifier.size(20.dp))
                    }
                }

                if (triggers.isEmpty()) {
                    Text(
                        "Añade al menos una frase que active la captura",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(IntentPattern(
                        id = id.ifBlank { "custom_${System.currentTimeMillis()}" },
                        label = label.trim(),
                        triggers = triggers,
                        isCustom = true
                    ))
                },
                enabled = label.isNotBlank() && triggers.isNotEmpty() && id !in existingIds,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// ── Test Phrase Dialog ──────────────────────────────────────────────────────

@Composable
private fun TestPhraseDialog(
    patterns: List<IntentPattern>,
    customKeywords: List<String>,
    onDismiss: () -> Unit
) {
    var testPhrase by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    val detector = remember(patterns, customKeywords) {
        com.mydiary.app.speech.IntentDetector().apply {
            setPatterns(patterns)
            setCustomKeywords(customKeywords)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Probar frase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = testPhrase,
                    onValueChange = {
                        testPhrase = it
                        val detection = detector.detect(it)
                        result = if (detection != null) {
                            "Capturado por: ${detection.label}"
                        } else if (it.length >= 4) {
                            "No detectado"
                        } else null
                    },
                    label = { Text("Escribe una frase") },
                    placeholder = { Text("ej: tendria que llamar al medico") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                result?.let { res ->
                    val isMatch = res.startsWith("Capturado")
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMatch)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = res,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isMatch) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isMatch)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// ── Small Components ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun KeywordChipRow(keyword: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = keyword,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Eliminar",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AddKeywordDialog(
    existingKeywords: Set<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    val trimmed = keyword.trim().lowercase()
    val isDuplicate = trimmed in existingKeywords

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir palabra clave") },
        text = {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("Frase") },
                singleLine = true,
                isError = isDuplicate,
                supportingText = if (isDuplicate) {
                    { Text("Ya existe") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotBlank() && !isDuplicate
            ) { Text("Añadir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
