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
        val version: Int = 2,
        val exportedAt: Long,
        val windowHours: Long,
        val summary: Summary,
        val analysis: DiagnosticsAnalyzer.Analysis,
        val qualityDecisions: List<QualityDecisionSample>,
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
        val qualityReviewNeeded: Int,
        val discardedPossibleFalseNegatives: Int,
        val acceptedLowConfidence: Int,
        val mediaPlaybackPauses: Int,
        val mediaPlaybackBlockedWindows: Int,
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

    @Serializable
    data class QualityDecisionSample(
        val id: Long,
        val createdAt: Long,
        val createdAtText: String,
        val status: String,
        val source: String,
        val rawText: String,
        val correctedText: String? = null,
        val cleanText: String? = null,
        val actionType: String,
        val confidence: Float,
        val llmConfidence: Float? = null,
        val wasReviewedByLLM: Boolean,
        val llmResult: String? = null,
        val llmKind: String? = null,
        val llmDecision: String? = null,
        val llmRoute: String? = null,
        val modelIsActionable: Boolean? = null,
        val usefulnessScore: Float? = null,
        val actionabilityScore: Float? = null,
        val discardReason: String? = null,
        val qualityBucket: String,
        val needsHumanReview: Boolean,
        val reviewHint: String
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
        val llmEventsByEntryId = events
            .filter { it.gate == "LLM" }
            .mapNotNull { event ->
                (event.meta["entryId"] ?: event.meta["id"])?.toLongOrNull()?.let { id -> id to event }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, value) -> value.maxByOrNull { it.ts } }
        val qualityDecisions = entries.map { entry ->
            entry.toQualityDecision(llmEventsByEntryId[entry.id])
        }

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
            qualityReviewNeeded = qualityDecisions.count { it.needsHumanReview },
            discardedPossibleFalseNegatives = qualityDecisions.count {
                it.qualityBucket == "discarded_possible_false_negative"
            },
            acceptedLowConfidence = qualityDecisions.count {
                it.qualityBucket == "accepted_low_confidence"
            },
            mediaPlaybackPauses = events.count {
                it.gate == "SERVICE" && it.text == "media_playback_pause"
            },
            mediaPlaybackBlockedWindows = events.count {
                (it.gate == "ASR_FINAL" && it.text == "media_playback_blocked_window") ||
                    (it.gate == "ASR_GATE" && it.text == "media_playback_gate_blocked")
            },
            recordings = recordings.size,
            recordingsWithActions = recordingActionCounts.keys.size
        )

        val export = Export(
            exportedAt = System.currentTimeMillis(),
            windowHours = windowHours,
            summary = summary,
            analysis = DiagnosticsAnalyzer.analyze(events, entries, recordings),
            qualityDecisions = qualityDecisions,
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

    private fun DiaryEntry.toQualityDecision(llmEvent: CaptureLog.Event?): QualityDecisionSample {
        val bucket = llmEvent?.meta?.get("qualityBucket") ?: fallbackQualityBucket(this, llmEvent)
        val needsHumanReview = bucket in REVIEW_BUCKETS ||
            status == "DISCARDED" ||
            status == "SUGGESTED" ||
            llmEvent == null ||
            llmConfidence?.let { it < 0.65f } == true
        return QualityDecisionSample(
            id = id,
            createdAt = createdAt,
            createdAtText = formatTime(createdAt),
            status = status,
            source = source.name,
            rawText = text,
            correctedText = correctedText,
            cleanText = cleanText,
            actionType = actionType,
            confidence = confidence,
            llmConfidence = llmConfidence,
            wasReviewedByLLM = wasReviewedByLLM,
            llmResult = llmEvent?.result,
            llmKind = llmEvent?.meta?.get("kind"),
            llmDecision = llmEvent?.meta?.get("decision"),
            llmRoute = llmEvent?.meta?.get("route"),
            modelIsActionable = llmEvent?.meta?.get("isActionable")?.toBooleanStrictOrNull(),
            usefulnessScore = llmEvent?.meta?.get("usefulness")?.toFloatOrNull(),
            actionabilityScore = llmEvent?.meta?.get("actionability")?.toFloatOrNull(),
            discardReason = llmEvent?.meta?.get("discardReason"),
            qualityBucket = bucket,
            needsHumanReview = needsHumanReview,
            reviewHint = llmEvent?.meta?.get("reviewHint") ?: fallbackReviewHint(this, bucket)
        )
    }

    private fun fallbackQualityBucket(entry: DiaryEntry, llmEvent: CaptureLog.Event?): String = when {
        llmEvent == null -> "missing_llm_decision"
        entry.status == "PENDING" && (entry.llmConfidence ?: entry.confidence) < 0.65f -> "accepted_low_confidence"
        entry.status == "PENDING" || entry.status == "COMPLETED" -> "accepted_action"
        entry.status == "SUGGESTED" -> "ambiguous_suggested"
        entry.status == "DISCARDED" -> "discarded_needs_review"
        else -> "unknown"
    }

    private fun fallbackReviewHint(entry: DiaryEntry, bucket: String): String = when (bucket) {
        "missing_llm_decision" -> "No hay evento LLM enlazado: revisar pipeline/log."
        "accepted_low_confidence" -> "Revisar precision: aceptada con confianza baja."
        "discarded_possible_false_negative", "discarded_needs_review" ->
            "Revisar recall: confirmar si era una accion util descartada."
        "ambiguous_suggested" -> "Revisar si debe pasar a tarea pendiente o descartarse."
        "accepted_action" -> "Muestra positiva: confirmar si la tarea aceptada era util."
        else -> "Revisar manualmente."
    }

    private fun formatTime(value: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(value))

    private fun defaultAnalysisGuide(): List<String> = listOf(
        "Review DISCARDED entries for false negatives: real tasks that were hidden.",
        "Review SUGGESTED entries for ambiguity: should they be pending, note, or discard?",
        "Compare SERVICE heartbeats, ASR_GATE, ASR_FINAL, SAVE and LLM counts to locate loss in the pipeline.",
        "Long SERVICE heartbeat gaps suggest the foreground listener stopped; ASR_GATE without ASR_FINAL suggests heard speech with no accepted trigger.",
        "Compare segment_finalized reasons: silence_stop is normal; unmatched_segment_cap means continuous speech/noise was rotated at 30s instead of getting stuck.",
        "Compare ASR_FINAL source=trigger vs source=uncertain_fallback; the latter should be rare and explains battery-sensitive recall attempts.",
        "Review uncertain_gate_fallback_blocked reason=battery_low/cooldown to see when fallback was intentionally skipped.",
        "Review SERVICE media_playback_pause and ASR media_playback_* events to confirm YouTube/Spotify audio was ignored instead of saved.",
        "Cluster discarded text by expression type: microphone tests, conversational fragments, ASR corruption, missing object, duplicate.",
        "Use qualityDecisions as the human-labelling table: label useful accepted items as true positives, useless accepted items as false positives, useful discarded/suggested items as false negatives.",
        "Start reviewing rows with needsHumanReview=true, especially discarded_possible_false_negative, accepted_low_confidence, ambiguous_suggested, and missing_llm_decision.",
        "Estimate precision: pending tasks that are useful / all pending tasks.",
        "Estimate recall sample: discarded or unclear entries that should have become tasks."
    )

    private val REVIEW_BUCKETS = setOf(
        "accepted_low_confidence",
        "ambiguous_suggested",
        "discarded_possible_false_negative",
        "discarded_needs_review",
        "missing_llm_decision"
    )
}
