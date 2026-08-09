package com.trama.shared.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String? = null,
    val transcription: String,
    val summary: String? = null,
    val keyPoints: String? = null,       // JSON array of strings
    val durationSeconds: Int,
    val source: Source,
    val createdAt: Long = System.currentTimeMillis(),
    val processingStatus: String = RecordingStatus.PENDING,
    val processedLocally: Boolean = false,
    val processedBy: String? = null, // "CLOUD", "NANO", "LOCAL"
    val isSynced: Boolean = false,
    /** App-private PCM file retained so interrupted transcription can be retried. */
    val audioFilePath: String? = null,
    val audioSampleRateHz: Int = 16_000
)

object RecordingStatus {
    const val CAPTURING = "CAPTURING"
    const val TRANSCRIBING = "TRANSCRIBING"
    const val PENDING = "PENDING"
    const val PROCESSING = "PROCESSING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
}
