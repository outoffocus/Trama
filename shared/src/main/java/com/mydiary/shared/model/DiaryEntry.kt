package com.mydiary.shared.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val keyword: String,
    val category: Category,
    val confidence: Float,
    val createdAt: Long = System.currentTimeMillis(),
    val source: Source,
    val isSynced: Boolean = false,
    val duration: Int // recording duration in seconds
)
