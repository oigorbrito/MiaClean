package com.miaclean.app.data.hash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class Md5HasherIntegrationTest {

    private lateinit var context: Context
    private lateinit var hasher: Md5Hasher

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        hasher = Md5Hasher(context)
    }

    @Test
    fun hashDummyMediaMatchesKnownVector() {
        val input = "hello world".toByteArray()
        val expected = "5eb63bbbe01eeed093cb22bb8f5acdc3"
        val uri = DummyMediaHarness.uriFromBytes(context, input)
        val result = hasher.hash(uri)
        assertEquals(expected, result)
    }
}
