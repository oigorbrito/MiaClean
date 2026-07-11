package com.miaclean.app.data.hash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test

class PerceptualHasherIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val hasher = PerceptualHasher(context)

    @Test
    fun `hash returns null for non-image data`() {
        // Create dummy non-image byte array
        val data = ByteArray(100) { 0 }
        val uri = DummyMediaHarness.uriFromBytes(context, data, "non_image")
        val result = hasher.hash(uri)
        assertNull(result)
    }
}
