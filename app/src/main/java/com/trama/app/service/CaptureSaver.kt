package com.trama.app.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.trama.app.diagnostics.CaptureLog
import com.trama.app.summary.ActionItemProcessor
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Persists a detected capture to the diary. Applies the persisted dedup gate
 * inside a DB transaction, fires the new-entry notification, and kicks off
 * the AI post-processing. Status callbacks let the service emit ASR debug
 * events without coupling this component to service internals.
 */
class CaptureSaver(
    private val context: Context,
    private val dedup: DeduplicationManager,
    private val notifier: ServiceNotifier,
    private val scope: CoroutineScope,
    private val repoProvider: () -> DiaryRepository?,
    private val onStatus: (String) -> Unit,
    private val onEntrySaved: suspend () -> Unit
) {

    fun save(
        intentId: String,
        label: String,
        text: String,
        originalText: String,
        llmConfidence: Float,
        wasReviewed: Boolean,
        confidence: Float
    ) {
        scope.launch(Dispatchers.IO) {
            val repo = repoProvider() ?: return@launch
            val capturedText = originalText.ifBlank { text }
            val entry = DiaryEntry(
                text = capturedText,
                keyword = intentId,
                category = label,
                confidence = confidence,
                source = Source.PHONE,
                duration = 0,
                correctedText = text,
                wasReviewedByLLM = wasReviewed,
                llmConfidence = llmConfidence,
                cleanText = null
            )
            val entryId = dedup.withSaveLock {
                repo.withTransaction {
                    val latest = getLatestPendingOnce()
                    if (dedup.isDuplicateOfLatestPending(latest, capturedText, text)) {
                        return@withTransaction null
                    }
                    insert(entry)
                }
            }

            if (entryId == null) {
                Log.i(TAG, "Persisted dedup: skipping recently saved duplicate '$text'")
                onStatus("duplicado reciente ignorado")
                CaptureLog.event(
                    gate = CaptureLog.Gate.DEDUP_SEM,
                    result = CaptureLog.Result.DUP,
                    text = text,
                    meta = mapOf("intent" to intentId)
                )
                return@launch
            }
            Log.i(
                TAG,
                "Entry saved: raw='$capturedText' corrected='$text' " +
                    "(intent: $intentId, label: $label, reviewed: $wasReviewed)"
            )
            CaptureLog.event(
                gate = CaptureLog.Gate.SAVE,
                result = CaptureLog.Result.OK,
                text = text,
                meta = mapOf("id" to entryId, "intent" to intentId, "label" to label)
            )
            onStatus("entrada guardada")
            notifier.showNewEntry(entry)
            onEntrySaved()

            EntryProcessingState.markProcessing(entryId)
            var acceptedForTimeline = false
            try {
                acceptedForTimeline = ActionItemProcessor(context).process(entryId, text, repo)
            } catch (e: Exception) {
                Log.w(TAG, "ActionItemProcessor failed for entry $entryId", e)
            } finally {
                EntryProcessingState.markFinished(entryId)
            }
            if (acceptedForTimeline) {
                notifyTimelineActionAdded()
            }
        }
    }

    private fun notifyTimelineActionAdded() {
        try {
            val pattern = longArrayOf(0, 45)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                if (vibrator?.hasVibrator() == true) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Vibrator::class.java)
                if (vibrator?.hasVibrator() == true) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Timeline action vibration failed", e)
        }
    }

    companion object {
        private const val TAG = "CaptureSaver"
    }
}
