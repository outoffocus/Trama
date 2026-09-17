package com.trama.shared.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(
    tableName = "diary_entries",
    indices = [Index(value = ["parentEntryId", "sourceCaptureId"], name = "index_diary_entries_parent_source", unique = true)]
)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val keyword: String,
    val category: String,
    val confidence: Float,
    val createdAt: Long = System.currentTimeMillis(),
    val source: Source,
    val isSynced: Boolean = false,
    val duration: Int, // recording duration in seconds
    val correctedText: String? = null,       // LLM-corrected version of text
    val wasReviewedByLLM: Boolean = false,   // whether an LLM validated this entry
    val llmConfidence: Float? = null,        // LLM confidence score (0.0-1.0)
    val processingBackend: String? = null,   // CLOUD, LOCAL, HEURISTIC, or null when unknown/not processed
    val isManual: Boolean = false,           // true if manually entered by user
    // ActionItem fields
    val status: String = EntryStatus.PENDING,      // PENDING, COMPLETED, DISCARDED
    val actionType: String = EntryActionType.GENERIC, // CALL, BUY, SEND, EVENT, REVIEW, TALK_TO, GENERIC
    val cleanText: String? = null,                 // AI-cleaned summary (e.g. "Llamar al dentista")
    val dueDate: Long? = null,                     // due date timestamp if mentioned
    val completedAt: Long? = null,                 // when it was marked completed
    val priority: String = EntryPriority.NORMAL,   // LOW, NORMAL, HIGH, URGENT
    val duplicateOfId: Long? = null,                // ID of original entry if this is a duplicate
    val sourceRecordingId: Long? = null,             // ID of the Recording this action was extracted from
    val userConfirmedAt: Long? = null,               // explicit human confirmation timestamp
    val verificationSource: String? = null,          // where the human confirmation happened
    // Trust contract added in schema v18. A durable memory is never used as the
    // mutable action proposal: processing creates ACTION rows linked to it.
    val contentKind: String = EntryContentKind.ACTION,
    val sourceCaptureId: String? = null,
    val parentEntryId: Long? = null,
    val revision: Long = 0,
    val humanDecision: String? = null,
    val humanDecisionAt: Long? = null,
    val triggerPhrase: String? = null,
    val triggerConfigVersion: Long? = null,
    val externalState: String? = null,
    val externalEventId: Long? = null,
    val externalUpdatedAt: Long? = null
) {
    /** Display text: cleanText > raw Whisper text */
    val displayText: String
        get() = normalizeDisplayCasing(cleanText ?: text)

    private fun normalizeDisplayCasing(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return value

        val letters = trimmed.filter { it.isLetter() }
        if (letters.length < 4) return value

        val uppercaseLetters = letters.count { it.isUpperCase() }
        val uppercaseRatio = uppercaseLetters.toFloat() / letters.length.toFloat()
        val looksLikeShouting = uppercaseRatio >= 0.85f
        val hasLowercase = letters.any { it.isLowerCase() }

        return if (looksLikeShouting && !hasLowercase) {
            trimmed.lowercase(Locale.getDefault())
        } else {
            value
        }
    }
}

object EntryProcessingBackend {
    const val LOCAL = "LOCAL"
    const val HEURISTIC = "HEURISTIC"
}

object EntryVerificationSource {
    const val CALENDAR = "CALENDAR"
    const val AGENDA = "AGENDA"
    const val RECORDING = "RECORDING"
    const val DETAIL = "DETAIL"
}

/** Entry lifecycle status */
object EntryStatus {
    const val SAVED = "SAVED" // Durable memory; enrichment cannot hide it.
    const val PENDING = "PENDING"
    const val COMPLETED = "COMPLETED"
    const val DISCARDED = "DISCARDED"
    const val SUGGESTED = "SUGGESTED" // Extracted from recording, awaiting user confirmation
}

object EntryContentKind {
    const val MEMORY = "MEMORY"
    const val ACTION = "ACTION"
}

object EntryHumanDecision {
    const val ACCEPTED = "ACCEPTED"
    const val COMPLETED = "COMPLETED"
    const val DISCARDED = "DISCARDED"
}

object EntryExternalState {
    const val SCHEDULED = "SCHEDULED"
    const val EDITOR_OPENED = "EDITOR_OPENED"
    const val FAILED = "FAILED"
}

/** Action type detected by AI */
object EntryActionType {
    const val CALL = "CALL"
    const val BUY = "BUY"
    const val SEND = "SEND"
    const val EVENT = "EVENT"
    const val REVIEW = "REVIEW"
    const val TALK_TO = "TALK_TO"
    const val GENERIC = "GENERIC"

    fun emoji(type: String): String = when (type) {
        CALL -> "\uD83D\uDCDE"
        BUY -> "\uD83D\uDED2"
        SEND -> "\u2709\uFE0F"
        EVENT -> "\uD83D\uDCC5"
        REVIEW -> "\uD83D\uDD0D"
        TALK_TO -> "\uD83D\uDCAC"
        else -> "\u2610"
    }

    fun label(type: String): String = when (type) {
        CALL -> "Llamar"
        BUY -> "Comprar"
        SEND -> "Enviar"
        EVENT -> "Evento"
        REVIEW -> "Revisar"
        TALK_TO -> "Hablar con"
        else -> "Tarea"
    }
}

/** Priority levels */
object EntryPriority {
    const val LOW = "LOW"
    const val NORMAL = "NORMAL"
    const val HIGH = "HIGH"
    const val URGENT = "URGENT"
}
