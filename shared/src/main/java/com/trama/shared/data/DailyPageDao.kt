package com.trama.shared.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trama.shared.model.DailyPage
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyPageDao {

    @Query("SELECT * FROM daily_pages WHERE dayStartMillis = :dayStartMillis")
    fun getByDay(dayStartMillis: Long): Flow<DailyPage?>

    @Query("SELECT * FROM daily_pages WHERE dayStartMillis = :dayStartMillis")
    suspend fun getByDayOnce(dayStartMillis: Long): DailyPage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(page: DailyPage)

    @Query(
        """
        UPDATE daily_pages
        SET reviewedAt = :reviewedAt,
            hasManualReview = 1,
            updatedAt = :updatedAt
        WHERE dayStartMillis = :dayStartMillis
        """
    )
    suspend fun markReviewed(dayStartMillis: Long, reviewedAt: Long, updatedAt: Long = reviewedAt)
}
