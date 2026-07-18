package com.miaclean.app.data.dedupe

import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaItem

/**
 * A minimal projection of a stored media record that the pure grouping algorithms need.
 * Adapters in :app map [MediaHashEntity] → [DuplicateCandidate] before handing off to groupers.
 */
data class DuplicateCandidate(
    val mediaId: Long,
    val item: MediaItem,
    /** MD5 hex digest; null if hashing failed. */
    val md5: String?,
    /** Perceptual hash string (hex or binary); null for non-image or unhashable files. */
    val pHash: String?,
    /** Comma-separated float embedding; null when embedder didn't run. */
    val embeddingHash: String?,
    val sizeBytes: Long,
)

/**
 * Groups a list of candidates by exact MD5 match.
 * Pure function — no I/O, no Android dependencies.
 */
interface ExactGrouper {
    fun group(candidates: List<DuplicateCandidate>, idOffset: Long): List<DuplicateGroup>
}

/**
 * Groups a list of candidates by perceptual hash similarity (Hamming distance ≤ threshold).
 * Pure function — no I/O, no Android dependencies.
 */
interface PerceptualGrouper {
    fun group(candidates: List<DuplicateCandidate>, idOffset: Long): List<DuplicateGroup>
}

/**
 * Groups a list of candidates by semantic embedding cosine similarity (≥ threshold).
 * Pure function — no I/O, no Android dependencies.
 */
interface SemanticGrouper {
    fun group(candidates: List<DuplicateCandidate>, idOffset: Long): List<DuplicateGroup>
}
