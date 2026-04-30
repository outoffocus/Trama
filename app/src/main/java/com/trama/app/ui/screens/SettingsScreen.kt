package com.trama.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.trama.app.diagnostics.CaptureLog
import com.trama.app.diagnostics.DiagnosticsExportManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.trama.app.audio.OfflineDictationCapture
import com.trama.app.backup.AutoBackupWorker
import com.trama.app.backup.BackupManager
import com.trama.app.backup.BackupScheduler
import com.trama.app.service.LocationDebugState
import com.trama.app.speech.speaker.ProfileDispersion
import com.trama.app.speech.speaker.SherpaSpeakerVerificationManager
import com.trama.app.speech.speaker.SpeakerEnrollmentStep
import com.trama.app.speech.speaker.VerificationDiagnostic
import com.trama.app.service.ServiceController
import com.trama.app.speech.IntentPattern
import com.trama.app.summary.CalendarHelper
import com.trama.app.summary.GemmaClient
import com.trama.app.summary.GoogleCalendarSyncManager
import com.trama.app.summary.GemmaModelManager
import com.trama.app.summary.PromptTemplateStore
import com.trama.app.summary.SummaryScheduler
import com.trama.app.ui.SettingsDataStore
import com.trama.app.ui.theme.CategoryColors
import com.trama.app.ui.theme.TimelineAccentPalette
import com.trama.app.ui.theme.timelineAccentColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SettingsSection(val route: String, val title: String, val subtitle: String) {
    ROOT("root", "Ajustes", "Control general de la app"),
    CAPTURE_MEMORY("capture-memory", "Captura y memoria", "Categorías, diccionario y ubicación"),
    IA("ia", "IA y resumen", "Resumen diario y automatización inteligente"),
    APPEARANCE("appearance", "Apariencia", "Color y lectura del timeline"),
    ADVANCED("advanced", "Avanzado", "Diagnóstico, modelos, prompts y backup");

    companion object {
        fun fromRoute(route: String?): SettingsSection =
            entries.firstOrNull { it.route == route } ?: ROOT
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    section: SettingsSection = SettingsSection.ROOT,
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings = viewModel
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()

    // Settings state
    val autoStart by settings.autoStart.collectAsState(initial = false)
    val summaryEnabled by settings.summaryEnabled.collectAsState(initial = true)
    val summaryHour by settings.summaryHour.collectAsState(initial = SettingsDataStore.DEFAULT_SUMMARY_HOUR)
    val visibleCalendarIds by settings.visibleCalendarIds.collectAsState(initial = null)
    val intentPatterns by settings.intentPatterns.collectAsState(initial = IntentPattern.DEFAULTS)

    // Gemini API key
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()

    val personalDictionary = viewModel.personalDictionary
    val learnedCorrections by personalDictionary.corrections.collectAsState(initial = emptyList())
    val speakerVerificationManager = viewModel.speakerVerificationManager

    // Backup
    val backupEnabled by settings.backupEnabled.collectAsState(initial = true)
    val backupHour by settings.backupHour.collectAsState(initial = SettingsDataStore.DEFAULT_BACKUP_HOUR)
    val contextPreRoll by settings.contextPreRollSeconds.collectAsState(
        initial = SettingsDataStore.DEFAULT_CONTEXT_PRE_ROLL
    )
    val contextPostRoll by settings.contextPostRollSeconds.collectAsState(
        initial = SettingsDataStore.DEFAULT_CONTEXT_POST_ROLL
    )
    val asrDebugEnabled by settings.asrDebugEnabled.collectAsState(initial = false)
    val listeningStatusOnHome by settings.listeningStatusOnHome.collectAsState(initial = false)
    val asrDebugEngine by settings.asrDebugEngine.collectAsState(initial = "-")
    val asrDebugStatus by settings.asrDebugStatus.collectAsState(initial = "sin datos")
    val asrDebugLastText by settings.asrDebugLastText.collectAsState(initial = "")
    val asrDebugGateText by settings.asrDebugGateText.collectAsState(initial = "")
    val asrDebugTriggerReason by settings.asrDebugTriggerReason.collectAsState(initial = "")
    val asrDebugLastWindowMs by settings.asrDebugLastWindowMs.collectAsState(initial = 0)
    val asrDebugLastDecodeMs by settings.asrDebugLastDecodeMs.collectAsState(initial = 0)
    val asrProcessingInFlight = remember(asrDebugStatus) {
        asrDebugStatus.contains("procesando", ignoreCase = true)
    }
    val watchDebugStatus by settings.watchDebugStatus.collectAsState(initial = "")
    val watchDebugTrigger by settings.watchDebugTrigger.collectAsState(initial = "")
    val locationEnabled by settings.locationEnabled.collectAsState(initial = false)
    val locationIntervalMinutes by settings.locationIntervalMinutes.collectAsState(
        initial = SettingsDataStore.DEFAULT_LOCATION_INTERVAL_MINUTES
    )
    val locationDwellMinutes by settings.locationDwellMinutes.collectAsState(
        initial = SettingsDataStore.DEFAULT_LOCATION_DWELL_MINUTES
    )
    val locationEntryRadiusMeters by settings.locationEntryRadiusMeters.collectAsState(
        initial = SettingsDataStore.DEFAULT_LOCATION_ENTRY_RADIUS_METERS
    )
    val locationExitRadiusMeters by settings.locationExitRadiusMeters.collectAsState(
        initial = SettingsDataStore.DEFAULT_LOCATION_EXIT_RADIUS_METERS
    )
    val googlePlacesApiKey by settings.googlePlacesApiKey.collectAsState(initial = "")
    val timelinePendingColorIndex by settings.timelineColorPending.collectAsState(
        initial = SettingsDataStore.DEFAULT_TIMELINE_COLOR_PENDING
    )
    val timelineCompletedColorIndex by settings.timelineColorCompleted.collectAsState(
        initial = SettingsDataStore.DEFAULT_TIMELINE_COLOR_COMPLETED
    )
    val timelineRecordingColorIndex by settings.timelineColorRecording.collectAsState(
        initial = SettingsDataStore.DEFAULT_TIMELINE_COLOR_RECORDING
    )
    val timelinePlaceColorIndex by settings.timelineColorPlace.collectAsState(
        initial = SettingsDataStore.DEFAULT_TIMELINE_COLOR_PLACE
    )
    val timelineCalendarColorIndex by settings.timelineColorCalendar.collectAsState(
        initial = SettingsDataStore.DEFAULT_TIMELINE_COLOR_CALENDAR
    )
    val themeMode by settings.themeMode.collectAsState(initial = 0)
    val showOldEntriesExpanded by settings.showOldEntriesExpanded.collectAsState(initial = false)
    val locationDebugStatus by LocationDebugState.status.collectAsState()
    val locationDebugLastSample by LocationDebugState.lastSample.collectAsState()
    val locationDebugCandidate by LocationDebugState.candidate.collectAsState()
    val locationDebugActiveDwell by LocationDebugState.activeDwell.collectAsState()

    // Gemma model
    val gemmaManager = remember { GemmaModelManager(context) }
    val gemmaState by gemmaManager.state.collectAsState()
    val gemmaPrefs = remember { GemmaModelManager.getPrefs(context) }
    var modelUrl by remember { mutableStateOf(GemmaModelManager.getModelUrl(context)) }
    var hfToken by remember { mutableStateOf(GemmaModelManager.getHfToken(context)) }
    val modelFilename = remember(modelUrl) { GemmaModelManager.filenameFromUrl(modelUrl) }
    var showModelConfig by remember { mutableStateOf(false) }

    // Sections expanded state
    var patternsExpanded by remember { mutableStateOf(false) }
    var promptsExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var speakerExpanded by remember { mutableStateOf(false) }
    var backupExpanded by remember { mutableStateOf(false) }
    var readableCalendars by remember { mutableStateOf(emptyList<CalendarHelper.ReadableCalendar>()) }
    var hasCalendarReadPermission by remember {
        mutableStateOf(CalendarHelper.hasCalendarPermission(context))
    }
    var calendarImportInProgress by remember { mutableStateOf(false) }
    var speakerStateVersion by remember { mutableIntStateOf(0) }
    var speakerTrainingInProgress by remember { mutableStateOf(false) }
    var speakerRecordingInProgress by remember { mutableStateOf(false) }
    var speakerStatusMessage by remember { mutableStateOf<String?>(null) }
    var activeSpeakerCapture by remember { mutableStateOf<OfflineDictationCapture?>(null) }

    val speakerBackendAvailable = remember(speakerStateVersion) { speakerVerificationManager.isBackendAvailable }
    val speakerEnabled = remember(speakerStateVersion) { speakerVerificationManager.isEnabled }
    val speakerConfigured = remember(speakerStateVersion) { speakerVerificationManager.isConfigured }
    val speakerSampleCount = remember(speakerStateVersion) { speakerVerificationManager.sampleCount }
    val speakerThreshold = remember(speakerStateVersion) { speakerVerificationManager.threshold }

    var speakerDiagnosticsExpanded by remember { mutableStateOf(false) }
    var speakerDiagnosticsVersion by remember { mutableIntStateOf(0) }
    val speakerRecentDiagnostics: List<VerificationDiagnostic> =
        remember(speakerStateVersion, speakerDiagnosticsVersion) {
            speakerVerificationManager.recentDiagnostics()
        }
    val speakerProfileDispersion: ProfileDispersion? =
        remember(speakerStateVersion, speakerDiagnosticsVersion) {
            speakerVerificationManager.profileDispersion()
        }

    // Dialogs
    var editingPattern by remember { mutableStateOf<IntentPattern?>(null) }
    var showAddPatternDialog by remember { mutableStateOf(false) }
    var showDeletePatternDialog by remember { mutableStateOf<IntentPattern?>(null) }
    var showTestDialog by remember { mutableStateOf(false) }

    // Backup state
    var backupInProgress by remember { mutableStateOf(false) }
    var backupLocationName by remember { mutableStateOf(AutoBackupWorker.getBackupFileName(context)) }
    var diagnosticsExportInProgress by remember { mutableStateOf(false) }

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
        AutoBackupWorker.setBackupFile(context, uri, name ?: "trama-backup.json")
        backupLocationName = name ?: "trama-backup.json"
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
                val count = viewModel.exportBackup(uri)
                Toast.makeText(context, "$count entradas exportadas", Toast.LENGTH_SHORT).show()
                context.getSharedPreferences("backup", Context.MODE_PRIVATE)
                    .edit().putLong("last_backup", System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            backupInProgress = false
        }
    }

    val diagnosticsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            diagnosticsExportInProgress = true
            try {
                val summary = DiagnosticsExportManager.exportToUri(context, uri, repository)
                Toast.makeText(
                    context,
                    "Diagnóstico exportado: ${summary.totalEntries} entradas, ${summary.totalEvents} eventos",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error diagnóstico: ${e.message}", Toast.LENGTH_LONG).show()
            }
            diagnosticsExportInProgress = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            backupInProgress = true
            try {
                val (imported, skipped) = viewModel.importBackup(uri)
                Toast.makeText(context, "$imported importadas, $skipped duplicadas", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
            backupInProgress = false
        }
    }

    val fineLocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                settings.setLocationEnabled(true)
                ServiceController.startLocationTracking(context)
            }
        } else {
            Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCalendarReadPermission = result[Manifest.permission.READ_CALENDAR] == true
        if (!hasCalendarReadPermission) {
            Toast.makeText(context, "Necesito acceso al calendario para filtrarlo", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(hasCalendarReadPermission) {
        if (hasCalendarReadPermission) {
            readableCalendars = CalendarHelper.getReadableCalendars(context)
                .filter { it.accountType == "com.google" }
        } else {
            readableCalendars = emptyList()
        }
    }

    fun refreshSpeakerUi() {
        speakerStateVersion += 1
    }

    fun startSpeakerEnrollment() {
        if (!speakerBackendAvailable) {
            speakerStatusMessage = "Falta el modelo offline de voz en app/src/main/assets/asr/speaker/model.onnx"
            return
        }

        val capture = OfflineDictationCapture()
        activeSpeakerCapture = capture
        scope.launch {
            speakerRecordingInProgress = true
            speakerStatusMessage = "Grabando muestra..."
            val window = capture.capture(maxDurationMs = 7_000L)
            speakerRecordingInProgress = false
            activeSpeakerCapture = null

            if (window == null) {
                speakerStatusMessage = "No he podido captar una muestra usable."
                return@launch
            }

            speakerTrainingInProgress = true
            val result = speakerVerificationManager.enrollSample(window)
            speakerTrainingInProgress = false
            speakerStatusMessage = when (result) {
                is SpeakerEnrollmentStep.SampleAccepted ->
                    "Muestra aceptada. Faltan ${result.remainingSamples}."
                is SpeakerEnrollmentStep.EnrollmentReady ->
                    "Perfil listo con ${result.acceptedSamples} muestras."
                is SpeakerEnrollmentStep.Rejected ->
                    result.reason
            }
            refreshSpeakerUi()
        }
    }

    val speakerPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeakerEnrollment()
        } else {
            speakerStatusMessage = "Necesito permiso de micrófono para entrenar tu voz."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(section.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (section == SettingsSection.ROOT) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = "Menos ruido, más intención",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aquí solo vive lo esencial. El resto está agrupado por intención para que ajustar Trama no se sienta como abrir una consola técnica.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                SectionHeader("General")

                SettingToggle(
                    title = "Inicio automatico",
                    subtitle = "Iniciar al encender el dispositivo",
                    checked = autoStart,
                    onCheckedChange = { scope.launch { settings.setAutoStart(it) } }
                )

                Spacer(modifier = Modifier.height(20.dp))

                val tramaColors = com.trama.app.ui.theme.LocalTramaColors.current
                SettingsNavigationCard(
                    icon = Icons.Default.Mic,
                    title = SettingsSection.CAPTURE_MEMORY.title,
                    subtitle = SettingsSection.CAPTURE_MEMORY.subtitle,
                    summary = "Triggers, diccionario aprendido y ubicación pasiva",
                    onClick = { onOpenSection(SettingsSection.CAPTURE_MEMORY) },
                    accent = tramaColors.amber,
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsNavigationCard(
                    icon = Icons.Default.AutoAwesome,
                    title = SettingsSection.IA.title,
                    subtitle = SettingsSection.IA.subtitle,
                    summary = "Resumen diario, Gemini y modelo local",
                    onClick = { onOpenSection(SettingsSection.IA) },
                    accent = tramaColors.teal,
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsNavigationCard(
                    icon = Icons.Default.Palette,
                    title = SettingsSection.APPEARANCE.title,
                    subtitle = SettingsSection.APPEARANCE.subtitle,
                    summary = "Acentos visuales y legibilidad del timeline",
                    onClick = { onOpenSection(SettingsSection.APPEARANCE) },
                    accent = tramaColors.warn,
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingsNavigationCard(
                    icon = Icons.Default.Tune,
                    title = SettingsSection.ADVANCED.title,
                    subtitle = SettingsSection.ADVANCED.subtitle,
                    summary = "Diagnóstico, prompts, backup y control fino",
                    onClick = { onOpenSection(SettingsSection.ADVANCED) },
                    accent = tramaColors.watch,
                )

                Spacer(modifier = Modifier.height(32.dp))
            } else {

            if (section == SettingsSection.ADVANCED) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "Herramientas de laboratorio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aquí viven los ajustes finos del pipeline, el diagnóstico y el mantenimiento del sistema. No son necesarios para usar Trama en el día a día.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PatternLegendChip(
                            text = "Captura",
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                        PatternLegendChip(
                            text = "Diagnóstico",
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                        PatternLegendChip(
                            text = "Modelos",
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                        PatternLegendChip(
                            text = "Mantenimiento",
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ═══════════════════════════════════════════════════════════════
            // CAPTURA
            // ═══════════════════════════════════════════════════════════════
            SectionHeader("Captura")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Captura de voz",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Vosk hace de filtro ligero y, si detecta una frase relevante, Whisper transcribe la captura completa. Estos dos controles solo ajustan cuánto contexto se conserva antes y después.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Contexto previo (t0)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${contextPreRoll}s",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = contextPreRoll.toFloat(),
                        onValueChange = {
                            scope.launch { settings.setContextPreRollSeconds(it.roundToInt()) }
                        },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Contexto posterior (t1)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${contextPostRoll}s",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = contextPostRoll.toFloat(),
                        onValueChange = {
                            scope.launch { settings.setContextPostRollSeconds(it.roundToInt()) }
                        },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "El audio solo vive en memoria y se descarta después de procesarse.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggle(
                        title = "Modo diagnostico ASR",
                        subtitle = "Muestra motor activo y ultima transcripcion",
                        checked = asrDebugEnabled,
                        onCheckedChange = { scope.launch { settings.setAsrDebugEnabled(it) } }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggle(
                        title = "Estado tecnico en inicio",
                        subtitle = "Sustituye \"Escuchando\" por el estado real de captura solo para diagnostico",
                        checked = listeningStatusOnHome,
                        onCheckedChange = { scope.launch { settings.setListeningStatusOnHome(it) } }
                    )

                    AnimatedVisibility(visible = asrDebugEnabled) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Diagnostico de escucha",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                AnimatedVisibility(visible = asrProcessingInFlight) {
                                    Text(
                                        "Procesando...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Motor: $asrDebugEngine", style = MaterialTheme.typography.bodyMedium)
                                Text("Estado: $asrDebugStatus", style = MaterialTheme.typography.bodyMedium)
                                if (asrDebugTriggerReason.isNotBlank()) {
                                    Text(
                                        "Motivo: $asrDebugTriggerReason",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "Ventana: ${asrDebugLastWindowMs} ms · Decodificacion: ${asrDebugLastDecodeMs} ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Texto del gate",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    asrDebugGateText.ifBlank { "—" },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Transcripción Whisper",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (asrDebugLastText.isNotBlank()) asrDebugLastText else "—",
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                // ── Sección reloj ──────────────────────────
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    thickness = 0.5.dp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "Reloj",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Estado: ${watchDebugStatus.ifBlank { "sin datos" }}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (watchDebugTrigger.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Último trigger",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        watchDebugTrigger,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            CaptureDiagnosticsCard(
                exportInProgress = diagnosticsExportInProgress,
                onExport = {
                    diagnosticsExportLauncher.launch(DiagnosticsExportManager.fileName())
                }
            )

            SectionDivider()
            }

            if (section == SettingsSection.APPEARANCE) {
            SectionHeader("Tema")

            Text(
                "Elige entre modo claro, oscuro o déjalo sincronizado con el sistema.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            val themeOptions = listOf("Sistema", "Claro", "Oscuro")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeOptions.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = themeMode == index,
                        onClick = { scope.launch { settings.setThemeMode(index) } },
                        shape = when (index) {
                            0 -> RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
                            themeOptions.lastIndex -> RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            SectionDivider()

            SectionHeader("Legibilidad")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mostrar tareas de otros días", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "La sección 'De otros días' aparece expandida al abrir la app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showOldEntriesExpanded,
                    onCheckedChange = { scope.launch { settings.setShowOldEntriesExpanded(it) } }
                )
            }

            SectionDivider()

            SectionHeader("Timeline")

            Text(
                "Cada tipo de evento puede tener su propio acento. Se usa de forma sutil en las cards para que el timeline sea más fácil de leer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            TimelineColorPicker(
                title = "Pendientes",
                subtitle = "Entradas activas y tareas abiertas",
                selectedIndex = timelinePendingColorIndex,
                onSelect = { index -> scope.launch { settings.setTimelineColorPending(index) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TimelineColorPicker(
                title = "Completadas",
                subtitle = "Acciones ya resueltas",
                selectedIndex = timelineCompletedColorIndex,
                onSelect = { index -> scope.launch { settings.setTimelineColorCompleted(index) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TimelineColorPicker(
                title = "Grabaciones",
                subtitle = "Sesiones de voz y su procesado",
                selectedIndex = timelineRecordingColorIndex,
                onSelect = { index -> scope.launch { settings.setTimelineColorRecording(index) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TimelineColorPicker(
                title = "Lugares",
                subtitle = "Estancias y eventos de ubicación",
                selectedIndex = timelinePlaceColorIndex,
                onSelect = { index -> scope.launch { settings.setTimelineColorPlace(index) } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TimelineColorPicker(
                title = "Calendario",
                subtitle = "Eventos leídos de tu agenda",
                selectedIndex = timelineCalendarColorIndex,
                onSelect = { index -> scope.launch { settings.setTimelineColorCalendar(index) } }
            )

            SectionDivider()
            }

            if (section == SettingsSection.CAPTURE_MEMORY) {
            SectionHeader("Ubicacion")

            SettingToggle(
                title = "Deteccion de estancias",
                subtitle = "Registra lugares visitados de forma pasiva",
                checked = locationEnabled,
                onCheckedChange = { enabled ->
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (enabled && !hasPermission) {
                        fineLocationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    } else {
                        scope.launch {
                            settings.setLocationEnabled(enabled)
                            if (enabled) ServiceController.startLocationTracking(context)
                            else ServiceController.stopLocationTracking(context)
                        }
                    }
                }
            )

            AnimatedVisibility(visible = locationEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Intervalo GPS", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${locationIntervalMinutes} min", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = locationIntervalMinutes.toFloat(),
                        onValueChange = {
                            scope.launch { settings.setLocationIntervalMinutes(it.roundToInt()) }
                        },
                        valueRange = 2f..15f,
                        steps = 12,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Umbral de estancia", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${locationDwellMinutes} min", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = locationDwellMinutes.toFloat(),
                        onValueChange = {
                            scope.launch { settings.setLocationDwellMinutes(it.roundToInt()) }
                        },
                        valueRange = 5f..60f,
                        steps = 10,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Radio de entrada", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${locationEntryRadiusMeters} m", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = locationEntryRadiusMeters.toFloat(),
                        onValueChange = {
                            val meters = it.roundToInt()
                            scope.launch {
                                settings.setLocationEntryRadiusMeters(meters)
                                if (locationEnabled) {
                                    ServiceController.stopLocationTracking(context)
                                    ServiceController.startLocationTracking(context)
                                }
                            }
                        },
                        valueRange = 20f..200f,
                        steps = 17,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Radio de salida", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${locationExitRadiusMeters} m", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = locationExitRadiusMeters.toFloat(),
                        onValueChange = {
                            val meters = it.roundToInt()
                            scope.launch {
                                settings.setLocationExitRadiusMeters(meters)
                                if (locationEnabled) {
                                    ServiceController.stopLocationTracking(context)
                                    ServiceController.startLocationTracking(context)
                                }
                            }
                        },
                        valueRange = 50f..400f,
                        steps = 34,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = googlePlacesApiKey,
                        onValueChange = { scope.launch { settings.setGooglePlacesApiKey(it) } },
                        label = { Text("Google Places API key") },
                        supportingText = { Text("Opcional. Se usará más adelante como fallback.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        "Por ahora las estancias aparecen como \"Lugar sin identificar\" y se enriquecerán en la siguiente fase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Diagnóstico de ubicación",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Estado: $locationDebugStatus", style = MaterialTheme.typography.bodyMedium)
                            Text("Última muestra: $locationDebugLastSample", style = MaterialTheme.typography.bodySmall)
                            Text("Candidato: $locationDebugCandidate", style = MaterialTheme.typography.bodySmall)
                            Text("Dwell activo: $locationDebugActiveDwell", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            SectionDivider()
            }

            // ═══════════════════════════════════════════════════════════════
            // DICCIONARIO
            // ═══════════════════════════════════════════════════════════════
            if (section == SettingsSection.CAPTURE_MEMORY) {
            SectionHeader("Diccionario aprendido")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Estas correcciones se aprenden cuando editas una entrada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (learnedCorrections.isEmpty()) {
                        Text(
                            "Aún no hay correcciones aprendidas.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        learnedCorrections
                            .sortedByDescending { it.count }
                            .forEach { correction ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = correction.wrong,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "→ ${correction.correct}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${correction.count} aprendizaje(s)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    personalDictionary.removeCorrection(correction.wrong)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Eliminar corrección"
                                            )
                                        }
                                    }
                                }
                            }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                scope.launch { personalDictionary.clearAll() }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Vaciar diccionario")
                        }
                    }
                }
            }

            SectionDivider()
            }

            // ═══════════════════════════════════════════════════════════════
            // IA Y RESUMEN
            // ═══════════════════════════════════════════════════════════════
            if (section == SettingsSection.IA) {
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
                            viewModel.setGeminiApiKey(it)
                        },
                        label = { Text("Clave API Gemini") },
                        supportingText = { Text("Gratis en aistudio.google.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SectionHeader("Google Calendar")

                    if (!hasCalendarReadPermission) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "Activa el acceso al calendario",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Así podrás elegir qué calendarios de Google importan eventos a Trama.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                FilledTonalButton(
                                    onClick = {
                                        calendarPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CALENDAR,
                                                Manifest.permission.WRITE_CALENDAR
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Dar acceso")
                                }
                            }
                        }
                    } else {
                        val effectiveSelectedIds = visibleCalendarIds ?: readableCalendars.map { it.id }.toSet()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "Elige qué calendarios se importan",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Los cambios solo aplican hacia adelante. Lo ya importado no se borra.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                settings.setVisibleCalendarIds(readableCalendars.map { it.id }.toSet())
                                                GoogleCalendarSyncManager(context).syncSelectedCalendars()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Todos")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                settings.setVisibleCalendarIds(emptySet())
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Ninguno")
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            calendarImportInProgress = true
                                            try {
                                                GoogleCalendarSyncManager(context).syncSelectedCalendars()
                                                Toast.makeText(
                                                    context,
                                                    "Calendario sincronizado",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } finally {
                                                calendarImportInProgress = false
                                            }
                                        }
                                    },
                                    enabled = !calendarImportInProgress,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (calendarImportInProgress) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(if (calendarImportInProgress) "Importando..." else "Importar ahora")
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (readableCalendars.isEmpty()) {
                                    Text(
                                        "No he encontrado calendarios legibles en el dispositivo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    readableCalendars.forEach { calendar ->
                                        CalendarSourceRow(
                                            title = calendar.displayName,
                                            subtitle = calendar.accountName.ifBlank { calendar.accountType.ifBlank { "Calendario local" } },
                                            checked = calendar.id in effectiveSelectedIds,
                                            onCheckedChange = { checked ->
                                                val next = effectiveSelectedIds.toMutableSet().apply {
                                                    if (checked) add(calendar.id) else remove(calendar.id)
                                                }
                                                scope.launch {
                                                    settings.setVisibleCalendarIds(next)
                                                    GoogleCalendarSyncManager(context).syncSelectedCalendars()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SectionDivider()
            }

            if (section == SettingsSection.ADVANCED) {
            CollapsibleSectionHeader(
                title = "Prompts",
                subtitle = "Edita los prompts del sistema sin tocar código",
                expanded = promptsExpanded,
                onToggle = { promptsExpanded = !promptsExpanded }
            )

            AnimatedVisibility(
                visible = promptsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    PromptTemplateStore.definitions.forEach { definition ->
                        PromptEditorCard(
                            definition = definition,
                            onSave = { PromptTemplateStore.set(context, definition.id, it) },
                            onReset = { PromptTemplateStore.reset(context, definition.id) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            SectionDivider()
            }

            // ═══════════════════════════════════════════════════════════════
            // MODELO LOCAL
            // ═══════════════════════════════════════════════════════════════
            if (section == SettingsSection.ADVANCED) {
            CollapsibleSectionHeader(
                title = "Modelo local",
                subtitle = "Descarga, activa y configura el modelo en el dispositivo",
                expanded = modelExpanded,
                onToggle = { modelExpanded = !modelExpanded }
            )

            AnimatedVisibility(
                visible = modelExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // ── Status + action button ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                modelFilename.removeSuffix(".task"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when (gemmaState) {
                                    is GemmaModelManager.DownloadState.Downloaded -> {
                                        val sizeMB = gemmaManager.getModelSizeMB()
                                        "Descargado · $sizeMB MB"
                                    }
                                    is GemmaModelManager.DownloadState.Downloading ->
                                        "Descargando ${(gemmaState as GemmaModelManager.DownloadState.Downloading).progress}%..."
                                    is GemmaModelManager.DownloadState.Failed ->
                                        (gemmaState as GemmaModelManager.DownloadState.Failed).message
                                    else -> "No descargado"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when (gemmaState) {
                                    is GemmaModelManager.DownloadState.Downloaded -> MaterialTheme.colorScheme.primary
                                    is GemmaModelManager.DownloadState.Failed -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        when (gemmaState) {
                            is GemmaModelManager.DownloadState.NotDownloaded -> {
                                FilledTonalButton(onClick = { gemmaManager.startDownload() }) {
                                    Icon(Icons.Default.CloudDownload, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Descargar")
                                }
                            }
                            is GemmaModelManager.DownloadState.Downloading -> {
                                OutlinedButton(onClick = { gemmaManager.cancelDownload() }) {
                                    Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cancelar")
                                }
                            }
                            is GemmaModelManager.DownloadState.Downloaded -> {
                                var showDeleteDialog by remember { mutableStateOf(false) }
                                OutlinedButton(onClick = { showDeleteDialog = true }) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Eliminar")
                                }
                                if (showDeleteDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteDialog = false },
                                        title = { Text("Eliminar modelo") },
                                        text = { Text("Se liberará espacio pero no podrás procesar grabaciones sin conexión.") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                gemmaManager.deleteModel()
                                                showDeleteDialog = false
                                            }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
                                        }
                                    )
                                }
                            }
                            is GemmaModelManager.DownloadState.Failed -> {
                                FilledTonalButton(onClick = { gemmaManager.startDownload() }) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }

                    // ── Progress bar ──
                    if (gemmaState is GemmaModelManager.DownloadState.Downloading) {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (gemmaState as GemmaModelManager.DownloadState.Downloading).progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Permite procesar grabaciones sin conexión a internet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // ── Enable/disable local model ──
                    if (gemmaState is GemmaModelManager.DownloadState.Downloaded) {
                        Spacer(Modifier.height(8.dp))
                        var localModelEnabled by remember {
                            mutableStateOf(GemmaClient.isLocalModelEnabled(context))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Usar modelo local",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    if (localModelEnabled) "Procesará sin conexión"
                                    else "Solo se usará la nube",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = localModelEnabled,
                                onCheckedChange = {
                                    localModelEnabled = it
                                    GemmaClient.setLocalModelEnabled(context, it)
                                }
                            )
                        }
                    }

                    // ── Config toggle ──
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { showModelConfig = !showModelConfig },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            if (showModelConfig) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Configurar modelo")
                    }

                    // ── Expandable config fields ──
                    AnimatedVisibility(visible = showModelConfig) {
                        Column {
                            Spacer(Modifier.height(4.dp))

                            OutlinedTextField(
                                value = modelUrl,
                                onValueChange = {
                                    modelUrl = it
                                    GemmaModelManager.setModelUrl(context, it)
                                },
                                label = { Text("URL del modelo (.task)") },
                                supportingText = {
                                    Text("Archivo: $modelFilename")
                                },
                                singleLine = false,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = hfToken,
                                onValueChange = {
                                    hfToken = it
                                    GemmaModelManager.setHfToken(context, it)
                                },
                                label = { Text("Token HuggingFace (opcional)") },
                                supportingText = { Text("Necesario para modelos con acceso restringido") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
                }
            }

            SectionDivider()
            }

            // ═══════════════════════════════════════════════════════════════
            // SOLO MI VOZ
            // ═══════════════════════════════════════════════════════════════
            if (section == SettingsSection.ADVANCED) {
            CollapsibleSectionHeader(
                title = "Solo mi voz",
                subtitle = "Verificacion offline despues de Whisper",
                expanded = speakerExpanded,
                onToggle = { speakerExpanded = !speakerExpanded }
            )

            AnimatedVisibility(
                visible = speakerExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

                    SettingToggle(
                        title = if (speakerConfigured && speakerEnabled) "Activado"
                        else if (speakerConfigured) "Preparado"
                        else "Sin entrenar",
                        subtitle = when {
                            !speakerBackendAvailable -> "Falta el modelo de speaker embedding offline"
                            speakerConfigured -> "Filtra capturas ajenas despues de transcribir"
                            else -> "Necesita al menos 3 muestras de tu voz"
                        },
                        checked = speakerEnabled && speakerConfigured && speakerBackendAvailable,
                        onCheckedChange = {
                            speakerVerificationManager.setEnabled(it)
                            refreshSpeakerUi()
                        },
                        enabled = speakerConfigured && speakerBackendAvailable
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Muestras guardadas: $speakerSampleCount/${SherpaSpeakerVerificationManager.REQUIRED_SAMPLES}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (speakerRecordingInProgress) {
                                    activeSpeakerCapture?.requestStop()
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        startSpeakerEnrollment()
                                    } else {
                                        speakerPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            enabled = !speakerTrainingInProgress,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (speakerTrainingInProgress) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (speakerRecordingInProgress) "Detener muestra" else "Grabar muestra")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    speakerVerificationManager.reset()
                                    speakerStatusMessage = "Perfil borrado"
                                    refreshSpeakerUi()
                                }
                            },
                            enabled = speakerSampleCount > 0,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Resetear")
                        }
                    }

                    if (speakerBackendAvailable) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Umbral de coincidencia: ${"%.0f".format(speakerThreshold * 100)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = speakerThreshold,
                            onValueChange = {
                                speakerVerificationManager.setThreshold(it)
                                refreshSpeakerUi()
                            },
                            valueRange = 0.4f..0.95f
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = {
                                speakerDiagnosticsExpanded = !speakerDiagnosticsExpanded
                                if (speakerDiagnosticsExpanded) speakerDiagnosticsVersion++
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (speakerDiagnosticsExpanded) "Ocultar diagnóstico"
                                else "Ver diagnóstico (avanzado)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        AnimatedVisibility(
                            visible = speakerDiagnosticsExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            SpeakerDiagnosticsBlock(
                                dispersion = speakerProfileDispersion,
                                diagnostics = speakerRecentDiagnostics,
                                onRefresh = { speakerDiagnosticsVersion++ }
                            )
                        }
                    }

                    speakerStatusMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionDivider()
            }

            // ═══════════════════════════════════════════════════════════════
            // BACKUP
            // ═══════════════════════════════════════════════════════════════
            if (section == SettingsSection.ADVANCED) {
            CollapsibleSectionHeader(
                title = "Copia de seguridad",
                subtitle = "Exporta o automatiza el respaldo de tus datos",
                expanded = backupExpanded,
                onToggle = { backupExpanded = !backupExpanded }
            )

            AnimatedVisibility(
                visible = backupExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

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
                            onClick = { backupFileSetupLauncher.launch("trama-backup.json") },
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
            val lastError = remember { AutoBackupWorker.getLastError(context) }
            if (lastBackup > 0) {
                val dateStr = java.text.SimpleDateFormat("d MMM yyyy, HH:mm",
                    java.util.Locale("es")).format(lastBackup)
                Text(
                    "Último: $dateStr ($lastCount elementos)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (lastError != null) {
                Text(
                    "⚠ $lastError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
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
                }
            }

            SectionDivider()
            }

            // ═══════════════════════════════════════════════════════════════
            // PATRONES (collapsible)
            // ═══════════════════════════════════════════════════════════════
            if (section == SettingsSection.CAPTURE_MEMORY) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { patternsExpanded = !patternsExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Categorias de captura",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "${intentPatterns.count { it.enabled }} activas de ${intentPatterns.size}",
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

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Como funcionan",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Las categorias no tienen que entender toda la frase. Sirven para detectar familias de intención cortas y estables, y solo entonces dejar pasar la captura a Whisper.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Base recomendada: usa pocas frases muy intencionales. Mantén expresiones naturales como \"recordar\", \"necesito\", \"falta por\" o \"pendiente de\", pero evita triggers conversacionales sueltos como \"llama\", \"dile\" o \"pregunta\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PatternLegendChip(
                            text = "Base recomendada",
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                        PatternLegendChip(
                            text = "Personalizable",
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                        PatternLegendChip(
                            text = "Tiempo fuera del gate",
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                            Text("Nueva categoria")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val builtInPatterns = intentPatterns.filterNot { it.isCustom }
                    val customPatterns = intentPatterns.filter { it.isCustom }

                    Text(
                        "Categorias base",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    builtInPatterns.forEachIndexed { index, pattern ->
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

                    if (customPatterns.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Categorias personalizadas",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        customPatterns.forEachIndexed { index, pattern ->
                            IntentPatternCard(
                                pattern = pattern,
                                colorIndex = builtInPatterns.size + index,
                                onToggle = { enabled ->
                                    scope.launch { settings.updatePattern(pattern.copy(enabled = enabled), intentPatterns) }
                                },
                                onEdit = { editingPattern = pattern },
                                onDelete = { showDeletePatternDialog = pattern }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            }
            }
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
            text = { Text("Se eliminara esta categoria y sus ${pattern.triggers.size} frases.") },
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
private fun PatternLegendChip(
    text: String,
    color: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SettingsNavigationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    summary: String,
    onClick: () -> Unit,
    accent: Color? = null,
) {
    val t = com.trama.app.ui.theme.LocalTramaColors.current
    val c = accent ?: MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = t.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, t.softBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = c.copy(alpha = 0.14f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(18.dp),
                    tint = c
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subtitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = c,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

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
private fun CollapsibleSectionHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentAlpha = if (enabled) 1f else 0.6f
        Column(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun CalendarSourceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineColorPicker(
    title: String,
    subtitle: String,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = timelineAccentColor(selectedIndex).copy(alpha = 0.14f)
                ) {
                    Text(
                        TimelineAccentPalette[selectedIndex % TimelineAccentPalette.size].name,
                        style = MaterialTheme.typography.labelMedium,
                        color = timelineAccentColor(selectedIndex),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimelineAccentPalette.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    Surface(
                        modifier = Modifier
                            .size(width = 34.dp, height = 22.dp)
                            .clickable { onSelect(index) },
                        shape = RoundedCornerShape(999.dp),
                        color = option.color.copy(alpha = if (selected) 0.95f else 0.22f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) option.color else option.color.copy(alpha = 0.28f)
                        )
                    ) {
                        if (selected) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptEditorCard(
    definition: PromptTemplateStore.PromptDefinition,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    var value by remember(definition.id) {
        mutableStateOf(PromptTemplateStore.get(context, definition.id))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                definition.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                definition.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 16,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onSave(value) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Guardar")
                }
                OutlinedButton(
                    onClick = {
                        onReset()
                        value = PromptTemplateStore.get(context, definition.id)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Restablecer")
                }
            }
        }
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
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (pattern.isCustom) {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            }
                        ) {
                            Text(
                                text = if (pattern.isCustom) "Personalizada" else "Base",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pattern.isCustom) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = patternDescription(pattern),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "${pattern.triggers.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

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

private fun patternDescription(pattern: IntentPattern): String {
    return when (pattern.id) {
        "recordatorios" -> "Frases explicitas para recordar algo."
        "tareas" -> "Pendientes generales como hacer, deber o necesitar."
        "comunicacion" -> "Acciones de llamar, escribir o mandar un mensaje."
        else -> if (pattern.isCustom) {
            "Categoria creada por ti para un caso concreto."
        } else {
            "Familia de activacion personalizada."
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
        title = { Text("Editar categoria") },
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
            Button(onClick = {
                onSave(
                    pattern.copy(
                        label = label.trim().ifBlank { pattern.label },
                        triggers = triggers
                    )
                )
            },
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
        title = { Text("Nueva categoria") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it },
                    label = { Text("Nombre de la categoria") }, singleLine = true,
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
        com.trama.app.speech.IntentDetector().apply { setPatterns(patterns) }
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

@Composable
private fun CaptureDiagnosticsCard(
    exportInProgress: Boolean,
    onExport: () -> Unit
) {
    var tick by remember { mutableIntStateOf(0) }
    val events = remember(tick) {
        CaptureLog.recentEvents(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
    }

    val rows: List<Triple<String, String, Int>> = remember(events) {
        val grouped = events.groupingBy { it.gate to it.result }.eachCount()
        val order = listOf(
            "ASR_GATE" to "OK",
            "ASR_GATE" to "NO_MATCH",
            "ASR_FINAL" to "OK",
            "SPEAKER" to "OK",
            "SPEAKER" to "REJECT",
            "INTENT" to "OK",
            "INTENT" to "NO_MATCH",
            "DEDUP_MEM" to "DUP",
            "DEDUP_SEM" to "DUP",
            "SERVICE" to "OK",
            "SERVICE" to "REJECT",
            "LLM" to "OK",
            "LLM" to "REJECT",
            "SAVE" to "OK",
            "RECORDING" to "OK",
            "RECORDING" to "NO_MATCH"
        )
        order.map { (g, r) -> Triple(g, r, grouped[g to r] ?: 0) }
    }

    val labels = mapOf(
        "ASR_GATE" to "OK" to "Gate aceptado/fallback",
        "ASR_GATE" to "NO_MATCH" to "Gate ligero sin trigger",
        "ASR_FINAL" to "OK" to "Transcripciones ASR",
        "SPEAKER" to "OK" to "Speaker verificado",
        "SPEAKER" to "REJECT" to "Speaker rechazado",
        "INTENT" to "OK" to "Intent detectado",
        "INTENT" to "NO_MATCH" to "Sin intent (ignorado)",
        "DEDUP_MEM" to "DUP" to "Duplicado (memoria)",
        "DEDUP_SEM" to "DUP" to "Duplicado (semántico)",
        "SERVICE" to "OK" to "Servicio activo/heartbeat",
        "SERVICE" to "REJECT" to "Servicio parado",
        "LLM" to "OK" to "LLM acepta tarea",
        "LLM" to "REJECT" to "LLM → revisión",
        "SAVE" to "OK" to "Entradas guardadas",
        "RECORDING" to "OK" to "Grabaciones con acciones",
        "RECORDING" to "NO_MATCH" to "Grabaciones sin acciones"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Diagnóstico de captura (últimas 24h)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Una entrada por cada etapa del pipeline. Si el número de 'Transcripciones ASR' sube pero 'Entradas guardadas' no, mira qué etapa intermedia rechaza: speaker, intent o LLM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { tick++ }) { Text("Actualizar") }
                    TextButton(
                        onClick = onExport,
                        enabled = !exportInProgress
                    ) {
                        Text(if (exportInProgress) "Exportando..." else "Exportar 72h")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            rows.forEach { (gate, result, count) ->
                val label = labels[gate to result] ?: "$gate / $result"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (events.isEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Sin eventos todavía. Deja el servicio en marcha y vuelve a abrir esta pantalla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SpeakerDiagnosticsBlock(
    dispersion: ProfileDispersion?,
    diagnostics: List<VerificationDiagnostic>,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Dispersión del perfil",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (dispersion == null) {
                    Text(
                        "Necesitas al menos 2 muestras para calcular dispersión.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val meanPct = "%.0f".format(dispersion.meanSimilarity * 100)
                    val stdPct = "%.1f".format(dispersion.stdSimilarity * 100)
                    Text(
                        "Similitud media entre muestras: $meanPct% · σ ±$stdPct%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val hint = when {
                        dispersion.stdSimilarity < 0.02f && dispersion.meanSimilarity > 0.92f ->
                            "Tus muestras son demasiado parecidas. Graba alguna en condiciones distintas (más lejos, con ruido, susurro o voz alta)."
                        dispersion.meanSimilarity < 0.55f ->
                            "Las muestras se parecen poco entre sí. Si no eres tú en alguna, considera resetear el perfil."
                        else -> null
                    }
                    if (hint != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Últimas verificaciones",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onRefresh,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Actualizar", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (diagnostics.isEmpty()) {
            Text(
                "Aún no hay verificaciones registradas. Aparecerán aquí tras la próxima captura.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val accepted = diagnostics.count { it.accepted }
            val rejected = diagnostics.size - accepted
            val withProfileSim = diagnostics.filter { !it.profileSimilarity.isNaN() }
            val avgProfile = if (withProfileSim.isNotEmpty()) {
                withProfileSim.map { it.profileSimilarity }.average().toFloat()
            } else {
                Float.NaN
            }
            val clippedShare = diagnostics.count { it.clippingFraction > 0.01f }

            val summary = buildString {
                append("Total: ${diagnostics.size}  ·  Aceptadas: $accepted  ·  Rechazadas: $rejected")
                if (!avgProfile.isNaN()) {
                    append("\nSim. media al centroide: ${"%.0f".format(avgProfile * 100)}%")
                }
                if (clippedShare > 0) {
                    append("\nCapturas con clipping (>1%): $clippedShare")
                }
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            diagnostics.asReversed().take(10).forEach { entry ->
                SpeakerDiagnosticsRow(entry)
            }
        }
    }
}

@Composable
private fun SpeakerDiagnosticsRow(entry: VerificationDiagnostic) {
    val simText = if (entry.profileSimilarity.isNaN()) {
        "—"
    } else {
        "${"%.0f".format(entry.similarity * 100)}% / ${"%.0f".format(entry.threshold * 100)}%"
    }
    val durationText = "${entry.durationMs} ms"
    val statusColor = if (entry.accepted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.error
    }
    val statusLabel = if (entry.accepted) "OK" else "rechazada"
    val rmsText = if (entry.rmsDbfs.isFinite()) "${"%.0f".format(entry.rmsDbfs)} dBFS" else "—"
    val clipping = if (entry.clippingFraction > 0.01f) {
        "  ·  clip ${"%.0f".format(entry.clippingFraction * 100)}%"
    } else {
        ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            statusLabel,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
            modifier = Modifier.width(72.dp)
        )
        Text(
            simText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(96.dp)
        )
        Text(
            "$durationText  ·  $rmsText$clipping",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
