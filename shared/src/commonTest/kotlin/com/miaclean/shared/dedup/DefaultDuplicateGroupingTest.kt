package com.miaclean.shared.dedup

import com.miaclean.app.domain.DuplicateGroup
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultDuplicateGroupingTest {

    private val grouping = DefaultDuplicateGrouping()

    private fun createItem(id: Long) = MediaItem(
        id = id,
        uri = "uri$id",
        displayName = "name$id",
        mimeType = "image/jpeg",
        sizeBytes = 1000L * id,
        dateTakenMs = 1000L * id,
        relativePath = "Pictures/",
        isFromWhatsApp = false,
        category = MediaCategory.Photo
    )

    private fun createCandidate(
        id: Long,
        md5: String = "md5_$id",
        pHash: String? = null,
        embeddingHash: FloatArray? = null
    ) = DedupCandidate(
        item = createItem(id),
        md5 = md5,
        pHash = pHash,
        embeddingHash = embeddingHash
    )

    @Test
    fun `test exact md5 grouping`() {
        val candidates = listOf(
            createCandidate(1, md5 = "hash1"),
            createCandidate(2, md5 = "hash1"),
            createCandidate(3, md5 = "hash2")
        )

        val groups = grouping.group(candidates)

        assertEquals(1, groups.size)
        assertEquals(DuplicateGroup.Strategy.EXACT_MD5, groups[0].strategy)
        assertEquals(2, groups[0].items.size)
        assertTrue(groups[0].items.any { it.id == 1L })
        assertTrue(groups[0].items.any { it.id == 2L })
    }

    @Test
    fun `test phash perceptual grouping`() {
        val candidates = listOf(
            createCandidate(1, md5 = "hash1", pHash = "1111111111111111"), // distance 0
            createCandidate(2, md5 = "hash2", pHash = "1111111111111110"), // distance 1
            createCandidate(3, md5 = "hash3", pHash = "0000000000000000")  // distance 16
        )

        val groups = grouping.group(candidates)

        assertEquals(1, groups.size)
        assertEquals(DuplicateGroup.Strategy.PERCEPTUAL_PHASH, groups[0].strategy)
        assertEquals(2, groups[0].items.size)
        assertTrue(groups[0].items.any { it.id == 1L })
        assertTrue(groups[0].items.any { it.id == 2L })
    }

    @Test
    fun `test semantic embed grouping`() {
        val candidates = listOf(
            createCandidate(1, md5 = "hash1", embeddingHash = floatArrayOf(1f, 0f, 0f)),
            createCandidate(2, md5 = "hash2", embeddingHash = floatArrayOf(0.99f, 0.1f, 0f)),
            createCandidate(3, md5 = "hash3", embeddingHash = floatArrayOf(0f, 1f, 0f))
        )

        val groups = grouping.group(candidates)

        assertEquals(1, groups.size)
        assertEquals(DuplicateGroup.Strategy.SEMANTIC_EMBED, groups[0].strategy)
        assertEquals(2, groups[0].items.size)
        assertTrue(groups[0].items.any { it.id == 1L })
        assertTrue(groups[0].items.any { it.id == 2L })
    }
}
