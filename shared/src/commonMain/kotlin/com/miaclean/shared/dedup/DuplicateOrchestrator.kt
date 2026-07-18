package com.miaclean.shared.dedup

import com.miaclean.app.domain.DuplicateGroup

/**
 * Orchestrates the deduplication pipeline: grouping candidates, ranking items within groups,
 * and determining the default selection for deletion.
 */
class DuplicateOrchestrator(
    private val grouping: DuplicateGrouping = DefaultDuplicateGrouping(),
    private val ranking: DuplicateRanking = DefaultDuplicateRanking(),
    private val selection: DuplicateSelection = DefaultDuplicateSelection()
) {
    /**
     * Groups candidates into duplicates and applies ranking to sort items within each group.
     */
    fun process(candidates: List<DedupCandidate>): List<DuplicateGroup> {
        val rawGroups = grouping.group(candidates)
        return rawGroups.map { group ->
            group.copy(items = ranking.rank(group))
        }
    }

    /**
     * Determines which items should be automatically selected for deletion across all groups.
     * Returns a set of media IDs to be deleted.
     */
    fun getAutoSelection(groups: List<DuplicateGroup>): Set<Long> {
        return groups.flatMap { group ->
            selection.selectForDeletion(group.items).map { it.id }
        }.toSet()
    }
}
