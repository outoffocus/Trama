package com.trama.shared.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timeline_events",
    indices = [
        Index("timestamp"),
        Index("type"),
        Index("placeId")
    ]
)
data class TimelineEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val timestamp: Long,
    val endTimestamp: Long? = null,
    val title: String,
    val subtitle: String? = null,
    val dataJson: String? = null,
    val isHighlight: Boolean = false,
    val placeId: Long? = null,
    val source: String = TimelineEventSource.AUTO,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

object TimelineEventType {
    const val DWELL = "DWELL"
    const val CALENDAR = "CALENDAR"
    const val AMBIENT_CONTEXT = "AMBIENT_CONTEXT"
}

object TimelineEventSource {
    const val AUTO = "AUTO"
    const val MANUAL = "MANUAL"
    const val CALENDAR_IMPORT = "CALENDAR_IMPORT"
    const val AMBIENT_LOCAL = "AMBIENT_LOCAL"
}
