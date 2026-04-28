package com.trama.app.diagnostics

import android.content.Context
import android.net.Uri
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Recording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manual export for product-quality analysis.
 *
 * This is intentionally user-initiated: it may include raw transcripts and
 * discarded entries, because those are the examples needed to improve capture
 * quality. Nothing is uploaded automatically.
 */
object DiagnosticsExportManager {

    private const val WINDOW_HOURS = 72L
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Serializable
    data class Export(
        val version: Int = 1,
        val exportedAt: Long,
        val windowHours: Long,
        val summary: Summary,
        val analysis: DiagnosticsAnalyzer.Analysis,
        val events: List<CaptureLog.Event>,
        val entries: List<EntrySample>,
        val recordings: List<RecordingSample>,
        val analysisGuide: List<String> = defaultAnalysisGuide()
    )

    @Serializable
    data class Summary(
        val totalEvents: Int,
        val eventsByGateResult: Map<String, Int>,
        val totalEntries: Int,
        val entriesByStatus: Map<String, Int>,
        val entriesBySource: Map<String, Int>,
        val llmAccepted: Int,
        val llmRejected: Int,
        val discardedEntries: Int,
        val suggestedEntries: Int,
        val pendingEntries: Int,
        val recordings: Int,
        val recordingsWithActions: Int
    )

    @Serializable
    data class EntrySample(
        val id: Long,
        val createdAt: Long,
        val createdAtText: String,
        val status: String,
        val source: String,
        val text: String,
        val correctedText: String? = null,
        val cleanText: String? = null,
        val actionType: String,
        val confidence: Float,
        val llmConfidence: Float? = null,
        val wasReviewedByLLM: Boolean,
        val sourceRecordingId: Long? = null,
        val dueDate: Long? = null,
        val priority: String
    )

    @Serializable
    data class RecordingSample(
        val id: Long,
        val createdAt: Long,
        val createdAtText: String,
        val source: String,
        val status: String,
        val processedBy: String? = null,
        val durationSeconds: Int,
        val transcription: String,
        val title: String? = null,
        val summary: String? = null,
        val actionCount: Int
    )

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        repository: DiaryRepository,
        windowHours: Long = WINDOW_HOURS
    ): Summary = withContext(Dispatchers.IO) {
        val sinceMs = System.currentTimeMillis() - windowHours * 60L * 60L * 1000L
        val events = CaptureLog.recentEvents(sinceMs)
        val entries = repository.getAllOnce()
            .filter { it.createdAt >= sinceMs || (it.completedAt ?: 0L) >= sinceMs }
            .sortedByDescending { it.createdAt }
        val recordings = try {
            repository.getAllRecordingsOnce()
                .filter { it.createdAt >= sinceMs }
                .sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }

        val recordingActionCounts = entries
            .mapNotNull { entry -> entry.sourceRecordingId?.let { it to entry } }
            .groupingBy { it.first }
            .eachCount()

        val summary = Summary(
            totalEvents = events.size,
            eventsByGateResult = events
                .groupingBy { "${it.gate}:${it.result}" }
                .eachCount()
                .toSortedMap(),
            totalEntries = entries.size,
            entriesByStatus = entries.groupingBy { it.status }.eachCount().toSortedMap(),
            entriesBySource = entries.groupingBy { it.source.name }.eachCount().toSortedMap(),
            llmAccepted = events.count { it.gate == "LLM" && it.result == "OK" },
            llmRejected = events.count { it.gate == "LLM" && it.result == "REJECT" },
            discardedEntries = entries.count { it.status == "DISCARDED" },
            suggestedEntries = entries.count { it.status == "SUGGESTED" },
            pendingEntries = entries.count { it.status == "PENDING" },
            recordings = recordings.size,
            recordingsWithActions = recordingActionCounts.keys.size
        )

        val export = Export(
            exportedAt = System.currentTimeMillis(),
            windowHours = windowHours,
            summary = summary,
            analysis = DiagnosticsAnalyzer.analyze(events, entries, recordings),
            events = events,
            entries = entries.map { it.toSample() },
            recordings = recordings.map { recording ->
                recording.toSample(actionCount = recordingActionCounts[recording.id] ?: 0)
            }
        )

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.encodeToString(export).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Cannot open output stream")

        summary
    }

    fun fileName(now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
        return "trama-diagnostics-$stamp.json"
    }

    private fun DiaryEntry.toSample(): EntrySample =
        EntrySample(
            id = id,
            createdAt = createdAt,
            createdAtText = formatTime(createdAt),
            status = status,
            source = source.name,
            text = text,
            correctedText = correctedText,
            cleanText = cleanText,
            actionType = actionType,
            confidence = confidence,
            llmConfidence = llmConfidence,
            wasReviewedByLLM = wasReviewedByLLM,
            sourceRecordingId = sourceRecordingId,
            dueDate = dueDate,
            priority = priority
        )

    private fun Recording.toSample(actionCount: Int): RecordingSample =
        RecordingSample(
            id = id,
            createdAt = createdAt,
            createdAtText = formatTime(createdAt),
            source = source.name,
            status = processingStatus,
            processedBy = processedBy,
            durationSeconds = durationSeconds,
            transcription = transcription,
            title = title,
            summary = summary,
            actionCount = actionCount
        )

    private fun formatTime(value: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(value))

    private fun defaultAnalysisGuide(): List<String> = listOf(
        "Review DISCARDED entries for false negatives: real tasks that were hidden.",
        "Review SUGGESTED entries for ambiguity: should they be pending, note, or discard?",
        "Compare SERVICE heartbeats, ASR_GATE, ASR_FINAL, SAVE and LLM counts to locate loss in the pipeline.",
        "Long SERVICE heartbeat gaps suggest the foreground listener stopped; ASR_GATE without ASR_FINAL suggests heard speech with no accepted trigger.",
        "Cluster discarded text by expression type: microphone tests, conversational fragments, ASR corruption, missing object, duplicate.",
        "Estimate precision: pending tasks that are useful / all pending tasks.",
        "Estimate recall sample: discarded or unclear entries that should have become tasks."
    )
}
