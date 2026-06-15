package com.miaclean.app.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.InputStream

class BitmapUtilsTest {

    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val uri = mockk<Uri>()
    private val inputStream = mockk<InputStream>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(BitmapFactory::class)
        every { context.contentResolver } returns contentResolver
        every { contentResolver.openInputStream(uri) } returns inputStream
    }

    @After
    fun tearDown() {
        unmockkStatic(BitmapFactory::class)
    }

    @Test
    fun `decodeDownscaled calculates correct inSampleSize for large image`() {
        // Mock image dimensions (e.g., 4000x3000, ~12MP)
        every { BitmapFactory.decodeStream(any(), any(), any()) } answers {
            val options = arg<BitmapFactory.Options>(2)
            if (options.inJustDecodeBounds) {
                options.outWidth = 4000
                options.outHeight = 3000
            }
            null // Return null for just decode bounds
        } andThenAnswer {
            val options = arg<BitmapFactory.Options>(2)
            assertEquals(8, options.inSampleSize) // 4000 / 8 = 500 (stays under 640)
            mockk<Bitmap>() // Return a mock bitmap for the second call
        }

        val result = BitmapUtils.decodeDownscaled(context, uri, 640)

        verify(exactly = 2) { contentResolver.openInputStream(uri) }
        assertEquals(true, result != null)
    }

    @Test
    fun `decodeDownscaled returns null on invalid URI`() {
        every { contentResolver.openInputStream(uri) } throws Exception("Failed to open")

        val result = BitmapUtils.decodeDownscaled(context, uri, 320)

        assertNull(result)
    }

    @Test
    fun `decodeDownscaled returns null if image has zero dimensions`() {
        every { BitmapFactory.decodeStream(any(), any(), any()) } answers {
            val options = arg<BitmapFactory.Options>(2)
            options.outWidth = 0
            options.outHeight = 0
            null
        }

        val result = BitmapUtils.decodeDownscaled(context, uri, 320)

        assertNull(result)
    }
}
