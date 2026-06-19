package com.miaclean.app.data.repository
import com.miaclean.app.domain.MediaItem
import com.miaclean.app.domain.MediaHash
interface MediaHashRepository {
    suspend fun findByMediaId(mediaId: Long): MediaHash?
    suspend fun upsert(item: MediaItem, md5: String, pHash: String?, embeddingHash: String?)
    suspend fun findExactDuplicates(): List<MediaHash>
    suspend fun findAllWithPHash(): List<MediaHash>
    suspend fun findAllWithEmbedding(): List<MediaHash>
}
