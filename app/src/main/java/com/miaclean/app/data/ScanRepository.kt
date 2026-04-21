package com.miaclean.app.data

import android.net.Uri
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.db.MediaHashEntity
import com.miaclean.app.data.hash.Md5Hasher
import com.miaclean.app.data.hash.PerceptualHasher
import com.miaclean.app.data.scan.MediaStoreScanner
import com.miaclean.app.data.scan.SafWhatsAppScanner
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaItem
import com.miaclean.app.domain.ScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full scan pipeline: enumerate → hash (MD5 + pHash) → persist → group.
 *
 * The repository is intentionally a single file so the feature boundary is easy to follow. Split it
 * once any stage grows beyond a few dozen lines.
 */
@Singleton
class ScanRepository @Inject constructor(
    private val mediaStoreScanner: MediaStoreScanner,
    private val safScanner: SafWhatsAppScanner,
    private val md5Hasher: Md5Hasher,
    private val perceptualHasher: PerceptualHasher,
    private val dao: MediaHashDao,
) {

    fun scan(additionalSafTreeUris: List<Uri> = emptyList()): Flow<ScanProgress> = channelFlow {
        send(ScanProgress.Running(0, 0))
        val items = withContext(Dispatchers.IO) {
            val base = mediaStoreScanner.scanAll()
            val extra = additionalSafTreeUris.flatMap { safScanner.scan(it) }
            (base + extra).distinctBy { it.uri }
        }
        val total = items.size
        if (total == 0) {
            send(ScanProgress.Done(duplicates = 0, groups = 0))
            return@channelFlow
        }

        withContext(Dispatchers.IO) {
            items.forEachIndexed { index, item ->
                val cached = dao.findByMediaId(item.id)
                if (cached == null) {
                    val uri = Uri.parse(item.uri)
                    val md5 = md5Hasher.hash(uri)
                    val phash = if (item.mimeType.startsWith("image/")) {
                        perceptualHasher.hash(uri)
                    } else {
                        null
                    }
                    if (md5 != null) {
                        dao.upsert(item.toEntity(md5 = md5, pHash = phash))
                    }
                }
                send(ScanProgress.Running(processed = index + 1, total = total))
            }
        }

        val groups = buildGroups()
        val duplicates = groups.sumOf { it.items.size }
        send(ScanProgress.Done(duplicates = duplicates, groups = groups.size))
    }

    suspend fun loadGroups(): List<DuplicateGroup> = withContext(Dispatchers.IO) { buildGroups() }

    private suspend fun buildGroups(): List<DuplicateGroup> {
        val exact = dao.findExactDuplicates()
        val exactByMd5 = exact.groupBy { it.md5 }
        val exactGroups = exactByMd5.values
            .filter { it.size > 1 }
            .mapIndexed { index, list ->
                DuplicateGroup(
                    groupId = index.toLong(),
                    strategy = DuplicateGroup.Strategy.EXACT_MD5,
                    items = list.map(MediaHashEntity::toMediaItem),
                    totalBytes = list.sumOf { it.sizeBytes },
                )
            }

        val pHashRows = dao.findAllWithPHash()
        val exactIds = exact.map { it.mediaId }.toSet()
        val perceptualCandidates = pHashRows.filter { it.mediaId !in exactIds }
        val perceptualGroups = buildPerceptualGroups(perceptualCandidates, offset = exactGroups.size)

        return exactGroups + perceptualGroups
    }

    private fun buildPerceptualGroups(
        rows: List<MediaHashEntity>,
        offset: Int,
    ): List<DuplicateGroup> {
        val visited = BooleanArray(rows.size)
        val groups = mutableListOf<DuplicateGroup>()
        var nextId = offset.toLong()
        for (i in rows.indices) {
            if (visited[i]) continue
            val base = rows[i]
            val bucket = mutableListOf(base)
            visited[i] = true
            for (j in (i + 1) until rows.size) {
                if (visited[j]) continue
                val candidate = rows[j]
                val leftHash = base.pHash ?: continue
                val rightHash = candidate.pHash ?: continue
                if (perceptualHasher.isSimilar(leftHash, rightHash)) {
                    bucket += candidate
                    visited[j] = true
                }
            }
            if (bucket.size > 1) {
                groups += DuplicateGroup(
                    groupId = nextId++,
                    strategy = DuplicateGroup.Strategy.PERCEPTUAL_PHASH,
                    items = bucket.map(MediaHashEntity::toMediaItem),
                    totalBytes = bucket.sumOf { it.sizeBytes },
                )
            }
        }
        return groups
    }
}

private fun MediaItem.toEntity(md5: String, pHash: String?): MediaHashEntity = MediaHashEntity(
    mediaId = id,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    dateTakenMs = dateTakenMs,
    relativePath = relativePath,
    isWhatsApp = isFromWhatsApp,
    md5 = md5,
    pHash = pHash,
    embeddingHash = null,
    lastScannedMs = System.currentTimeMillis(),
)

private fun MediaHashEntity.toMediaItem(): MediaItem = MediaItem(
    id = mediaId,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    dateTakenMs = dateTakenMs,
    relativePath = relativePath,
    isFromWhatsApp = isWhatsApp,
)
