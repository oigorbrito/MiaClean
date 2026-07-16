package com.miaclean.shared.dedup

import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultDuplicateRankingAndSelectionTest {

    private val ranking = DefaultDuplicateRanking()
    private val selection = DefaultDuplicateSelection()

    private fun createItem(id: Long, sizeBytes: Long, dateTakenMs: Long) = MediaItem(
        id = id,
        uri = "uri$id",
        displayName = "name$id",
        mimeType = "image/jpeg",
        sizeBytes = sizeBytes,
        dateTakenMs = dateTakenMs,
        relativePath = "Pictures/",
        isFromWhatsApp = false,
        category = MediaCategory.Photo
    )

    @Test
    fun `ranking sorts by largest size then oldest date`() {
        val group = DuplicateGroup(
            groupId = 1,
            strategy = DuplicateGroup.Strategy.EXACT_MD5,
            items = listOf(
                createItem(1, sizeBytes = 1000, dateTakenMs = 2000), // small, new
                createItem(2, sizeBytes = 2000, dateTakenMs = 1000), // large, old
                createItem(3, sizeBytes = 2000, dateTakenMs = 500)   // large, oldest -> BEST
            ),
            totalBytes = 5000
        )

        val ranked = ranking.rank(group)

        assertEquals(3L, ranked[0].id)
        assertEquals(2L, ranked[1].id)
        assertEquals(1L, ranked[2].id)
    }

    @Test
    fun `selection keeps the first item and selects the rest for deletion`() {
        val rankedItems = listOf(
            createItem(3, 2000, 500),
            createItem(2, 2000, 1000),
            createItem(1, 1000, 2000)
        )

        val selected = selection.selectForDeletion(rankedItems)

        assertEquals(2, selected.size)
        assertEquals(2L, selected[0].id)
        assertEquals(1L, selected[1].id)
    }
}
