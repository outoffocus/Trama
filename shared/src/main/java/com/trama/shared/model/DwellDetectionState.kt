package com.trama.shared.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dwell_detection_state")
data class DwellDetectionState(
    @PrimaryKey
    val id: Int = 1,
    val candidateLat: Double? = null,
    val candidateLon: Double? = null,
    val candidateStartedAt: Long? = null,
    val candidateLastSeenAt: Long? = null,
    val anchorLat: Double? = null,
    val anchorLon: Double? = null,
    val dwellStartedAt: Long? = null,
    val active: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
