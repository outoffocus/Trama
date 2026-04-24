package com.trama.shared.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trama.shared.model.TimelineEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineEventDao {

    @Query("SELECT * FROM timeline_events ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TimelineEvent>>

    @Query("SELECT * FROM timeline_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun byDateRange(startTime: Long, endTime: Long): Flow<List<TimelineEvent>>

    @Query("SELECT * FROM timeline_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun byDateRangeOnce(startTime: Long, endTime: Long): List<TimelineEvent>

    @Query("SELECT * FROM timeline_events WHERE id = :id")
    suspend fun getByIdOnce(id: Long): TimelineEvent?

    @Query("SELECT * FROM timeline_events WHERE type = :type AND source = :source AND dataJson = :dataJson LIMIT 1")
    suspend fun getByTypeSourceAndDataJson(type: String, source: String, dataJson: String): TimelineEvent?

    @Query("SELECT * FROM timeline_events WHERE placeId = :placeId ORDER BY timestamp DESC")
    fun getByPlaceId(placeId: Long): Flow<List<TimelineEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: TimelineEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<TimelineEvent>)

    @Update
    suspend fun update(event: TimelineEvent)

    @Query("UPDATE timeline_events SET title = :title WHERE placeId = :placeId")
    suspend fun updateTitleForPlace(placeId: Long, title: String)

    @Query("DELETE FROM timeline_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM timeline_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
