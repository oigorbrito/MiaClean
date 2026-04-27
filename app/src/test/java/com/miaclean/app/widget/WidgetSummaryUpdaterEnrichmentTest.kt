package com.miaclean.app.widget

import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enrichment helpers ([WidgetSummaryUpdater.pickThumbnailUris] +
 * [WidgetSummaryUpdater.bucketByDominantCategory]) tested independently of the I/O wrapper so
 * the production updater stays a thin orchestrator. The accounting test (`...AccountingTest`)
 * covers `count + reclaimableBytes`; this file focuses on the additions.
 */
class WidgetSummaryUpdaterEnrichmentTest {

    // ---------- pickThumbnailUris ----------

    @Test
    fun `pickThumbnailUris returns empty when limit is zero`() {
        val groups = listOf(group(items = listOf(itemAt(uri = "u1"), itemAt(uri = "u2"))))
        assertTrue(WidgetSummaryUpdater.pickThumbnailUris(groups, limit = 0).isEmpty())
    }

    @Test
    fun `pickThumbnailUris returns empty when no group has at least 2 items`() {
        // Singletons cannot be duplicates; the helper must guard the invariant even if upstream
        // mistakenly emits one. Otherwise a malformed group would seed a "phantom" thumbnail.
        val groups = listOf(group(items = listOf(itemAt(uri = "u1"))))
        assertTrue(WidgetSummaryUpdater.pickThumbnailUris(groups, limit = 3).isEmpty())
    }

    @Test
    fun `pickThumbnailUris orders groups by descending reclaimable bytes`() {
        // Group A: keeper 100, total 300 → reclaimable 200
        // Group B: keeper  10, total  40 → reclaimable  30
        // Group C: keeper 200, total 700 → reclaimable 500 (winner)
        val groupA = group(
            items = listOf(itemAt(uri = "a1", size = 100), itemAt(uri = "a2", size = 200)),
            totalBytes = 300,
        )
        val groupB = group(
            items = listOf(itemAt(uri = "b1", size = 10), itemAt(uri = "b2", size = 30)),
            totalBytes = 40,
        )
        val groupC = group(
            items = listOf(itemAt(uri = "c1", size = 200), itemAt(uri = "c2", size = 500)),
            totalBytes = 700,
        )
        val picked = WidgetSummaryUpdater.pickThumbnailUris(
            groups = listOf(groupA, groupB, groupC),
            limit = 3,
        )
        assertEquals(listOf("c1", "a1", "b1"), picked)
    }

    @Test
    fun `pickThumbnailUris caps at the requested limit`() {
        val groups = (1..5).map { idx ->
            group(
                items = listOf(
                    itemAt(uri = "g${idx}a", size = 1L),
                    itemAt(uri = "g${idx}b", size = idx.toLong() * 1000L),
                ),
                totalBytes = 1L + idx.toLong() * 1000L,
            )
        }
        val picked = WidgetSummaryUpdater.pickThumbnailUris(groups, limit = 3)
        assertEquals(3, picked.size)
        // Largest reclaimable is g5 (~5000), then g4, then g3.
        assertEquals(listOf("g5a", "g4a", "g3a"), picked)
    }

    @Test
    fun `pickThumbnailUris drops groups with blank first uri`() {
        // Items with a blank URI should never reach the widget — they would render as a broken
        // image slot. Keeping the filter at the codec boundary is belt-and-suspenders for the
        // upstream filter in the codec.
        val groups = listOf(
            group(items = listOf(itemAt(uri = "", size = 1), itemAt(uri = "x", size = 2))),
            group(items = listOf(itemAt(uri = "ok", size = 10), itemAt(uri = "y", size = 20))),
        )
        val picked = WidgetSummaryUpdater.pickThumbnailUris(groups, limit = 3)
        assertEquals(listOf("ok"), picked)
    }

    // ---------- bucketByDominantCategory ----------

    @Test
    fun `bucketByDominantCategory sums excess per dominant category`() {
        val groups = listOf(
            group( // 3 screenshots → excess 2
                items = listOf(
                    itemAt(category = MediaCategory.Screenshot),
                    itemAt(category = MediaCategory.Screenshot),
                    itemAt(category = MediaCategory.Screenshot),
                ),
            ),
            group( // 2 screenshots → excess 1
                items = listOf(
                    itemAt(category = MediaCategory.Screenshot),
                    itemAt(category = MediaCategory.Screenshot),
                ),
            ),
            group( // 4 selfies → excess 3
                items = listOf(
                    itemAt(category = MediaCategory.Selfie),
                    itemAt(category = MediaCategory.Selfie),
                    itemAt(category = MediaCategory.Selfie),
                    itemAt(category = MediaCategory.Selfie),
                ),
            ),
        )
        val buckets = WidgetSummaryUpdater.bucketByDominantCategory(groups)
        assertEquals(
            mapOf(MediaCategory.Screenshot to 3, MediaCategory.Selfie to 3),
            buckets,
        )
    }

    @Test
    fun `bucketByDominantCategory drops zero entries`() {
        // Singleton groups shouldn't reach this helper, but if they do we must not surface a
        // "0 photos" chip in the widget.
        val groups = listOf(
            group(items = listOf(itemAt(category = MediaCategory.Photo))),
            group(
                items = listOf(
                    itemAt(category = MediaCategory.Selfie),
                    itemAt(category = MediaCategory.Selfie),
                ),
            ),
        )
        val buckets = WidgetSummaryUpdater.bucketByDominantCategory(groups)
        assertEquals(mapOf(MediaCategory.Selfie to 1), buckets)
    }

    @Test
    fun `bucketByDominantCategory empty input yields empty map`() {
        assertTrue(WidgetSummaryUpdater.bucketByDominantCategory(emptyList()).isEmpty())
    }

    // ---------- Helpers ----------

    private fun itemAt(
        uri: String = "content://dummy",
        size: Long = 100L,
        category: MediaCategory = MediaCategory.Photo,
    ): MediaItem = MediaItem(
        id = uri.hashCode().toLong(),
        uri = uri,
        displayName = "$uri.jpg",
        mimeType = "image/jpeg",
        sizeBytes = size,
        dateTakenMs = 0L,
        relativePath = "Pictures/",
        isFromWhatsApp = false,
        category = category,
    )

    private fun group(
        items: List<MediaItem>,
        totalBytes: Long = items.sumOf { it.sizeBytes },
    ): DuplicateGroup = DuplicateGroup(
        groupId = items.hashCode().toLong(),
        strategy = DuplicateGroup.Strategy.EXACT_MD5,
        items = items,
        totalBytes = totalBytes,
    )
}
