package com.miaclean.app.data.hash

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
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
}
