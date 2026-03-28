package com.mydiary.app.summary

import kotlinx.serialization.Serializable

/**
 * An action suggested by the LLM after analyzing the day's entries.
 */
@Serializable
data class SuggestedAction(
    val type: ActionType,
    val title: String,
    val description: String = "",
    /** ISO date/time string if applicable (e.g. "2026-03-19T10:00") */
    val datetime: String? = null,
    /** Contact name if applicable */
    val contact: String? = null,
    /** Whether the user has executed this action */
    val done: Boolean = false,
    /** IDs of diary entries this action was derived from */
    val entryIds: List<Long> = emptyList(),
    /** Timestamp when the source entry was captured (millis) */
    val capturedAt: Long? = null
)

@Serializable
enum class ActionType {
    CALENDAR_EVENT,  // Create calendar event
    REMINDER,        // Set alarm/reminder
    TODO,            // Task to do
    MESSAGE,         // Send message to someone
    CALL,            // Call someone
    NOTE             // Important note to keep
}

/**
 * A semantic group of entries, dynamically assigned by the LLM.
 * Similar keywords (e.g. "tengo que", "debería") are merged into one group.
 */
@Serializable
data class EntryGroup(
    val label: String,     // e.g. "Pendientes", "Salud", "Trabajo"
    val emoji: String,     // e.g. "📋", "🏥", "💼"
    val items: List<String> // individual entry summaries within this group
)

/**
 * The complete daily summary produced by the LLM.
 */
@Serializable
data class DailySummary(
    val date: String,              // "2026-03-18"
    val narrative: String,         // 2-3 sentence summary of the day
    val groups: List<EntryGroup> = emptyList(), // semantic grouping of entries
    val actions: List<SuggestedAction>,
    val entryCount: Int,
    val generatedAt: Long = System.currentTimeMillis()
)
