package com.miaclean.app.data

import android.net.Uri
import com.miaclean.app.data.classify.ClassifierEventLogger
import com.miaclean.app.data.classify.ErrorCategory
import com.miaclean.app.data.classify.MediaClassifier
import com.miaclean.app.data.classify.MemeSignalsProvider
import com.miaclean.app.data.classify.SelfieSignalsProvider
import com.miaclean.app.data.classify.MemeEvaluator
import com.miaclean.app.data.classify.SelfieEvaluator
import com.miaclean.app.data.db.MediaHashDao
import com.miaclean.app.data.db.MediaHashEntity
import com.miaclean.app.data.hash.AndroidMediaSource
import com.miaclean.shared.dedup.DuplicateOrchestrator
import com.miaclean.shared.hash.ExactHashOrchestrator
import com.miaclean.app.data.hash.PerceptualHasher
import com.miaclean.app.data.ml.ImageEmbedderWrapper
import com.miaclean.app.data.scan.MediaStoreScanner
import com.miaclean.app.data.scan.SafWhatsAppScanner
import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import com.miaclean.app.R
import com.miaclean.app.domain.ScanProgress
import com.miaclean.app.ui.scan.ClassifierErrorMapper
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
    private val exactHashOrchestrator: ExactHashOrchestrator,
    private val perceptualHasher: PerceptualHasher,
    private val imageEmbedder: ImageEmbedderWrapper,
    private val classifier: MediaClassifier,
    private val selfieSignalsProvider: SelfieSignalsProvider,
    private val memeSignalsProvider: MemeSignalsProvider,
    private val logger: ClassifierEventLogger,
    private val dao: MediaHashDao,
    private val duplicateOrchestrator: DuplicateOrchestrator,
) {

    fun scan(additionalSafTreeUris: List<Uri> = emptyList()): Flow<ScanProgress> = channelFlow {
        try {
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

            // Performance Optimization: Pre-fetch all cached media IDs into a Set to avoid
            // N+1 database queries inside the processing loop. This reduces database
            // roundtrips from O(N) to O(1) total for the cache-check phase.
            val cachedIds = withContext(Dispatchers.IO) { dao.findAllMediaIds().toSet() }

            withContext(Dispatchers.IO) {
                items.forEachIndexed { index, item ->
                    if (item.id !in cachedIds) {
                        val uri = Uri.parse(item.uri)
                        val hashResult = exactHashOrchestrator.calculateHash(AndroidMediaSource(uri))
                        val md5 = when (hashResult) {
                            is ExactHashOrchestrator.Result.Success -> hashResult.hash
                            is ExactHashOrchestrator.Result.Failure -> {
                                hashResult.exception?.let { throw it }
                                null
                            }
                        }
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
                        if (md5 != null) {
                            val category = try {
                                resolveCategory(item, uri) { error ->
                                    if (firstClassifierErrorResId == null) {
                                        firstClassifierErrorResId = ClassifierErrorMapper.mapToFriendlyMessage(error)
                                    }
                                }
                            } catch (e: Exception) {
                                // If resolveCategory throws (shouldn't, but defense in depth),
                                // record as unexpected error and fall back to Photo.
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
                    send(ScanProgress.Running(processed = index + 1, total = total))
                }
            }

            val groups = buildGroups()
            val duplicates = groups.sumOf { it.items.size }
            send(
                ScanProgress.Done(
                    duplicates = duplicates,
                    groups = groups.size,
                    classificationErrorResId = firstClassifierErrorResId,
                ),
            )
        } catch (e: SecurityException) {
            send(ScanProgress.Failed(R.string.scan_error_permission_revoked))
        } catch (e: java.io.FileNotFoundException) {
            send(ScanProgress.Failed(R.string.scan_error_media_unavailable))
        } catch (e: Exception) {
            send(ScanProgress.Failed(R.string.scan_error_unexpected))
        }
    }

    suspend fun loadGroups(): List<DuplicateGroup> = withContext(Dispatchers.IO) { buildGroups() }

    /**
     * Classifies [item] using the cheap metadata-only [MediaClassifier] first, then promotes a
     * `Photo` via ML signals. The pipeline is ordered from cheapest → most expensive:
     *
     *  1. [MediaClassifier] (path/MIME only) — filters out Screenshot/Video/Document/
     *     metadata-selfie/metadata-meme. Only survivors ranked as `Photo` continue.
     *  2. [SelfieDetector] (EXIF + MediaPipe Face Detector) — promotes to `Selfie`.
     *  3. [MemeDetector] (ML Kit Text Recognition) — promotes to `Meme`.
     *
     * Meme runs *after* selfie because (a) path-heuristic memes are already caught by step 1
     * and (b) a photo with a face AND caption text is far more often a selfie with a filter
     * watermark than a meme.
     */
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

        val selfieSignals = selfieSignalsProvider.provideSignals(item, onError)
        if (selfieSignals != null && SelfieEvaluator.isSelfie(selfieSignals)) {
            logger.logSuccess("ScanRepositoryPipeline", item.id, MediaCategory.Selfie.name, System.currentTimeMillis() - startTime)
            return MediaCategory.Selfie
        }

        val memeSignals = memeSignalsProvider.provideSignals(item, onError)
        if (memeSignals != null && MemeEvaluator.isMeme(memeSignals)) {
            logger.logSuccess("ScanRepositoryPipeline", item.id, MediaCategory.Meme.name, System.currentTimeMillis() - startTime)
            return MediaCategory.Meme
        }

        logger.logSuccess("ScanRepositoryPipeline", item.id, base.name, System.currentTimeMillis() - startTime)
        return base
    }

    private suspend fun buildGroups(): List<DuplicateGroup> {
        val exact = dao.findExactDuplicates()
        val pHashRows = dao.findAllWithPHash()
        val embeddingRows = dao.findAllWithEmbedding()

        val allEntities = (exact + pHashRows + embeddingRows).distinctBy { it.mediaId }
        val candidates = allEntities.map { entity ->
            com.miaclean.shared.dedup.DedupCandidate(
                item = entity.toMediaItem(),
                md5 = entity.md5,
                pHash = entity.pHash,
                embeddingHash = entity.embeddingHash?.let(::decodeEmbedding)
            )
        }

        val orchestrator = duplicateOrchestrator
        return orchestrator.process(candidates)
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
