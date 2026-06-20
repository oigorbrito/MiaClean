package com.miaclean.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaHashDao {

    @Query("SELECT * FROM media_hash WHERE media_id = :mediaId LIMIT 1")
    suspend fun findByMediaId(mediaId: Long): MediaHashEntity?

    @Query("SELECT * FROM media_hash WHERE media_id IN (:mediaIds)")
    suspend fun findByMediaIdsChunk(mediaIds: List<Long>): List<MediaHashEntity>

    /**
     * Chunked wrapper around [findByMediaIdsChunk] mirroring [deleteByMediaIds] to stay under
     * SQLite's 999-variable limit on API 26–30.
     */
    @Transaction
    suspend fun findByMediaIds(mediaIds: List<Long>): List<MediaHashEntity> {
        if (mediaIds.isEmpty()) return emptyList()
        return mediaIds.chunked(CHUNK_SIZE).flatMap { findByMediaIdsChunk(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaHashEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MediaHashEntity>)

    @Query("DELETE FROM media_hash WHERE media_id = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)

    @Query("DELETE FROM media_hash WHERE media_id IN (:mediaIds)")
    suspend fun deleteByMediaIdsChunk(mediaIds: List<Long>)

    /**
     * Chunked wrapper around [deleteByMediaIdsChunk]. SQLite's `SQLITE_MAX_VARIABLE_NUMBER` is 999
     * on API 26–30, so a large bulk selection (e.g. "select all duplicates except first" across
     * hundreds of groups) would otherwise crash with `SQLiteException: too many SQL variables`.
     */
    @Transaction
    suspend fun deleteByMediaIds(mediaIds: List<Long>) {
        if (mediaIds.isEmpty()) return
        mediaIds.chunked(CHUNK_SIZE).forEach { deleteByMediaIdsChunk(it) }
    }

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

    @Query("SELECT * FROM media_hash WHERE embedding_hash IS NOT NULL")
    suspend fun findAllWithEmbedding(): List<MediaHashEntity>

    @Query("SELECT media_id FROM media_hash")
    suspend fun findAllMediaIds(): List<Long>

    companion object {
        /** Safely below SQLite's 999-variable limit on API 26–30. */
        private const val CHUNK_SIZE = 900
    }
}
