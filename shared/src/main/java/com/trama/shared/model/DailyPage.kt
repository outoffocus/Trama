package com.trama.shared.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_pages")
data class DailyPage(
    @PrimaryKey
    val dayStartMillis: Long,
    val date: String,
    val status: String = DailyPageStatus.DRAFT,
    val briefSummary: String? = null,
    val insightsJson: String = "",
    val markdown: String = "",
    val markdownPath: String? = null,
    val generatedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null,
    val hasManualReview: Boolean = false
)

object DailyPageStatus {
    const val DRAFT = "DRAFT"
    const val FINAL = "FINAL"
}
