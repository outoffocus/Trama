package com.trama.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trama.app.summary.DailyPageGenerator
import com.trama.app.summary.DailyReviewSnapshot
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DailyPageStatus
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Place
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val MAX_TASKS_RENDER = 30
private const val MAX_DUPLICATES_RENDER = 12
private const val MAX_PLACES_RENDER = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    onBack: () -> Unit,
    onEntryClick: (Long) -> Unit,
    onPlaceClick: (Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DatabaseProvider.getRepository(context) }
    val generator = remember { DailyPageGenerator(context) }
    val dayStartMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val endOfDay = remember(dayStartMillis) {
        com.trama.shared.util.DayRange.of(dayStartMillis).endInclusiveMs
    }
    val headerDate = remember(dayStartMillis) {
        SimpleDateFormat("EEEE d 'de' MMMM", Locale("es"))
            .format(Date(dayStartMillis))
            .replaceFirstChar { it.uppercase() }
    }

    val dailyPage by repository.getDailyPage(dayStartMillis).collectAsState(initial = null)
    var snapshot by remember { mutableStateOf<DailyReviewSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var postponeEntry by remember { mutableStateOf<DiaryEntry?>(null) }

    fun reloadSnapshot() {
        scope.launch {
            loading = true
            error = null
            try {
                snapshot = generator.buildSnapshot(dayStartMillis)
            } catch (t: Throwable) {
                error = t.message ?: "No se pudo cargar la revision del dia."
            } finally {
                loading = false
            }
        }
    }

    fun refresh(final: Boolean = false) {
        scope.launch {
            loading = true
            error = null
            try {
                generator.generateAndPersist(
                    dayStartMillis = dayStartMillis,
                    status = if (final) DailyPageStatus.FINAL else DailyPageStatus.DRAFT
                )
                snapshot = generator.buildSnapshot(dayStartMillis)
            } catch (t: Throwable) {
                error = t.message ?: "No se pudo actualizar la revision del dia."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(dayStartMillis) {
        reloadSnapshot()
    }

    val tasksToReview = (snapshot?.tasksToReview ?: emptyList()).take(MAX_TASKS_RENDER)
    val duplicateOverflow = ((snapshot?.duplicatesToReview?.size ?: 0) - MAX_DUPLICATES_RENDER).coerceAtLeast(0)
    val duplicatesToReview = (snapshot?.duplicatesToReview ?: emptyList()).take(MAX_DUPLICATES_RENDER)
    val placeOverflow = ((snapshot?.placesToReview?.size ?: 0) - MAX_PLACES_RENDER).coerceAtLeast(0)
    val placesToReview = (snapshot?.placesToReview ?: emptyList()).take(MAX_PLACES_RENDER)
    val completedToday = snapshot?.completedToday ?: emptyList()
    val briefSummary = dailyPage?.briefSummary?.takeIf { it.isNotBlank() }
        ?: "Todavia no hay una pagina diaria consolidada. Puedes regenerarla manualmente."

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Revision del dia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("hero") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = headerDate,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = briefSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (dailyPage?.status == DailyPageStatus.FINAL) {
                            Text(
                                "Cierre diario generado y markdown guardado en memoria local.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item("tasks_header") {
                ReviewSectionHeader(
                    title = "Tareas por revisar",
                    subtitle = "${tasksToReview.size} abiertas · ${completedToday.size} completadas hoy"
                )
            }
            if (tasksToReview.isEmpty()) {
                item("tasks_empty") {
                    EmptyReviewCard(
                        title = "Nada urgente por limpiar",
                        body = "Las tareas abiertas del dia ya estan bastante ordenadas."
                    )
                }
            } else {
                items(tasksToReview, key = { "task_${it.id}" }) { entry ->
                    TaskReviewCard(
                        entry = entry,
                        onEdit = { onEntryClick(entry.id) },
                        onDone = {
                            scope.launch {
                                repository.markCompleted(entry.id)
                                repository.markDailyPageReviewed(dayStartMillis)
                                reloadSnapshot()
                            }
                        },
                        onPostpone = { postponeEntry = entry }
                    )
                }
            }

            if (duplicatesToReview.isNotEmpty()) {
                item("duplicates_header") {
                    ReviewSectionHeader(
                        title = "Duplicados por resolver",
                        subtitle = buildString {
                            append("${snapshot?.duplicatesToReview?.size ?: duplicatesToReview.size} recordatorios se parecen demasiado entre si")
                            if (duplicateOverflow > 0) append(" · mostrando $MAX_DUPLICATES_RENDER")
                        }
                    )
                }
                items(duplicatesToReview, key = { "dup_${it.id}" }) { entry ->
                    DuplicateReviewCard(
                        entry = entry,
                        onKeep = {
                            scope.launch {
                                repository.clearDuplicate(entry.id)
                                repository.markDailyPageReviewed(dayStartMillis)
                                reloadSnapshot()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                repository.deleteById(entry.id)
                                repository.markDailyPageReviewed(dayStartMillis)
                                reloadSnapshot()
                            }
                        },
                        onOpen = { onEntryClick(entry.id) }
                    )
                }
            }

            item("places_header") {
                ReviewSectionHeader(
                    title = "Sitios por valorar",
                    subtitle = if (placesToReview.isEmpty()) {
                        "No tienes sitios pendientes de comentar hoy"
                    } else {
                        buildString {
                            append("${snapshot?.placesToReview?.size ?: placesToReview.size} lugares merecen una opinion rapida")
                            if (placeOverflow > 0) append(" · mostrando $MAX_PLACES_RENDER")
                        }
                    }
                )
            }
            if (placesToReview.isEmpty()) {
                item("places_empty") {
                    EmptyReviewCard(
                        title = "Sitios al dia",
                        body = "Los lugares visitados hoy ya tienen valoracion o comentario."
                    )
                }
            } else {
                items(placesToReview, key = { "place_${it.id}" }) { place ->
                    PlaceReviewCard(
                        place = place,
                        onOpenDetail = { onPlaceClick(place.id) },
                        onRate = { rating ->
                            scope.launch {
                                repository.updatePlaceOpinion(
                                    id = place.id,
                                    rating = rating,
                                    opinionText = place.opinionText,
                                    opinionSummary = place.opinionSummary,
                                    opinionUpdatedAt = System.currentTimeMillis()
                                )
                                repository.markDailyPageReviewed(dayStartMillis)
                                reloadSnapshot()
                            }
                        }
                    )
                }
            }

            item("summary_note") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Resumen breve del dia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            briefSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        dailyPage?.markdownPath?.let {
                            Text(
                                "Markdown tecnico: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (loading) {
                item("loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            error?.let { message ->
                item("error") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    postponeEntry?.let { entry ->
        PostponeEntryDialog(
            entry = entry,
            onDismiss = { postponeEntry = null },
            onPostpone = { dueDate ->
                scope.launch {
                    repository.updateDueDate(entry.id, dueDate)
                    repository.markDailyPageReviewed(dayStartMillis)
                    postponeEntry = null
                    reloadSnapshot()
                }
            }
        )
    }
}

@Composable
private fun ReviewSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyReviewCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskReviewCard(
    entry: DiaryEntry,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onPostpone: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                entry.displayText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                buildTaskMeta(entry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Corregir")
                }
                FilledTonalButton(onClick = onDone) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Hecha")
                }
                OutlinedButton(onClick = onPostpone) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Posponer")
                }
            }
        }
    }
}

private fun buildTaskMeta(entry: DiaryEntry): String {
    val dateFormat = SimpleDateFormat("d MMM · HH:mm", Locale("es"))
    val created = dateFormat.format(Date(entry.createdAt))
    val due = entry.dueDate?.let { " · vence ${dateFormat.format(Date(it))}" }.orEmpty()
    return "Creada $created$due"
}

@Composable
private fun DuplicateReviewCard(
    entry: DiaryEntry,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(entry.displayText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Esta entrada aparece como posible duplicado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen) { Text("Abrir") }
                OutlinedButton(onClick = onKeep) { Text("Conservar") }
                FilledTonalButton(onClick = onDelete) { Text("Eliminar") }
            }
        }
    }
}

@Composable
private fun PlaceReviewCard(
    place: Place,
    onOpenDetail: () -> Unit,
    onRate: suspend (Int?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var rating by remember(place.id, place.rating) { mutableStateOf(place.rating ?: 0) }
    var saving by remember(place.id) { mutableStateOf(false) }
    var localError by remember(place.id) { mutableStateOf<String?>(null) }

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Valora rapido este lugar o abre su ficha para comentar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { star ->
                    InputChip(
                        selected = rating == star,
                        onClick = {
                            scope.launch {
                                saving = true
                                localError = null
                                try {
                                    rating = star
                                    onRate(star)
                                } catch (t: Throwable) {
                                    localError = t.message ?: "No se pudo guardar la valoracion."
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        label = { Text("$star") },
                        enabled = !saving
                    )
                }
            }

            place.opinionSummary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            place.opinionText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            localError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenDetail) {
                    Text(if (place.opinionText.isNullOrBlank()) "Comentar" else "Abrir ficha")
                }
            }
        }
    }
}

@Composable
private fun PostponeEntryDialog(
    entry: DiaryEntry,
    onDismiss: () -> Unit,
    onPostpone: (Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Posponer tarea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    entry.displayText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onPostpone(resolveTomorrow(entry.dueDate)) },
                        label = { Text("Mañana") }
                    )
                    AssistChip(
                        onClick = { onPostpone(resolveNextWeek(entry.dueDate)) },
                        label = { Text("Semana que viene") }
                    )
                }
                TextButton(
                    onClick = {
                        val today = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                onPostpone(resolveCustomDate(year, month, dayOfMonth, entry.dueDate))
                            },
                            today.get(Calendar.YEAR),
                            today.get(Calendar.MONTH),
                            today.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                ) {
                    Text("Elegir fecha")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun preferredHour(previousDueDate: Long?): Int =
    previousDueDate?.let {
        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.HOUR_OF_DAY)
    } ?: 9

private fun preferredMinute(previousDueDate: Long?): Int =
    previousDueDate?.let {
        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.MINUTE)
    } ?: 0

private fun resolveTomorrow(previousDueDate: Long?): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, preferredHour(previousDueDate))
        set(Calendar.MINUTE, preferredMinute(previousDueDate))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun resolveNextWeek(previousDueDate: Long?): Long =
    Calendar.getInstance().apply {
        add(Calendar.WEEK_OF_YEAR, 1)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, preferredHour(previousDueDate))
        set(Calendar.MINUTE, preferredMinute(previousDueDate))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun resolveCustomDate(
    year: Int,
    month: Int,
    dayOfMonth: Int,
    previousDueDate: Long?
): Long =
    Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, dayOfMonth)
        set(Calendar.HOUR_OF_DAY, preferredHour(previousDueDate))
        set(Calendar.MINUTE, preferredMinute(previousDueDate))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
