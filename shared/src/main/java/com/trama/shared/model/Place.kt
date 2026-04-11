package com.trama.shared.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "places",
    indices = [
        Index("lastVisitAt"),
        Index("isHome"),
        Index("isWork")
    ]
)
data class Place(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val type: String? = null,
    val visitCount: Int = 0,
    val lastVisitAt: Long? = null,
    val rating: Int? = null,
    val opinionText: String? = null,
    val opinionSummary: String? = null,
    val opinionUpdatedAt: Long? = null,
    val isHome: Boolean = false,
    val isWork: Boolean = false,
    val userRenamed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
