package com.miaclean.app.data

import android.net.Uri
import com.miaclean.app.R
import com.miaclean.app.data.classify.ClassifierEventLogger
import com.miaclean.app.data.classify.ErrorCategory
import com.miaclean.app.data.classify.MediaClassifier
import com.miaclean.app.data.classify.MemeDetector
import com.miaclean.app.data.classify.SelfieDetector
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.db.MediaHashEntity
import com.miaclean.app.data.hash.Md5Hasher
import com.miaclean.app.data.hash.PerceptualHasher
import com.miaclean.app.data.ml.ImageEmbedderWrapper
import com.miaclean.app.data.scan.MediaStoreScanner
import com.miaclean.app.data.scan.SafWhatsAppScanner
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import com.miaclean.app.domain.ScanErrorCode
import com.miaclean.app.domain.ScanProgress
import com.miaclean.app.ui.scan.ClassifierErrorMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
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
        try {
            send(ScanProgress.Running(0, 0))
            val items = withContext(Dispatchers.IO) {
                val base = try {
                    mediaStoreScanner.scanAll()
                } catch (e: Exception) {
                    throw wrapFatalException(e)
                }
                val extra = additionalSafTreeUris.flatMap { uri ->
                    try {
                        safScanner.scan(uri)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                (base + extra).distinctBy { it.uri }
            }
            val total = items.size
            if (total == 0) {
                send(ScanProgress.Done(duplicates = 0, groups = 0))
                return@channelFlow
            }

            var firstClassifierErrorResId: Int? = null

            val cachedIds = withContext(Dispatchers.IO) {
                dao.findAllMediaIds().toSet()
            }

            withContext(Dispatchers.IO) {
                items.forEachIndexed { index, item ->
                    try {
                        if (item.id in cachedIds) return@forEachIndexed
                        processItem(item, { error ->
                            if (firstClassifierErrorResId == null) {
                                firstClassifierErrorResId = ClassifierErrorMapper.mapToFriendlyMessage(error)
                            }
                        }, { unexpectedErrorResId ->
                            if (firstClassifierErrorResId == null) {
                                firstClassifierErrorResId = unexpectedErrorResId
                            }
                        })
                    } catch (e: Exception) {
                        if (isFatalException(e)) throw wrapFatalException(e)
                        // Non-fatal: just skip this item and record unexpected error if not already set
                        if (firstClassifierErrorResId == null) {
                            firstClassifierErrorResId = R.string.classifier_error_unexpected
                        }
                    }
                    send(ScanProgress.Running(processed = index + 1, total = total))
                }
            }

            val groups = buildGroups()
            val duplicates = groups.sumOf { it.items.size }
            send(ScanProgress.Done(
                duplicates = duplicates,
                groups = groups.size,
                classificationErrorResId = firstClassifierErrorResId
            ))
        } catch (e: Exception) {
            val fatal = if (e is FatalScanException) e else wrapFatalException(e)
            send(ScanProgress.Failed(fatal.errorCode, fatal.reasonResId))
        }
    }

    private suspend fun processItem(
        item: MediaItem,
        onClassifierError: (ErrorCategory) -> Unit,
        onUnexpectedError: (Int) -> Unit
    ) {
        val uri = Uri.parse(item.uri)
        val md5 = md5Hasher.hash(uri) ?: return

        val phash = if (item.mimeType.startsWith("image/")) {
            perceptualHasher.hash(uri)
        } else {
            null
        }
        val embeddingHash = if (item.mimeType.startsWith("image/")) {
            imageEmbedder.embed(uri)?.let(::encodeEmbedding)
        } else {
            null
        }

        val category = try {
            resolveCategory(item, uri, onClassifierError)
        } catch (e: Exception) {
            onUnexpectedError(R.string.classifier_error_unexpected)
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

    private fun isFatalException(e: Throwable): Boolean {
        return e is FileNotFoundException || e is SecurityException || e is FatalScanException ||
                e.cause is FileNotFoundException || e.cause is SecurityException
    }

    private fun wrapFatalException(e: Throwable): FatalScanException {
        return when {
            e is FatalScanException -> e
            e is SecurityException || e.cause is SecurityException ->
                FatalScanException(ScanErrorCode.PERMISSION_REVOKED, R.string.scan_error_permission_revoked, e)
            e is FileNotFoundException || e.cause is FileNotFoundException ->
                FatalScanException(ScanErrorCode.MEDIA_UNAVAILABLE, R.string.scan_error_media_unavailable, e)
            else ->
                FatalScanException(ScanErrorCode.UNEXPECTED, R.string.scan_error_unexpected, e)
        }
    }

    private class FatalScanException(
        val errorCode: ScanErrorCode,
        val reasonResId: Int,
        cause: Throwable? = null
    ) : RuntimeException(cause)

    suspend fun loadGroups(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        try { buildGroups() } catch (e: Exception) { emptyList() }
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
                    items = list.map(MediaHashEntity::toMediaItem),
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
                    items = bucket.map(MediaHashEntity::toMediaItem),
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
            val embedding = row.embeddingHash?.let(::decodeEmbedding) ?: return@mapNotNull null
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
                if (imageEmbedder.cosine(baseEmbedding, candidateEmbedding) >= SEMANTIC_SIMILARITY_THRESHOLD) {
                    bucket += candidateRow
                    visited[j] = true
                }
            }

            if (bucket.size > 1) {
                groups += DuplicateGroup(
                    groupId = nextId++,
                    strategy = DuplicateGroup.Strategy.SEMANTIC_EMBED,
                    items = bucket.map(MediaHashEntity::toMediaItem),
                    totalBytes = bucket.sumOf { it.sizeBytes },
                )
            }
        }
        return groups
    }
}

private fun MediaItem.toEntity(
    md5: String,
    pHash: String?,
    embeddingHash: String?,
    category: MediaCategory,
): MediaHashEntity = MediaHashEntity(
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
    embeddingHash = embeddingHash,
    lastScannedMs = System.currentTimeMillis(),
    category = category.name,
)

private fun encodeEmbedding(values: FloatArray): String =
    values.joinToString(separator = ",") { "%.5f".format(java.util.Locale.US, it) }

private fun decodeEmbedding(serialized: String): FloatArray {
    val parts = serialized.split(',')
    return FloatArray(parts.size) { index -> parts[index].toFloatOrNull() ?: 0f }
}

private const val SEMANTIC_SIMILARITY_THRESHOLD = 0.92f
private const val PHASH_DISTANCE_THRESHOLD = 5

private class IntDisjointSet(size: Int) {
    private val parent = IntArray(size) { it }
    private val rank = IntArray(size)

    fun find(x: Int): Int {
        if (parent[x] != x) parent[x] = find(parent[x])
        return parent[x]
    }

    fun union(a: Int, b: Int) {
        var rootA = find(a)
        var rootB = find(b)
        if (rootA == rootB) return
        if (rank[rootA] < rank[rootB]) {
            val tmp = rootA
            rootA = rootB
            rootB = tmp
        }
        parent[rootB] = rootA
        if (rank[rootA] == rank[rootB]) rank[rootA]++
    }
}

private class HammingBkTree {
    private var root: Node? = null

    fun add(hash: String, index: Int) {
        val node = Node(hash = hash, indices = mutableListOf(index))
        val currentRoot = root
        if (currentRoot == null) {
            root = node
            return
        }
        insert(currentRoot, node)
    }

    fun search(hash: String, maxDistance: Int): List<Int> {
        val result = mutableListOf<Int>()
        searchRecursive(root = root, hash = hash, maxDistance = maxDistance, result = result)
        return result
    }

    private fun insert(current: Node, candidate: Node) {
        val distance = hammingDistance(current.hash, candidate.hash, stopAfter = Int.MAX_VALUE)
        if (distance == 0) {
            current.indices += candidate.indices
            return
        }
        val next = current.children[distance]
        if (next == null) {
            current.children[distance] = candidate
        } else {
            insert(next, candidate)
        }
    }

    private fun searchRecursive(
        root: Node?,
        hash: String,
        maxDistance: Int,
        result: MutableList<Int>,
    ) {
        val node = root ?: return
        val distance = hammingDistance(node.hash, hash, stopAfter = Int.MAX_VALUE)
        if (distance <= maxDistance) result += node.indices
        val lower = (distance - maxDistance).coerceAtLeast(0)
        val upper = distance + maxDistance
        for ((edge, child) in node.children) {
            if (edge in lower..upper) {
                searchRecursive(root = child, hash = hash, maxDistance = maxDistance, result = result)
            }
        }
    }

    private data class Node(
        val hash: String,
        val indices: MutableList<Int>,
        val children: MutableMap<Int, Node> = mutableMapOf(),
    )
}

private fun hammingDistance(left: String, right: String, stopAfter: Int): Int {
    if (left.length != right.length) return Int.MAX_VALUE
    var distance = 0
    for (i in left.indices) {
        if (left[i] != right[i]) distance++
        if (distance > stopAfter) return distance
    }
    return distance
}

private fun MediaHashEntity.toMediaItem(): MediaItem = MediaItem(
    id = mediaId,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    dateTakenMs = dateTakenMs,
    relativePath = relativePath,
    isFromWhatsApp = isWhatsApp,
    category = runCatching { MediaCategory.valueOf(category) }.getOrDefault(MediaCategory.Other),
)
