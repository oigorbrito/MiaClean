package com.miaclean.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaHashDao {

    @Query("SELECT * FROM media_hash WHERE media_id = :mediaId LIMIT 1")
    suspend fun findByMediaId(mediaId: Long): MediaHashEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaHashEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MediaHashEntity>)

    @Query("DELETE FROM media_hash WHERE media_id = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)

    @Query("SELECT COUNT(*) FROM media_hash")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM media_hash
        WHERE md5 IN (
            SELECT md5 FROM media_hash GROUP BY md5 HAVING COUNT(*) > 1
        )
        ORDER BY md5, date_taken_ms DESC
        """,
    )
    suspend fun findExactDuplicates(): List<MediaHashEntity>

    @Query("SELECT * FROM media_hash WHERE p_hash IS NOT NULL")
    suspend fun findAllWithPHash(): List<MediaHashEntity>
}
