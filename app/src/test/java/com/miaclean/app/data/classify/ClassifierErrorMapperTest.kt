package com.miaclean.app.data.classify

import android.content.Context
import android.util.Log
import com.miaclean.app.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ClassifierErrorMapperTest {

    private val context: Context = mockk()
    private lateinit var mapper: ClassifierErrorMapper

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        mapper = ClassifierErrorMapper(context)

        // Mock localized strings
        every { context.getString(R.string.error_classifier_invalid_image) } returns "Invalid image"
        every { context.getString(R.string.error_classifier_network) } returns "Network error"
        every { context.getString(R.string.error_classifier_timeout) } returns "Timeout"
        every { context.getString(R.string.error_classifier_empty) } returns "Empty response"
        every { context.getString(R.string.error_classifier_service) } returns "Service unavailable"
        every { context.getString(R.string.error_classifier_unexpected) } returns "Unexpected error"
    }

    @After
    fun teardown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `maps InvalidImageException correctly`() {
        val error = InvalidImageException("test")
        assertEquals("Invalid image", mapper.map(error))
    }

    @Test
    fun `maps ClassificationNetworkException correctly`() {
        val error = ClassificationNetworkException("test")
        assertEquals("Network error", mapper.map(error))
    }

    @Test
    fun `maps ClassificationTimeoutException correctly`() {
        val error = ClassificationTimeoutException("test")
        assertEquals("Timeout", mapper.map(error))
    }

    @Test
    fun `maps EmptyClassificationResponseException correctly`() {
        val error = EmptyClassificationResponseException("test")
        assertEquals("Empty response", mapper.map(error))
    }

    @Test
    fun `maps ClassificationServiceUnavailableException correctly`() {
        val error = ClassificationServiceUnavailableException("test")
        assertEquals("Service unavailable", mapper.map(error))
    }

    @Test
    fun `maps generic Exception to unexpected error`() {
        val error = Exception("test")
        assertEquals("Unexpected error", mapper.map(error))
    }
}
