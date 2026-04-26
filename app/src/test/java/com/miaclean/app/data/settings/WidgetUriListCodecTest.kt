package com.miaclean.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistence contract tests for [WidgetUriListCodec]. Locks the wire format and the failure
 * modes so the widget can degrade to "no thumbnails this cycle" instead of crashing on a
 * corrupted prefs file (forced reinstall would be the alternative).
 */
class WidgetUriListCodecTest {

    @Test
    fun `null or blank decodes to empty`() {
        assertTrue(WidgetUriListCodec.decode(null).isEmpty())
        assertTrue(WidgetUriListCodec.decode("").isEmpty())
        assertTrue(WidgetUriListCodec.decode("   ").isEmpty())
    }

    @Test
    fun `empty list encodes to empty string`() {
        assertEquals("", WidgetUriListCodec.encode(emptyList()))
    }

    @Test
    fun `round trip preserves order`() {
        val original = listOf(
            "content://media/external/images/media/123",
            "content://media/external/images/media/456",
            "content://media/external/images/media/789",
        )
        assertEquals(original, WidgetUriListCodec.decode(WidgetUriListCodec.encode(original)))
    }

    @Test
    fun `blank entries are dropped on encode`() {
        val encoded = WidgetUriListCodec.encode(
            listOf(
                "content://media/external/images/media/1",
                "",
                "   ",
                "content://media/external/images/media/2",
            ),
        )
        assertEquals(
            listOf(
                "content://media/external/images/media/1",
                "content://media/external/images/media/2",
            ),
            WidgetUriListCodec.decode(encoded),
        )
    }

    @Test
    fun `blank segments are dropped on decode`() {
        // Defense-in-depth: a downgrade or hand-edited prefs file with stray separators must
        // not produce empty-string entries that the loader would then try to parse as URIs.
        val decoded = WidgetUriListCodec.decode("a||b|||c")
        assertEquals(listOf("a", "b", "c"), decoded)
    }
}
