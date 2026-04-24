package com.trama.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.trama.app.audio.OfflineDictationCapture
import com.trama.app.audio.SherpaWhisperAsrEngine
import com.trama.app.location.DwellDurationFormatter
import com.trama.app.location.PlaceMapsLauncher
import com.trama.app.summary.PlaceOpinionSummarizer
import com.trama.app.ui.components.SectionRule
import com.trama.app.ui.components.SoftCard
import com.trama.app.ui.theme.LocalTramaColors
import com.trama.shared.data.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    placeId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val opinionSummarizer = remember { PlaceOpinionSummarizer(context) }
    val whisperEngine = remember { SherpaWhisperAsrEngine(context) }
    val scope = rememberCoroutineScope()
    val place by repository.getPlaceById(placeId).collectAsState(initial = null)
    val events by repository.getTimelineEventsByPlaceId(placeId).collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es")) }

    val currentPlace = place
    if (currentPlace == null) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Lugar") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            Text(
                "Lugar no encontrado",
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp)
            )
        }
        return
    }

    var editedName by remember(currentPlace.id, currentPlace.name) { mutableStateOf(currentPlace.name) }
    var editedRating by remember(currentPlace.id, currentPlace.rating) { mutableStateOf(currentPlace.rating ?: 0) }
    var opinionText by remember(currentPlace.id, currentPlace.opinionText) { mutableStateOf(currentPlace.opinionText.orEmpty()) }
    var isSummarizing by remember(currentPlace.id) { mutableStateOf(false) }
    var opinionSummaryError by remember(currentPlace.id) { mutableStateOf<String?>(null) }
    var isDictating by remember(currentPlace.id) { mutableStateOf(false) }
    var isTranscribingOpinion by remember(currentPlace.id) { mutableStateOf(false) }
    var opinionInputError by remember(currentPlace.id) { mutableStateOf<String?>(null) }
    var activeDictationCapture by remember { mutableStateOf<OfflineDictationCapture?>(null) }

    fun appendOpinionTranscript(spokenText: String) {
        opinionText = buildString {
            val current = opinionText.trim()
            if (current.isNotBlank()) {
                append(current)
                append("\n")
            }
            append(spokenText.trim())
        }
    }

    fun startOfflineDictation() {
        if (!whisperEngine.isAvailable) {
            opinionInputError = "Whisper no está disponible ahora mismo para dictado offline."
            return
        }

        opinionInputError = null
        opinionSummaryError = null

        val capture = OfflineDictationCapture()
        activeDictationCapture = capture
        scope.launch {
            isDictating = true
            val window = capture.capture()
            isDictating = false
            activeDictationCapture = null

            if (window == null || window.durationMs() < 300L) {
                opinionInputError = "No he podido captar audio suficiente."
                return@launch
            }

            isTranscribingOpinion = true
            val transcript = withContext(Dispatchers.IO) {
                whisperEngine.transcribe(window, languageTag = "es")?.text?.trim()
            }
            isTranscribingOpinion = false

            if (transcript.isNullOrBlank()) {
                opinionInputError = "No he podido transcribir esa opinión."
            } else {
                appendOpinionTranscript(transcript)
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = RequestPermission()
    ) { granted ->
        if (granted) {
            startOfflineDictation()
        } else {
            opinionInputError = "Necesito permiso de micrófono para dictar tu opinión."
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Lugar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        repository.renamePlace(currentPlace.id, editedName.trim())
                        repository.updateTimelineEventTitlesForPlace(currentPlace.id, editedName.trim())
                    }
                },
                enabled = editedName.trim().isNotBlank() && editedName.trim() != currentPlace.name
            ) {
                Text("Guardar nombre")
            }

            SectionRule(title = "Ficha")
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(currentPlace.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    currentPlace.type?.let {
                        Text("Tipo: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Visitas: ${currentPlace.visitCount}", style = MaterialTheme.typography.bodyMedium)
                    currentPlace.lastVisitAt?.let {
                        Text("Última visita: ${dateFormat.format(Date(it))}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Lat ${"%.5f".format(currentPlace.latitude)}, Lon ${"%.5f".format(currentPlace.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionRule(title = "Tu opinión")
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Valoración".uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalTramaColors.current.mutedText
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            (1..5).forEach { star ->
                                IconButton(onClick = { editedRating = if (editedRating == star) 0 else star }) {
                                    Icon(
                                        imageVector = if (star <= editedRating) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "$star estrellas",
                                        tint = if (star <= editedRating) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = opinionText,
                        onValueChange = { opinionText = it },
                        label = { Text("Qué te pareció") },
                        placeholder = { Text("Comida, servicio, ambiente, precio...") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    repository.updatePlaceOpinion(
                                        id = currentPlace.id,
                                        rating = editedRating.takeIf { it > 0 },
                                        opinionText = opinionText.trim().ifBlank { null },
                                        opinionSummary = currentPlace.opinionSummary,
                                        opinionUpdatedAt = System.currentTimeMillis()
                                    )
                                }
                            },
                            enabled = editedRating != (currentPlace.rating ?: 0) ||
                                opinionText.trim() != currentPlace.opinionText.orEmpty().trim()
                        ) {
                            Text("Guardar opinión")
                        }

                        TextButton(
                            onClick = {
                                if (isDictating) {
                                    activeDictationCapture?.requestStop()
                                    return@TextButton
                                }

                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    startOfflineDictation()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !isTranscribingOpinion && !isSummarizing
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Text(
                                when {
                                    isDictating -> "Detener"
                                    isTranscribingOpinion -> "Transcribiendo..."
                                    else -> "Dictar"
                                }
                            )
                        }
                    }

                    opinionInputError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isSummarizing = true
                                opinionSummaryError = null

                                repository.updatePlaceOpinion(
                                    id = currentPlace.id,
                                    rating = editedRating.takeIf { it > 0 },
                                    opinionText = opinionText.trim().ifBlank { null },
                                    opinionSummary = currentPlace.opinionSummary,
                                    opinionUpdatedAt = System.currentTimeMillis()
                                )

                                val summary = withContext(Dispatchers.IO) {
                                    opinionSummarizer.summarize(
                                        placeName = currentPlace.name,
                                        rating = editedRating.takeIf { it > 0 },
                                        opinionText = opinionText
                                    )
                                }

                                if (summary.isNullOrBlank()) {
                                    opinionSummaryError = "No se pudo resumir la opinión ahora mismo."
                                } else {
                                    repository.updatePlaceOpinionSummary(
                                        id = currentPlace.id,
                                        opinionSummary = summary,
                                        opinionUpdatedAt = System.currentTimeMillis()
                                    )
                                }

                                isSummarizing = false
                            }
                        },
                        enabled = opinionText.trim().isNotBlank() && !isSummarizing && !isDictating && !isTranscribingOpinion,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSummarizing) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Resumir mi opinión")
                        }
                    }

                    currentPlace.opinionSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                        SoftCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Resumen IA".uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LocalTramaColors.current.mutedText
                                )
                                Text(summary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    opinionSummaryError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        if (currentPlace.isHome) repository.clearHomePlace(currentPlace.id)
                        else repository.markHomePlace(currentPlace.id)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (currentPlace.isHome) "Quitar como casa" else "Marcar como casa")
            }

            Button(
                onClick = {
                    scope.launch {
                        if (currentPlace.isWork) repository.clearWorkPlace(currentPlace.id)
                        else repository.markWorkPlace(currentPlace.id)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (currentPlace.isWork) "Quitar como trabajo" else "Marcar como trabajo")
            }

            Button(
                onClick = {
                    PlaceMapsLauncher.openInGoogleMaps(
                        context = context,
                        latitude = currentPlace.latitude,
                        longitude = currentPlace.longitude,
                        label = currentPlace.name
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Abrir en Google Maps")
            }

            Spacer(modifier = Modifier.height(4.dp))
            SectionRule(title = "Historial", count = events.take(20).size.takeIf { it > 0 })
            events.take(20).forEach { event ->
                TextButton(onClick = {}) {
                    Text(
                        "${dateFormat.format(Date(event.timestamp))} · ${DwellDurationFormatter.formatHours(event.timestamp, event.endTimestamp)}",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
