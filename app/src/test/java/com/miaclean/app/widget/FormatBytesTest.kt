package com.miaclean.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SI-unit formatter used in the widget's "~X MB recuperáveis" subtext. Tests pin the thresholds
 * so a future refactor that changes the unit boundaries surfaces a visible diff (e.g. a switch
 * to binary prefixes would change every assertion here).
 */
class FormatBytesTest {

    @Test
    fun `zero and negative render as zero bytes`() {
        assertEquals("0 B", FormatBytes.humanReadable(0))
        assertEquals("0 B", FormatBytes.humanReadable(-42))
    }

    @Test
    fun `sub-kilobyte values stay as bytes`() {
        assertEquals("500 B", FormatBytes.humanReadable(500))
        assertEquals("999 B", FormatBytes.humanReadable(999))
    }

    @Test
    fun `kilobyte range rounds to integer`() {
        assertEquals("1 KB", FormatBytes.humanReadable(1_000))
        assertEquals("4 KB", FormatBytes.humanReadable(4_500))
        assertEquals("999 KB", FormatBytes.humanReadable(999_500))
    }

    @Test
    fun `megabyte range uses one decimal`() {
        assertEquals("1.0 MB", FormatBytes.humanReadable(1_000_000))
        assertEquals("4.2 MB", FormatBytes.humanReadable(4_200_000))
        assertEquals("999.9 MB", FormatBytes.humanReadable(999_900_000))
    }

    @Test
    fun `gigabyte range uses one decimal`() {
        assertEquals("1.5 GB", FormatBytes.humanReadable(1_500_000_000L))
    }
}
