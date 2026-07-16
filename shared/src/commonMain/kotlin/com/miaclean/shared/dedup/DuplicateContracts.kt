package com.miaclean.shared.dedup

import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaItem

/**
 * A candidate for deduplication containing both the item details and its hashes.
 */
data class DedupCandidate(
    val item: MediaItem,
    val md5: String,
    val pHash: String?,
    val embeddingHash: FloatArray?
)

/**
 * Contract for grouping media items into duplicates based on different strategies.
 */
interface DuplicateGrouping {
    /**
     * Groups a list of candidates into duplicate groups.
     * The input should include all candidates to be considered.
     */
    fun group(candidates: List<DedupCandidate>): List<DuplicateGroup>
}

/**
 * Contract for ranking items within a duplicate group to determine the "best" item to keep.
 */
interface DuplicateRanking {
    /**
     * Ranks the items in a duplicate group. The first item in the returned list is considered the best.
     */
    fun rank(group: DuplicateGroup): List<MediaItem>
}

/**
 * Contract for selecting items to be deleted or kept from a duplicate group.
 */
interface DuplicateSelection {
    /**
     * Given a ranked list of items, returns the subset of items that should be automatically selected for deletion.
     */
    fun selectForDeletion(rankedItems: List<MediaItem>): List<MediaItem>
}
