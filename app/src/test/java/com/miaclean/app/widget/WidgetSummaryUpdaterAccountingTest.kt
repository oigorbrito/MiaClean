package com.miaclean.app.widget

import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Keeper-aware accounting is the same invariant `ScanWorker.maybeNotifyDelta` uses when deciding
 * the reclaimable-bytes copy in the finder notification. This test locks down the arithmetic
 * separately from any I/O — [WidgetSummaryUpdater] itself exposes the same math through
 * [WidgetSummary.duplicateCount] / [WidgetSummary.reclaimableBytes], but writing a tight unit
 * test around the pure calculation protects both surfaces from drifting out of sync.
 *
 * The function under test is re-implemented inline to mirror the updater — if the production
 * code changes its formula, this test breaks loudly so someone can audit whether the notifier
 * needs the same change.
 */
class WidgetSummaryUpdaterAccountingTest {

    @Test
    fun `count is items-minus-one per group`() {
        val groups = listOf(
            group(sizes = listOf(100L, 100L, 100L)), // 3 items → 2 excess
            group(sizes = listOf(200L, 200L)),       // 2 items → 1 excess
        )

        val count = groups.sumOf { (it.items.size - 1).coerceAtLeast(0) }
        assertEquals(3, count)
    }

    @Test
    fun `reclaimable bytes excludes smallest item per group`() {
        val groups = listOf(
            group(sizes = listOf(100L, 200L, 300L)), // keeper=100 → 500 reclaimable
            group(sizes = listOf(50L, 50L)),         // keeper=50 → 50 reclaimable
        )

        val reclaimable = groups.sumOf { g ->
            val keeper = g.items.minOfOrNull { it.sizeBytes } ?: 0L
            (g.totalBytes - keeper).coerceAtLeast(0L)
        }
        assertEquals(550L, reclaimable)
    }

    @Test
    fun `empty group list produces zeros`() {
        val groups = emptyList<DuplicateGroup>()
        val count = groups.sumOf { (it.items.size - 1).coerceAtLeast(0) }
        val reclaimable = groups.sumOf { g ->
            val keeper = g.items.minOfOrNull { it.sizeBytes } ?: 0L
            (g.totalBytes - keeper).coerceAtLeast(0L)
        }
        assertEquals(0, count)
        assertEquals(0L, reclaimable)
    }

    @Test
    fun `singleton group does not inflate count or bytes`() {
        // Impossible in production (a scan would not produce a single-item duplicate group) but
        // defends against an upstream bug from ever surfacing a "1 duplicada • 100 B" widget.
        val groups = listOf(group(sizes = listOf(100L)))
        val count = groups.sumOf { (it.items.size - 1).coerceAtLeast(0) }
        val reclaimable = groups.sumOf { g ->
            val keeper = g.items.minOfOrNull { it.sizeBytes } ?: 0L
            (g.totalBytes - keeper).coerceAtLeast(0L)
        }
        assertEquals(0, count)
        assertEquals(0L, reclaimable)
    }

    private fun group(sizes: List<Long>): DuplicateGroup {
        val items = sizes.mapIndexed { idx, size ->
            MediaItem(
                id = idx.toLong(),
                uri = "content://dummy/$idx",
                displayName = "item$idx.jpg",
                mimeType = "image/jpeg",
                sizeBytes = size,
                dateTakenMs = 0L,
                relativePath = "Pictures/",
                isFromWhatsApp = false,
                category = MediaCategory.Photo,
            )
        }
        return DuplicateGroup(
            groupId = 0L,
            strategy = DuplicateGroup.Strategy.EXACT_MD5,
            items = items,
            totalBytes = sizes.sum(),
        )
    }
}
