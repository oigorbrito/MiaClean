package com.miaclean.app.data.adapter
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.db.MediaHashEntity
import com.miaclean.app.data.repository.MediaHashRepository
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaHash
import com.miaclean.app.domain.MediaItem
import javax.inject.Inject
class RoomMediaHashRepository @Inject constructor(private val dao: MediaHashDao) : MediaHashRepository {
    override suspend fun findByMediaId(mediaId: Long): MediaHash? = dao.findByMediaId(mediaId)?.let { MediaHash(it.md5, it.pHash, it.embeddingHash, it.toMediaItem()) }
    override suspend fun upsert(item: MediaItem, md5: String, pHash: String?, embeddingHash: String?) {
        dao.upsert(MediaHashEntity(item.id, item.uri, item.displayName, item.mimeType, item.sizeBytes, item.dateTakenMs, item.relativePath, item.isFromWhatsApp, md5, pHash, embeddingHash, System.currentTimeMillis(), item.category.name))
    }
    override suspend fun findExactDuplicates(): List<MediaHash> = dao.findExactDuplicates().map { MediaHash(it.md5, it.pHash, it.embeddingHash, it.toMediaItem()) }
    override suspend fun findAllWithPHash(): List<MediaHash> = dao.findAllWithPHash().map { MediaHash(it.md5, it.pHash, it.embeddingHash, it.toMediaItem()) }
    override suspend fun findAllWithEmbedding(): List<MediaHash> = dao.findAllWithEmbedding().map { MediaHash(it.md5, it.pHash, it.embeddingHash, it.toMediaItem()) }
}
private fun MediaHashEntity.toMediaItem(): MediaItem = MediaItem(mediaId, uri, displayName, mimeType, sizeBytes, dateTakenMs, relativePath, isWhatsApp, runCatching { MediaCategory.valueOf(category) }.getOrDefault(MediaCategory.Other))
