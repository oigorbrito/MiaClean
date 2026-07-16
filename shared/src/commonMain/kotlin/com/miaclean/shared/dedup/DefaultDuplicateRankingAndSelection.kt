package com.miaclean.shared.dedup

import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaItem

class DefaultDuplicateRanking : DuplicateRanking {
    override fun rank(group: DuplicateGroup): List<MediaItem> {
        // Simple ranking for now: just sort by highest resolution/size or date
        // Since we don't have resolution, we can just sort by sizeBytes (largest first)
        // and then by dateTakenMs (oldest first).
        return group.items.sortedWith(
            compareByDescending<MediaItem> { it.sizeBytes }
                .thenBy { it.dateTakenMs }
                .thenBy { it.id }
        )
    }
}

class DefaultDuplicateSelection : DuplicateSelection {
    override fun selectForDeletion(rankedItems: List<MediaItem>): List<MediaItem> {
        if (rankedItems.isEmpty()) return emptyList()
        // Always keep the best one (the first item)
        return rankedItems.drop(1)
    }
}
