package com.miaclean.shared.dedup

import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuplicateOrchestratorTest {

    private val orchestrator = DuplicateOrchestrator()

    private fun createItem(id: Long, sizeBytes: Long = 1000L) = MediaItem(
        id = id,
        uri = "uri$id",
        displayName = "name$id",
        mimeType = "image/jpeg",
        sizeBytes = sizeBytes,
        dateTakenMs = 1000L * id,
        relativePath = "Pictures/",
        isFromWhatsApp = false,
        category = MediaCategory.Photo
    )

    private fun createCandidate(id: Long, md5: String = "hash1", sizeBytes: Long = 1000L) = DedupCandidate(
        item = createItem(id, sizeBytes),
        md5 = md5,
        pHash = null,
        embeddingHash = null
    )

    @Test
    fun `process groups candidates and ranks them`() {
        val candidates = listOf(
            createCandidate(id = 1, md5 = "hash1", sizeBytes = 1000L), // old, small
            createCandidate(id = 2, md5 = "hash1", sizeBytes = 2000L)  // new, large -> BEST
        )

        val groups = orchestrator.process(candidates)

        assertEquals(1, groups.size)
        val group = groups[0]
        assertEquals(2, group.items.size)
        // Item 2 should be first because it is larger
        assertEquals(2L, group.items[0].id)
        assertEquals(1L, group.items[1].id)
    }

    @Test
    fun `getAutoSelection returns all items except the best one in each group`() {
        val groups = listOf(
            DuplicateGroup(
                groupId = 1,
                strategy = DuplicateGroup.Strategy.EXACT_MD5,
                items = listOf(createItem(1), createItem(2), createItem(3)), // 1 is kept, 2 and 3 selected
                totalBytes = 3000L
            ),
            DuplicateGroup(
                groupId = 2,
                strategy = DuplicateGroup.Strategy.EXACT_MD5,
                items = listOf(createItem(4), createItem(5)), // 4 is kept, 5 selected
                totalBytes = 2000L
            )
        )

        val selection = orchestrator.getAutoSelection(groups)

        assertEquals(3, selection.size)
        assertTrue(selection.contains(2L))
        assertTrue(selection.contains(3L))
        assertTrue(selection.contains(5L))
    }
}
