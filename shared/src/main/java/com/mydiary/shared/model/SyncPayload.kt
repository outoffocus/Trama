package com.mydiary.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncPayload(
    val entries: List<SyncEntry>
)

@Serializable
data class SyncEntry(
    val id: Long,
    val text: String,
    val keyword: String,
    val category: String,
    val confidence: Float,
    val createdAt: Long,
    val source: String,
    val duration: Int,
    val status: String = "PENDING",
    val actionType: String = "GENERIC",
    val cleanText: String? = null,
    val dueDate: Long? = null,
    val priority: String = "NORMAL"
) {
    fun toDiaryEntry(): DiaryEntry = DiaryEntry(
        text = text,
        keyword = keyword,
        category = category,
        confidence = confidence,
        createdAt = createdAt,
        source = Source.valueOf(source),
        isSynced = true,
        duration = duration,
        status = status,
        actionType = actionType,
        cleanText = cleanText,
        dueDate = dueDate,
        priority = priority
    )

    companion object {
        fun fromDiaryEntry(entry: DiaryEntry): SyncEntry = SyncEntry(
            id = entry.id,
            text = entry.text,
            keyword = entry.keyword,
            category = entry.category,
            confidence = entry.confidence,
            createdAt = entry.createdAt,
            source = entry.source.name,
            duration = entry.duration,
            status = entry.status,
            actionType = entry.actionType,
            cleanText = entry.cleanText,
            dueDate = entry.dueDate,
            priority = entry.priority
        )
    }
}
