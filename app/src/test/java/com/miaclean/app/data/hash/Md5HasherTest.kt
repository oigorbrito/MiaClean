package com.miaclean.app.data.hash

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class Md5HasherTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var hasher: Md5Hasher
    private val testUri = mockk<Uri>()

    @Before
    fun setup() {
        context = mockk()
        contentResolver = mockk()
        every { context.contentResolver } returns contentResolver
        hasher = Md5Hasher(context)
    }

    @Test
    fun `hash calculates correct MD5 for known input`() {
        // "hello world" MD5 is 5eb63bbbe01eeed093cb22bb8f5acdc3
        val input = "hello world".toByteArray()
        every { contentResolver.openInputStream(testUri) } returns ByteArrayInputStream(input)

        val result = hasher.hash(testUri)

        assertEquals("5eb63bbbe01eeed093cb22bb8f5acdc3", result)
    }

    @Test
    fun `hash handles empty file`() {
        val input = ByteArray(0)
        every { contentResolver.openInputStream(testUri) } returns ByteArrayInputStream(input)

        val result = hasher.hash(testUri)

        // MD5 of empty string is d41d8cd98f00b204e9800998ecf8427e
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", result)
    }

    @Test
    fun `hash returns null when input stream cannot be opened`() {
        every { contentResolver.openInputStream(testUri) } returns null

        val result = hasher.hash(testUri)

        assertNull(result)
    }

    @Test
    fun `hash handles large input spanning multiple buffer reads`() {
        // 256 KB — forces multiple iterations inside hashStream (buffer is 64 KB)
        val largeBytes = TestMediaHelper.deterministicBytes(256 * 1024)
        every { contentResolver.openInputStream(testUri) } returns TestMediaHelper.streamOf(largeBytes)

        val result = hasher.hash(testUri)

        // Compute expected MD5 independently
        val expected = java.security.MessageDigest.getInstance("MD5")
            .digest(largeBytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, result)
    }

    @Test
    fun `hash is deterministic for identical content`() {
        val bytes = TestMediaHelper.deterministicBytes(1024)
        every { contentResolver.openInputStream(testUri) } returns TestMediaHelper.streamOf(bytes)
        val first = hasher.hash(testUri)

        every { contentResolver.openInputStream(testUri) } returns TestMediaHelper.streamOf(bytes)
        val second = hasher.hash(testUri)

        assertEquals(first, second)
    }

    @Test
    fun `hash produces different digests for different content`() {
        val bytesA = TestMediaHelper.deterministicBytes(128, seed = 1)
        val bytesB = TestMediaHelper.deterministicBytes(128, seed = 2)

        every { contentResolver.openInputStream(testUri) } returns TestMediaHelper.streamOf(bytesA)
        val hashA = hasher.hash(testUri)

        every { contentResolver.openInputStream(testUri) } returns TestMediaHelper.streamOf(bytesB)
        val hashB = hasher.hash(testUri)

        assertNotEquals(hashA, hashB)
    }

    @Test
    fun `hash matches known test vectors`() {
        for ((input, expected) in TestMediaHelper.MD5_VECTORS) {
            every { contentResolver.openInputStream(testUri) } returns TestMediaHelper.streamOf(input)
            val result = hasher.hash(testUri)
            assertEquals("MD5 mismatch for input of size ${input.size}", expected, result)
        }
    }

    @Test
    fun `hash handles binary content with null bytes`() {
        val binaryData = TestMediaHelper.repeatedBytes(0x00, 512)
        every { contentResolver.openInputStream(testUri) } returns TestMediaHelper.streamOf(binaryData)

        val result = hasher.hash(testUri)

        val expected = java.security.MessageDigest.getInstance("MD5")
            .digest(binaryData)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, result)
    }
}
