package com.trama.app.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.DailyPage
import com.trama.shared.model.DwellDetectionState
import com.trama.shared.model.Place
import com.trama.shared.model.Recording
import com.trama.shared.model.Source
import com.trama.shared.model.TimelineEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Handles backup/restore of diary entries via JSON files.
 * Uses Storage Access Framework (SAF) so user can save to Google Drive,
 * Downloads, or any storage provider — zero configuration needed.
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class Backup(
        val version: Int = 3,
        val exportedAt: Long = System.currentTimeMillis(),
        val entries: List<BackupEntry>,
        val recordings: List<BackupRecording> = emptyList(),
        val timelineEvents: List<BackupTimelineEvent> = emptyList(),
        val places: List<BackupPlace> = emptyList(),
        val dwellDetectionState: BackupDwellDetectionState? = null,
        val dailyPages: List<BackupDailyPage> = emptyList()
    )

    @Serializable
    data class BackupEntry(
        val id: Long = 0,
        val text: String,
        val keyword: String,
        val category: String,
        val confidence: Float,
        val source: String,
        val duration: Int,
        val createdAt: Long,
        val correctedText: String? = null,
        val wasReviewedByLLM: Boolean = false,
        val llmConfidence: Float? = null,
        val status: String? = null,
        val actionType: String? = null,
        val cleanText: String? = null,
        val dueDate: Long? = null,
        val completedAt: Long? = null,
        val priority: String? = null,
        val processingBackend: String? = null,
        val isManual: Boolean = false,
        val isSynced: Boolean = false,
        val duplicateOfId: Long? = null,
        val sourceRecordingId: Long? = null
    )

    @Serializable
    data class BackupRecording(
        val id: Long = 0,
        val title: String? = null,
        val transcription: String,
        val summary: String? = null,
        val keyPoints: String? = null,
        val durationSeconds: Int,
        val source: String,
        val createdAt: Long,
        val processingStatus: String = "PENDING",
        val processedLocally: Boolean = false,
        val processedBy: String? = null,
        val isSynced: Boolean = false,
        val audioSampleRateHz: Int = 16_000
    )

    @Serializable
    data class BackupTimelineEvent(
        val id: Long = 0,
        val type: String,
        val timestamp: Long,
        val endTimestamp: Long? = null,
        val title: String,
        val subtitle: String? = null,
        val dataJson: String? = null,
        val isHighlight: Boolean = false,
        val placeId: Long? = null,
        val source: String = "AUTO",
        val createdAt: Long,
        val completedAt: Long? = null
    )

    @Serializable
    data class BackupPlace(
        val id: Long = 0,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val type: String? = null,
        val visitCount: Int = 0,
        val lastVisitAt: Long? = null,
        val rating: Int? = null,
        val opinionText: String? = null,
        val opinionSummary: String? = null,
        val opinionUpdatedAt: Long? = null,
        val isHome: Boolean = false,
        val isWork: Boolean = false,
        val userRenamed: Boolean = false,
        val createdAt: Long,
        val updatedAt: Long
    )

    @Serializable
    data class BackupDwellDetectionState(
        val candidateLat: Double? = null,
        val candidateLon: Double? = null,
        val candidateStartedAt: Long? = null,
        val candidateLastSeenAt: Long? = null,
        val anchorLat: Double? = null,
        val anchorLon: Double? = null,
        val dwellStartedAt: Long? = null,
        val active: Boolean = false,
        val updatedAt: Long,
        val lastClosedLat: Double? = null,
        val lastClosedLon: Double? = null,
        val lastClosedAt: Long? = null
    )

    @Serializable
    data class BackupDailyPage(
        val dayStartMillis: Long,
        val date: String,
        val status: String,
        val briefSummary: String? = null,
        val insightsJson: String = "",
        val markdown: String = "",
        val markdownPath: String? = null,
        val generatedAt: Long,
        val updatedAt: Long,
        val reviewedAt: Long? = null,
        val hasManualReview: Boolean = false
    )

    /**
     * Export all entries + recordings to JSON and write to the given URI.
     * @return number of entries exported
     */
    suspend fun exportToUri(context: Context, uri: Uri, repository: DiaryRepository): Int {
        return withContext(Dispatchers.IO) {
            val backup = createBackup(repository)
            DurableBackupWriter.write(context, uri, encode(backup))
            val count = entityCount(backup)
            Log.i(TAG, "Exported $count entities to $uri")
            count
        }
    }

    suspend fun createBackup(repository: DiaryRepository): Backup = repository.withTransaction {
        Backup(
            entries = getAllOnce().map { it.toBackupEntry() },
            recordings = getAllRecordingsOnce().map { it.toBackupRecording() },
            timelineEvents = getAllTimelineEventsOnce().map { it.toBackupTimelineEvent() },
            places = getAllPlacesOnce().map { it.toBackupPlace() },
            dwellDetectionState = getDwellDetectionState()?.toBackupDwellState(),
            dailyPages = getAllDailyPagesOnce().map { it.toBackupDailyPage() }
        )
    }

    fun encode(backup: Backup): String = json.encodeToString(backup)

    fun decode(value: String): Backup = json.decodeFromString(value)

    fun entityCount(backup: Backup): Int = backup.entries.size + backup.recordings.size +
        backup.timelineEvents.size + backup.places.size + backup.dailyPages.size +
        if (backup.dwellDetectionState != null) 1 else 0

    /**
     * Import entries from a JSON backup file.
     * Skips duplicates (same createdAt + text).
     * @return Pair(imported, skipped)
     */
    suspend fun importFromUri(context: Context, uri: Uri, repository: DiaryRepository): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            val jsonStr = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: throw Exception("Cannot open input stream")

            val backup = decode(jsonStr)
            var imported = 0
            var skipped = 0
            repository.withTransaction {
                val recordingIds = restoreRecordings(backup.recordings)
                val placeIds = restorePlaces(backup.places)
                val entryIds = mutableMapOf<Long, Long>()
                backup.entries.forEach { entry ->
                    val existing = getByCreatedAtAndText(entry.createdAt, entry.text)
                    val restoredId = existing?.id ?: insert(
                        entry.toDiaryEntry(
                            sourceRecordingId = entry.sourceRecordingId?.let(recordingIds::get),
                            duplicateOfId = null
                        )
                    )
                    if (existing == null) imported++ else skipped++
                    if (entry.id > 0L) entryIds[entry.id] = restoredId
                }
                backup.entries.forEach { entry ->
                    val restoredId = entryIds[entry.id] ?: return@forEach
                    val duplicateId = entry.duplicateOfId?.let(entryIds::get) ?: return@forEach
                    markDuplicate(restoredId, duplicateId)
                }
                backup.timelineEvents.forEach { event ->
                    if (getTimelineEventByNaturalKey(event.type, event.timestamp, event.title) == null) {
                        insertTimelineEvent(event.toTimelineEvent(event.placeId?.let(placeIds::get)))
                    }
                }
                backup.dailyPages.forEach { upsertDailyPage(it.toDailyPage()) }
                backup.dwellDetectionState?.let { saveDwellDetectionState(it.toDwellState()) }
            }

            Log.i(TAG, "Imported $imported entries (skipped $skipped) from backup v${backup.version}")
            Pair(imported, skipped)
        }
    }

    /**
     * Get backup file name with date.
     */
    fun getBackupFileName(): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(System.currentTimeMillis())
        return "trama-backup-$date.json"
    }

    private fun DiaryEntry.toBackupEntry() = BackupEntry(
        id = id,
        text = text,
        keyword = keyword,
        category = category,
        confidence = confidence,
        source = source.name,
        duration = duration,
        createdAt = createdAt,
        correctedText = correctedText,
        wasReviewedByLLM = wasReviewedByLLM,
        llmConfidence = llmConfidence,
        status = status,
        actionType = actionType,
        cleanText = cleanText,
        dueDate = dueDate,
        completedAt = completedAt,
        priority = priority,
        processingBackend = processingBackend,
        isManual = isManual,
        isSynced = isSynced,
        duplicateOfId = duplicateOfId,
        sourceRecordingId = sourceRecordingId
    )

    private fun Recording.toBackupRecording() = BackupRecording(
        id = id,
        title = title,
        transcription = transcription,
        summary = summary,
        keyPoints = keyPoints,
        durationSeconds = durationSeconds,
        source = source.name,
        createdAt = createdAt,
        processingStatus = processingStatus,
        processedLocally = processedLocally,
        processedBy = processedBy,
        isSynced = isSynced,
        audioSampleRateHz = audioSampleRateHz
    )

    private fun BackupRecording.toRecording() = Recording(
        title = title,
        transcription = transcription,
        summary = summary,
        keyPoints = keyPoints,
        durationSeconds = durationSeconds,
        source = sourceValue(source),
        createdAt = createdAt,
        processingStatus = processingStatus,
        processedLocally = processedLocally,
        processedBy = processedBy,
        isSynced = isSynced,
        audioSampleRateHz = audioSampleRateHz
    )

    private fun BackupEntry.toDiaryEntry(
        sourceRecordingId: Long?,
        duplicateOfId: Long?
    ) = DiaryEntry(
        text = text,
        keyword = keyword,
        category = category,
        confidence = confidence,
        source = sourceValue(source),
        isSynced = isSynced,
        duration = duration,
        createdAt = createdAt,
        correctedText = correctedText,
        wasReviewedByLLM = wasReviewedByLLM,
        llmConfidence = llmConfidence,
        status = status ?: com.trama.shared.model.EntryStatus.PENDING,
        actionType = actionType ?: com.trama.shared.model.EntryActionType.GENERIC,
        cleanText = cleanText,
        dueDate = dueDate,
        completedAt = completedAt,
        priority = priority ?: com.trama.shared.model.EntryPriority.NORMAL,
        processingBackend = processingBackend,
        isManual = isManual,
        duplicateOfId = duplicateOfId,
        sourceRecordingId = sourceRecordingId
    )

    private suspend fun DiaryRepository.restoreRecordings(
        recordings: List<BackupRecording>
    ): Map<Long, Long> = buildMap {
        recordings.forEach { recording ->
            val existing = getRecordingByCreatedAt(recording.createdAt)
            val restoredId = existing?.id ?: insertRecording(recording.toRecording())
            if (recording.id > 0L) put(recording.id, restoredId)
        }
    }

    private suspend fun DiaryRepository.restorePlaces(
        places: List<BackupPlace>
    ): Map<Long, Long> = buildMap {
        places.forEach { place ->
            val existing = getPlaceByNaturalKey(place.name, place.latitude, place.longitude)
            val restoredId = existing?.id ?: insertPlace(place.toPlace())
            if (place.id > 0L) put(place.id, restoredId)
        }
    }

    private fun TimelineEvent.toBackupTimelineEvent() = BackupTimelineEvent(
        id, type, timestamp, endTimestamp, title, subtitle, dataJson, isHighlight,
        placeId, source, createdAt, completedAt
    )

    private fun BackupTimelineEvent.toTimelineEvent(restoredPlaceId: Long?) = TimelineEvent(
        type = type,
        timestamp = timestamp,
        endTimestamp = endTimestamp,
        title = title,
        subtitle = subtitle,
        dataJson = dataJson,
        isHighlight = isHighlight,
        placeId = restoredPlaceId,
        source = source,
        createdAt = createdAt,
        completedAt = completedAt
    )

    private fun Place.toBackupPlace() = BackupPlace(
        id, name, latitude, longitude, type, visitCount, lastVisitAt, rating,
        opinionText, opinionSummary, opinionUpdatedAt, isHome, isWork, userRenamed,
        createdAt, updatedAt
    )

    private fun BackupPlace.toPlace() = Place(
        name = name,
        latitude = latitude,
        longitude = longitude,
        type = type,
        visitCount = visitCount,
        lastVisitAt = lastVisitAt,
        rating = rating,
        opinionText = opinionText,
        opinionSummary = opinionSummary,
        opinionUpdatedAt = opinionUpdatedAt,
        isHome = isHome,
        isWork = isWork,
        userRenamed = userRenamed,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun DwellDetectionState.toBackupDwellState() = BackupDwellDetectionState(
        candidateLat, candidateLon, candidateStartedAt, candidateLastSeenAt,
        anchorLat, anchorLon, dwellStartedAt, active, updatedAt,
        lastClosedLat, lastClosedLon, lastClosedAt
    )

    private fun BackupDwellDetectionState.toDwellState() = DwellDetectionState(
        // Never resume an old active dwell after restoring a potentially old backup.
        active = false,
        updatedAt = updatedAt,
        lastClosedLat = lastClosedLat,
        lastClosedLon = lastClosedLon,
        lastClosedAt = lastClosedAt
    )

    private fun DailyPage.toBackupDailyPage() = BackupDailyPage(
        dayStartMillis, date, status, briefSummary, insightsJson, markdown,
        markdownPath, generatedAt, updatedAt, reviewedAt, hasManualReview
    )

    private fun BackupDailyPage.toDailyPage() = DailyPage(
        dayStartMillis, date, status, briefSummary, insightsJson, markdown,
        markdownPath, generatedAt, updatedAt, reviewedAt, hasManualReview
    )

    private fun sourceValue(value: String): Source =
        runCatching { Source.valueOf(value) }.getOrDefault(Source.PHONE)
}
