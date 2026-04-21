package com.miaclean.app.work

import com.miaclean.app.domain.MediaCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDeltaTest {

    @Test
    fun `empty current map produces empty delta`() {
        val deltas = DuplicateDelta.computeByCategory(
            current = emptyMap(),
            baseline = mapOf(MediaCategory.Photo to 5),
        )
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `category missing from baseline treated as baseline zero`() {
        val deltas = DuplicateDelta.computeByCategory(
            current = mapOf(MediaCategory.Screenshot to CategoryBucket(3, 12_000_000L)),
            baseline = emptyMap(),
        )
        assertEquals(1, deltas.size)
        val delta = deltas.getValue(MediaCategory.Screenshot)
        assertEquals(3, delta.newItems)
        assertEquals(12_000_000L, delta.reclaimableBytes)
    }

    @Test
    fun `category at or below baseline is dropped`() {
        val deltas = DuplicateDelta.computeByCategory(
            current = mapOf(
                MediaCategory.Selfie to CategoryBucket(5, 20L),
                MediaCategory.Photo to CategoryBucket(2, 8L),
            ),
            baseline = mapOf(
                MediaCategory.Selfie to 5,
                MediaCategory.Photo to 9,
            ),
        )
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `mix of above and below baseline keeps only the above`() {
        val deltas = DuplicateDelta.computeByCategory(
            current = mapOf(
                MediaCategory.Screenshot to CategoryBucket(7, 30_000L),
                MediaCategory.Selfie to CategoryBucket(3, 10_000L),
                MediaCategory.Meme to CategoryBucket(4, 15_000L),
            ),
            baseline = mapOf(
                MediaCategory.Screenshot to 5,
                MediaCategory.Selfie to 3,
                MediaCategory.Meme to 0,
            ),
        )
        assertEquals(setOf(MediaCategory.Screenshot, MediaCategory.Meme), deltas.keys)
        assertEquals(2, deltas.getValue(MediaCategory.Screenshot).newItems)
        assertEquals(4, deltas.getValue(MediaCategory.Meme).newItems)
    }

    @Test
    fun `bucket with zero or negative excess is dropped`() {
        // Keeper-aware excess can legitimately be 0 (a fresh group with exactly one item left
        // after trashing the duplicate). Protecting the negative case is a belt-and-braces
        // guard against a future bug upstream in the bucketing math.
        val deltas = DuplicateDelta.computeByCategory(
            current = mapOf(
                MediaCategory.Photo to CategoryBucket(0, 0L),
                MediaCategory.Video to CategoryBucket(-1, 500L),
            ),
            baseline = emptyMap(),
        )
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `reclaimable bytes propagate from bucket to delta unchanged`() {
        val deltas = DuplicateDelta.computeByCategory(
            current = mapOf(MediaCategory.Video to CategoryBucket(2, 2_147_483_648L)),
            baseline = mapOf(MediaCategory.Video to 1),
        )
        // Bytes are a category-total snapshot, not a delta; verifying the value is copied
        // verbatim guards against a future accidental subtraction-style computation that would
        // make the notification show "0 MB" when baseline bytes equal current bytes.
        assertEquals(2_147_483_648L, deltas.getValue(MediaCategory.Video).reclaimableBytes)
    }
}
