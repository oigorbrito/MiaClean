package com.miaclean.app.data.settings

import com.miaclean.app.domain.MediaCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCountCodecTest {

    @Test
    fun `null or blank decodes to empty`() {
        assertTrue(CategoryCountCodec.decode(null).isEmpty())
        assertTrue(CategoryCountCodec.decode("").isEmpty())
        assertTrue(CategoryCountCodec.decode("   ").isEmpty())
    }

    @Test
    fun `empty map encodes to empty string`() {
        assertEquals("", CategoryCountCodec.encode(emptyMap()))
    }

    @Test
    fun `round trip preserves positive counts`() {
        val original = mapOf(
            MediaCategory.Screenshot to 3,
            MediaCategory.Selfie to 1,
            MediaCategory.Video to 12,
        )
        assertEquals(original, CategoryCountCodec.decode(CategoryCountCodec.encode(original)))
    }

    @Test
    fun `zero and negative counts are dropped on encode`() {
        val encoded = CategoryCountCodec.encode(
            mapOf(
                MediaCategory.Screenshot to 5,
                MediaCategory.Selfie to 0,
                MediaCategory.Meme to -3,
            ),
        )
        assertEquals(mapOf(MediaCategory.Screenshot to 5), CategoryCountCodec.decode(encoded))
    }

    @Test
    fun `unknown category tokens are dropped on decode`() {
        // Simulates a downgrade path or a manually-edited prefs file — worker must never crash
        // on a token that disappeared from the enum, it just rebuilds that slot on the next
        // successful post.
        val decoded = CategoryCountCodec.decode("Screenshot=4;Ghost=9;Photo=2")
        assertEquals(
            mapOf(MediaCategory.Screenshot to 4, MediaCategory.Photo to 2),
            decoded,
        )
    }

    @Test
    fun `malformed segments are dropped on decode`() {
        val decoded = CategoryCountCodec.decode("Screenshot=4;malformed;Photo=notANumber;Meme=3")
        assertEquals(
            mapOf(MediaCategory.Screenshot to 4, MediaCategory.Meme to 3),
            decoded,
        )
    }

    @Test
    fun `zero or negative counts in wire format are dropped on decode`() {
        // Defense-in-depth: even if an older codec revision wrote zeroes, the reader must treat
        // them as "no baseline" so ScanWorker gets `emptyMap()` for that category instead of a
        // misleading explicit zero that could let a real delta slip through the >0 guard.
        val decoded = CategoryCountCodec.decode("Screenshot=4;Photo=0;Video=-2")
        assertEquals(mapOf(MediaCategory.Screenshot to 4), decoded)
    }
}
