package com.mydiary.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydiary.app.backup.AutoBackupWorker
import com.mydiary.app.backup.BackupManager
import com.mydiary.app.backup.BackupScheduler
import com.mydiary.app.speech.IntentPattern
import com.mydiary.app.speech.SpeakerEnrollment
import com.mydiary.app.summary.SummaryScheduler
import com.mydiary.app.ui.DatabaseProvider
import com.mydiary.app.ui.SettingsDataStore
import com.mydiary.app.ui.theme.CategoryColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    val repository = remember { DatabaseProvider.getRepository(context) }

    // Settings state
    val autoStart by settings.autoStart.collectAsState(initial = false)
    val duration by settings.recordingDuration.collectAsState(initial = SettingsDataStore.DEFAULT_DURATION)
    val summaryEnabled by settings.summaryEnabled.collectAsState(initial = true)
    val summaryHour by settings.summaryHour.collectAsState(initial = SettingsDataStore.DEFAULT_SUMMARY_HOUR)
    val intentPatterns by settings.intentPatterns.collectAsState(initial = IntentPattern.DEFAULTS)

    // Gemini API key
    val summaryPrefs = remember { context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE) }
    var geminiApiKey by remember { mutableStateOf(summaryPrefs.getString("gemini_api_key", "") ?: "") }

    // Speaker
    val speakerEnrollment = remember { SpeakerEnrollment(context) }
    var speakerEnabled by remember { mutableStateOf(speakerEnrollment.isEnabled()) }
    var isEnrolled by remember { mutableStateOf(speakerEnrollment.isEnrolled()) }

    // Backup
    val backupEnabled by settings.backupEnabled.collectAsState(initial = true)
    val backupHour by settings.backupHour.collectAsState(initial = SettingsDataStore.DEFAULT_BACKUP_HOUR)

    // Sections expanded state
    var patternsExpanded by remember { mutableStateOf(false) }

    // Dialogs
    var editingPattern by remember { mutableStateOf<IntentPattern?>(null) }
    var showAddPatternDialog by remember { mutableStateOf(false) }
    var showDeletePatternDialog by remember { mutableStateOf<IntentPattern?>(null) }
    var showTestDialog by remember { mutableStateOf(false) }

    // Backup state
    var backupInProgress by remember { mutableStateOf(false) }
    var backupLocationName by remember { mutableStateOf(AutoBackupWorker.getBackupFileName(context)) }

    // SAF launchers — CreateDocument works with Google Drive (OpenDocumentTree doesn't)
    val backupFileSetupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // Persist permission so WorkManager can overwrite this file later
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { context.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
        // Get display name
        val name = try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null }
        AutoBackupWorker.setBackupFile(context, uri, name ?: "mydiary-backup.json")
        backupLocationName = name ?: "mydiary-backup.json"
        // Trigger immediate backup so the file isn't empty
        AutoBackupWorker.runNow(context)
        Toast.makeText(context, "Backup configurado — guardando ahora...", Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            backupInProgress = true
            try {
                val count = BackupManager.exportToUri(context, uri, repository)
                Toast.makeText(context, "$count entradas exportadas", Toast.LENGTH_SHORT).show()
                context.getSharedPreferences("backup", Context.MODE_PRIVATE)
                    .edit().putLong("last_backup", System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            backupInProgress = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            backupInProgress = true
            try {
                val (imported, skipped) = BackupManager.importFromUri(context, uri, repository)
                Toast.makeText(context, "$imported importadas, $skipped duplicadas", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            backupInProgress = false
        }
    }

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

            // ═══════════════════════════════════════════════════════════════
            // GENERAL
            // ═══════════════════════════════════════════════════════════════
            SectionHeader("General")

            SettingToggle(
                title = "Inicio automatico",
                subtitle = "Iniciar al encender el dispositivo",
                checked = autoStart,
                onCheckedChange = { scope.launch { settings.setAutoStart(it) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Duracion de escucha", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                Text("${duration}s", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = duration.toFloat(),
                onValueChange = { scope.launch { settings.setRecordingDuration(it.roundToInt()) } },
                valueRange = 5f..60f, steps = 10,
                modifier = Modifier.fillMaxWidth()
            )

            SectionDivider()

            // ═══════════════════════════════════════════════════════════════
            // IA Y RESUMEN
            // ═══════════════════════════════════════════════════════════════
            SectionHeader("IA y resumen")

            SettingToggle(
                title = "Resumen diario",
                subtitle = "Genera acciones sugeridas con Gemini",
                checked = summaryEnabled,
                onCheckedChange = {
                    scope.launch {
                        settings.setSummaryEnabled(it)
                        if (it) SummaryScheduler.schedule(context, summaryHour)
                        else SummaryScheduler.cancel(context)
                    }
                }
            )

            AnimatedVisibility(visible = summaryEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hora del resumen", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f))
                        Text("${summaryHour}:00", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = summaryHour.toFloat(),
                        onValueChange = {
                            val h = it.roundToInt()
                            scope.launch {
                                settings.setSummaryHour(h)
                                SummaryScheduler.schedule(context, h)
                            }
                        },
                        valueRange = 6f..23f, steps = 16,
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
            }

            SectionDivider()

            // ═══════════════════════════════════════════════════════════════
            // VOZ
            // ═══════════════════════════════════════════════════════════════
            SectionHeader("Verificacion de voz")

            SettingToggle(
                title = if (isEnrolled) "Activada" else "Desactivada",
                subtitle = if (isEnrolled) "Solo guarda notas con tu voz"
                           else "Registra tu voz para filtrar otras personas",
                checked = speakerEnabled && isEnrolled,
                onCheckedChange = {
                    speakerEnabled = it
                    speakerEnrollment.setEnabled(it)
                }
            )

            if (isEnrolled) {
                TextButton(onClick = {
                    speakerEnrollment.resetEnrollment()
                    isEnrolled = false
                    speakerEnabled = false
                    speakerEnrollment.setEnabled(false)
                }) {
                    Text("Repetir entrenamiento", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "El entrenamiento se realiza desde la pantalla principal " +
                    "(en una futura version).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionDivider()

            // ═══════════════════════════════════════════════════════════════
            // BACKUP
            // ═══════════════════════════════════════════════════════════════
            SectionHeader("Copia de seguridad")

            SettingToggle(
                title = "Backup automatico diario",
                subtitle = "Guarda en la carpeta Descargas",
                checked = backupEnabled,
                onCheckedChange = {
                    scope.launch {
                        settings.setBackupEnabled(it)
                        if (it) BackupScheduler.schedule(context, backupHour)
                        else BackupScheduler.cancel(context)
                    }
                }
            )

            AnimatedVisibility(visible = backupEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // File location picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ubicacion", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                backupLocationName ?: "No configurada",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (backupLocationName != null) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.error
                            )
                        }
                        FilledTonalButton(
                            onClick = { backupFileSetupLauncher.launch("mydiary-backup.json") },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(if (backupLocationName != null) "Cambiar" else "Elegir") }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Hour slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hora", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f))
                        Text("${backupHour}:00", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = backupHour.toFloat(),
                        onValueChange = {
                            val h = it.roundToInt()
                            scope.launch {
                                settings.setBackupHour(h)
                                BackupScheduler.schedule(context, h)
                            }
                        },
                        valueRange = 0f..23f, steps = 22,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val backupPrefs = remember { context.getSharedPreferences("backup", Context.MODE_PRIVATE) }
            val lastBackup = remember { backupPrefs.getLong("last_backup", 0L) }
            val lastCount = remember { backupPrefs.getInt("last_backup_count", 0) }
            if (lastBackup > 0) {
                val dateStr = java.text.SimpleDateFormat("d MMM yyyy, HH:mm",
                    java.util.Locale("es")).format(lastBackup)
                Text(
                    "Ultimo: $dateStr ($lastCount entradas)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (backupLocationName != null) {
                    FilledTonalButton(
                        onClick = {
                            AutoBackupWorker.runNow(context)
                            Toast.makeText(context, "Backup iniciado...", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !backupInProgress,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup ahora")
                    }
                }

                OutlinedButton(
                    onClick = { exportLauncher.launch(BackupManager.getBackupFileName()) },
                    enabled = !backupInProgress,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exportar")
                }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    enabled = !backupInProgress,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importar")
                }
            }

            Text(
                "Exportar permite guardar manualmente en otra ubicacion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            SectionDivider()

            // ═══════════════════════════════════════════════════════════════
            // PATRONES (collapsible)
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { patternsExpanded = !patternsExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Patrones de captura",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "${intentPatterns.count { it.enabled }} activos de ${intentPatterns.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (patternsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = patternsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Test + Add buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showTestDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Probar frase") }
                        FilledTonalButton(
                            onClick = { showAddPatternDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nuevo")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    intentPatterns.forEachIndexed { index, pattern ->
                        IntentPatternCard(
                            pattern = pattern,
                            colorIndex = index,
                            onToggle = { enabled ->
                                scope.launch { settings.updatePattern(pattern.copy(enabled = enabled), intentPatterns) }
                            },
                            onEdit = { editingPattern = pattern },
                            onDelete = if (pattern.isCustom) { { showDeletePatternDialog = pattern } } else null
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
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

    if (showTestDialog) {
        TestPhraseDialog(
            patterns = intentPatterns,
            onDismiss = { showTestDialog = false }
        )
    }
}

// ── Section Components ──────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
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
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                Text("${pattern.triggers.size}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(checked = pattern.enabled, onCheckedChange = onToggle)
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 10.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        pattern.triggers.forEach { trigger ->
                            Surface(shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("\"$trigger\"", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("Editar")
                        }
                        onDelete?.let {
                            OutlinedButton(onClick = it, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.Delete, null, Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(4.dp))
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
                OutlinedTextField(value = label, onValueChange = { label = it },
                    label = { Text("Nombre") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                Text("Frases (${triggers.size})", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    triggers.forEach { trigger ->
                        InputChip(
                            selected = false, onClick = {},
                            label = { Text(trigger, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, "Eliminar",
                                    modifier = Modifier.size(16.dp).clickable {
                                        triggers = triggers.filter { it != trigger }
                                    })
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newTrigger, onValueChange = { newTrigger = it },
                        label = { Text("Nueva frase") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    FilledTonalButton(
                        onClick = {
                            val t = newTrigger.trim().lowercase()
                            if (t.isNotBlank() && t !in triggers) { triggers = triggers + t; newTrigger = "" }
                        },
                        enabled = newTrigger.trim().isNotBlank() && newTrigger.trim().lowercase() !in triggers,
                        shape = RoundedCornerShape(10.dp)
                    ) { Icon(Icons.Default.Add, null, Modifier.size(20.dp)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(pattern.copy(label = label.trim().ifBlank { pattern.label }, triggers = triggers)) },
                enabled = triggers.isNotEmpty() && label.isNotBlank(), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
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
    val id = label.trim().lowercase().replace("\\s+".toRegex(), "_").replace("[^a-z0-9_]".toRegex(), "")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo patron") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it },
                    label = { Text("Nombre (ej: Trabajo, Salud...)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                if (triggers.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        triggers.forEach { trigger ->
                            InputChip(selected = false, onClick = {},
                                label = { Text(trigger) },
                                trailingIcon = { Icon(Icons.Default.Close, "Eliminar",
                                    modifier = Modifier.size(16.dp).clickable {
                                        triggers = triggers.filter { it != trigger }
                                    }) },
                                shape = RoundedCornerShape(8.dp))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newTrigger, onValueChange = { newTrigger = it },
                        label = { Text("Frase de activacion") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    FilledTonalButton(
                        onClick = {
                            val t = newTrigger.trim().lowercase()
                            if (t.isNotBlank() && t !in triggers) { triggers = triggers + t; newTrigger = "" }
                        },
                        enabled = newTrigger.trim().isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) { Icon(Icons.Default.Add, null, Modifier.size(20.dp)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(IntentPattern(
                    id = id.ifBlank { "custom_${System.currentTimeMillis()}" },
                    label = label.trim(), triggers = triggers, isCustom = true
                ))
            }, enabled = label.isNotBlank() && triggers.isNotEmpty() && id !in existingIds,
                shape = RoundedCornerShape(10.dp)) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        shape = RoundedCornerShape(24.dp)
    )
}

// ── Test Phrase Dialog ──────────────────────────────────────────────────────

@Composable
private fun TestPhraseDialog(
    patterns: List<IntentPattern>,
    onDismiss: () -> Unit
) {
    var testPhrase by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    val detector = remember(patterns) {
        com.mydiary.app.speech.IntentDetector().apply { setPatterns(patterns) }
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
                        result = if (detection != null) "Capturado por: ${detection.label}"
                                 else if (it.length >= 4) "No detectado" else null
                    },
                    label = { Text("Escribe una frase") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                result?.let { res ->
                    val isMatch = res.startsWith("Capturado")
                    Surface(shape = RoundedCornerShape(10.dp),
                        color = if (isMatch) MaterialTheme.colorScheme.primaryContainer
                               else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)) {
                        Text(res, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isMatch) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isMatch) MaterialTheme.colorScheme.onPrimaryContainer
                                   else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        shape = RoundedCornerShape(24.dp)
    )
}
