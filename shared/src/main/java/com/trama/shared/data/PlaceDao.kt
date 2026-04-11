package com.trama.shared.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trama.shared.model.Place
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Place>>

    @Query("SELECT * FROM places ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<Place>

    @Query("SELECT * FROM places WHERE id = :id")
    fun getById(id: Long): Flow<Place?>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Place?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: Place): Long

    @Update
    suspend fun update(place: Place)

    @Query("UPDATE places SET name = :name, userRenamed = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE places SET visitCount = visitCount + 1, lastVisitAt = :visitedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementVisit(id: Long, visitedAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE places
        SET rating = :rating,
            opinionText = :opinionText,
            opinionSummary = :opinionSummary,
            opinionUpdatedAt = :opinionUpdatedAt,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateOpinion(
        id: Long,
        rating: Int?,
        opinionText: String?,
        opinionSummary: String?,
        opinionUpdatedAt: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE places
        SET opinionSummary = :opinionSummary,
            opinionUpdatedAt = :opinionUpdatedAt,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateOpinionSummary(
        id: Long,
        opinionSummary: String?,
        opinionUpdatedAt: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE places SET isHome = CASE WHEN id = :placeId THEN 1 ELSE 0 END")
    suspend fun markHome(placeId: Long)

    @Query("UPDATE places SET isHome = 0 WHERE id = :placeId")
    suspend fun clearHome(placeId: Long)

    @Query("UPDATE places SET isWork = CASE WHEN id = :placeId THEN 1 ELSE 0 END")
    suspend fun markWork(placeId: Long)

    @Query("UPDATE places SET isWork = 0 WHERE id = :placeId")
    suspend fun clearWork(placeId: Long)

    @Query(
        """
        SELECT * FROM places
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLon AND :maxLon
        """
    )
    suspend fun findInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<Place>
}
