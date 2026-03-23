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
    val isManual: Boolean = false            // true if manually entered by user
)
