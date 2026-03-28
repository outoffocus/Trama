package com.mydiary.shared.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries")
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
    val isManual: Boolean = false,           // true if manually entered by user
    // ActionItem fields
    val status: String = EntryStatus.PENDING,      // PENDING, COMPLETED, DISCARDED
    val actionType: String = EntryActionType.GENERIC, // CALL, BUY, SEND, EVENT, REVIEW, TALK_TO, GENERIC
    val cleanText: String? = null,                 // AI-cleaned summary (e.g. "Llamar al dentista")
    val dueDate: Long? = null,                     // due date timestamp if mentioned
    val completedAt: Long? = null,                 // when it was marked completed
    val priority: String = EntryPriority.NORMAL,   // LOW, NORMAL, HIGH, URGENT
    val duplicateOfId: Long? = null,                // ID of original entry if this is a duplicate
    val sourceRecordingId: Long? = null              // ID of the Recording this action was extracted from
) {
    /** Display text: cleanText > correctedText > text */
    val displayText: String get() = cleanText ?: correctedText ?: text
}

/** Entry lifecycle status */
object EntryStatus {
    const val PENDING = "PENDING"
    const val COMPLETED = "COMPLETED"
    const val DISCARDED = "DISCARDED"
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
