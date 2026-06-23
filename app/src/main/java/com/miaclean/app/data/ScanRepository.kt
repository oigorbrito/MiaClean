package com.miaclean.app.data

import android.net.Uri
import com.miaclean.app.data.classify.ClassifierEventLogger
import com.miaclean.app.data.classify.ErrorCategory
import com.miaclean.app.data.classify.MediaClassifier
import com.miaclean.app.data.classify.MemeDetector
import com.miaclean.app.data.classify.SelfieDetector
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.db.MediaHashEntity
import com.miaclean.app.data.db.toEntity
import com.miaclean.app.data.db.toMediaItem
import com.miaclean.app.data.hash.Md5Hasher
import com.miaclean.app.data.hash.PerceptualHasher
import com.miaclean.app.data.ml.ImageEmbedderWrapper
import com.miaclean.app.data.scan.MediaStoreScanner
import com.miaclean.app.data.scan.SafWhatsAppScanner
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import com.miaclean.app.domain.ScanProgress
import com.miaclean.app.domain.util.EmbeddingUtils
import com.miaclean.app.domain.util.GroupingConstants.PHASH_DISTANCE_THRESHOLD
import com.miaclean.app.domain.util.GroupingConstants.SEMANTIC_SIMILARITY_THRESHOLD
import com.miaclean.app.domain.util.HammingBkTree
import com.miaclean.app.domain.util.IntDisjointSet
import com.miaclean.app.ui.scan.ClassifierErrorMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full scan pipeline: enumerate → hash (MD5 + pHash) → persist → group.
 */
@Singleton
class ScanRepository @Inject constructor(
    private val mediaStoreScanner: MediaStoreScanner,
    private val safScanner: SafWhatsAppScanner,
    private val md5Hasher: Md5Hasher,
    private val perceptualHasher: PerceptualHasher,
    private val imageEmbedder: ImageEmbedderWrapper,
    private val classifier: MediaClassifier,
    private val selfieDetector: SelfieDetector,
    private val memeDetector: MemeDetector,
    private val logger: ClassifierEventLogger,
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

        var firstClassifierErrorResId: Int? = null

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
                    val embeddingHash = if (item.mimeType.startsWith("image/")) {
                        imageEmbedder.embed(uri)?.let { EmbeddingUtils.encode(it) }
                    } else {
                        null
                    }
                    if (md5 != null) {
                        val category = try {
                            resolveCategory(item, uri) { error ->
                                if (firstClassifierErrorResId == null) {
                                    firstClassifierErrorResId = ClassifierErrorMapper.mapToFriendlyMessage(error)
                                }
                            }
                        } catch (e: Exception) {
                            if (firstClassifierErrorResId == null) {
                                firstClassifierErrorResId = ClassifierErrorMapper.mapToFriendlyMessage(ErrorCategory.UNEXPECTED)
                            }
                            MediaCategory.Photo
                        }
                        dao.upsert(
                            item.toEntity(
                                md5 = md5,
                                pHash = phash,
                                embeddingHash = embeddingHash,
                                category = category,
                            ),
                        )
                    }
                }
                send(ScanProgress.Running(index + 1, total))
            }

            val groups = buildGroups()
            val duplicateCount = groups.sumOf { it.items.size - 1 }
            send(ScanProgress.Done(duplicateCount, groups.size, firstClassifierErrorResId))
        }
    }

    suspend fun loadGroups(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        buildGroups()
    }

    private suspend fun resolveCategory(
        item: MediaItem,
        uri: Uri,
        onError: (ErrorCategory) -> Unit,
    ): MediaCategory {
        val startTime = System.currentTimeMillis()
        logger.logStart("ScanRepositoryPipeline", item.id)

        val base = classifier.classify(item)
        if (base != MediaCategory.Photo) {
            logger.logSuccess("ScanRepositoryPipeline", item.id, base.name, System.currentTimeMillis() - startTime)
            return base
        }

        if (selfieDetector.isSelfie(uri, item.sizeBytes, item.id, onError)) {
            logger.logSuccess("ScanRepositoryPipeline", item.id, MediaCategory.Selfie.name, System.currentTimeMillis() - startTime)
            return MediaCategory.Selfie
        }

        if (memeDetector.isMeme(uri, item.sizeBytes, item.id, onError)) {
            logger.logSuccess("ScanRepositoryPipeline", item.id, MediaCategory.Meme.name, System.currentTimeMillis() - startTime)
            return MediaCategory.Meme
        }

        logger.logSuccess("ScanRepositoryPipeline", item.id, base.name, System.currentTimeMillis() - startTime)
        return base
    }

    private suspend fun buildGroups(): List<DuplicateGroup> {
        val exact = dao.findExactDuplicates()
        val exactByMd5 = exact.groupBy { it.md5 }
        val exactGroups = exactByMd5.values
            .filter { it.size > 1 }
            .mapIndexed { index, list ->
                DuplicateGroup(
                    groupId = index.toLong(),
                    strategy = DuplicateGroup.Strategy.EXACT_MD5,
                    items = list.map { it.toMediaItem() },
                    totalBytes = list.sumOf { it.sizeBytes },
                )
            }

        val pHashRows = dao.findAllWithPHash()
        val exactIds = exact.map { it.mediaId }.toSet()
        val perceptualCandidates = pHashRows.filter { it.mediaId !in exactIds }
        val perceptualGroups = buildPerceptualGroups(perceptualCandidates, offset = exactGroups.size)
        val groupedByPerceptual = perceptualGroups.flatMapTo(mutableSetOf()) { group ->
            group.items.map { it.id }
        }

        val embeddingRows = dao.findAllWithEmbedding()
        val semanticCandidates = embeddingRows.filter { row ->
            row.mediaId !in exactIds && row.mediaId !in groupedByPerceptual
        }
        val semanticGroups = buildSemanticGroups(
            rows = semanticCandidates,
            offset = exactGroups.size + perceptualGroups.size,
        )

        return exactGroups + perceptualGroups + semanticGroups
    }

    private fun buildPerceptualGroups(
        rows: List<MediaHashEntity>,
        offset: Int,
    ): List<DuplicateGroup> {
        if (rows.isEmpty()) return emptyList()

        val hashes = rows.mapNotNull { row ->
            val hash = row.pHash ?: return@mapNotNull null
            row to hash
        }
        if (hashes.isEmpty()) return emptyList()

        val disjointSet = IntDisjointSet(hashes.size)
        val tree = HammingBkTree().apply {
            hashes.forEachIndexed { index, (_, hash) -> add(hash, index) }
        }

        hashes.forEachIndexed { index, (_, hash) ->
            val neighbors = tree.search(hash, PHASH_DISTANCE_THRESHOLD)
            neighbors.forEach { neighbor ->
                if (neighbor != index) disjointSet.union(index, neighbor)
            }
        }

        val components = linkedMapOf<Int, MutableList<MediaHashEntity>>()
        hashes.forEachIndexed { index, (row, _) ->
            val root = disjointSet.find(index)
            components.getOrPut(root) { mutableListOf() } += row
        }

        var nextId = offset.toLong()
        return components.values
            .asSequence()
            .filter { it.size > 1 }
            .map { bucket ->
                DuplicateGroup(
                    groupId = nextId++,
                    strategy = DuplicateGroup.Strategy.PERCEPTUAL_PHASH,
                    items = bucket.map { it.toMediaItem() },
                    totalBytes = bucket.sumOf { it.sizeBytes },
                )
            }
            .toList()
    }

    private fun buildSemanticGroups(
        rows: List<MediaHashEntity>,
        offset: Int,
    ): List<DuplicateGroup> {
        val decoded = rows.mapNotNull { row ->
            val embedding = row.embeddingHash?.let { EmbeddingUtils.decode(it) } ?: return@mapNotNull null
            row to embedding
        }
        val visited = BooleanArray(decoded.size)
        val groups = mutableListOf<DuplicateGroup>()
        var nextId = offset.toLong()

        for (i in decoded.indices) {
            if (visited[i]) continue
            val (baseRow, baseEmbedding) = decoded[i]
            val bucket = mutableListOf(baseRow)
            visited[i] = true

            for (j in (i + 1) until decoded.size) {
                if (visited[j]) continue
                val (candidateRow, candidateEmbedding) = decoded[j]
                if (EmbeddingUtils.cosine(baseEmbedding, candidateEmbedding) >= SEMANTIC_SIMILARITY_THRESHOLD) {
                    bucket += candidateRow
                    visited[j] = true
                }
            }

            if (bucket.size > 1) {
                groups += DuplicateGroup(
                    groupId = nextId++,
                    strategy = DuplicateGroup.Strategy.SEMANTIC_EMBED,
                    items = bucket.map { it.toMediaItem() },
                    totalBytes = bucket.sumOf { it.sizeBytes },
                )
            }
        }
        return groups
    }
}
