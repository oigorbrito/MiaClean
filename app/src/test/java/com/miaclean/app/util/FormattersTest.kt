package com.miaclean.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `bytes under 1KB stay in B`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `KB scale`() {
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
    }

    @Test
    fun `MB scale`() {
        assertEquals("1.0 MB", formatBytes(1024L * 1024L))
        assertEquals("4.5 MB", formatBytes((4.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `GB scale`() {
        assertEquals("1.0 GB", formatBytes(1024L * 1024L * 1024L))
        assertEquals("2.5 GB", formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `TB scale saturates at largest unit`() {
        val onePb = 1024L * 1024L * 1024L * 1024L * 1024L
        // Don't assert exact digits of very large values (double precision wobbles), just the
        // unit: we should stop at TB rather than overflow into a fifth unit that doesn't exist.
        val formatted = formatBytes(onePb)
        assert(formatted.endsWith(" TB")) { "expected TB saturation, got $formatted" }
    }
}
