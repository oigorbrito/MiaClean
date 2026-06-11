package com.miaclean.app.data.classify

import com.miaclean.app.R
import com.miaclean.app.ui.scan.ClassifierErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifierRobustnessTest {

    @Test
    fun `mapToFriendlyMessage returns correct string resource for each ErrorCategory`() {
        assertEquals(
            R.string.classifier_error_image_invalid,
            ClassifierErrorMapper.mapToFriendlyMessage(ErrorCategory.IMAGE_INVALID)
        )
        assertEquals(
            R.string.classifier_error_timeout,
            ClassifierErrorMapper.mapToFriendlyMessage(ErrorCategory.TIMEOUT)
        )
        assertEquals(
            R.string.classifier_error_network,
            ClassifierErrorMapper.mapToFriendlyMessage(ErrorCategory.NETWORK_ERROR)
        )
        assertEquals(
            R.string.classifier_error_service_unavailable,
            ClassifierErrorMapper.mapToFriendlyMessage(ErrorCategory.SERVICE_UNAVAILABLE)
        )
        assertEquals(
            R.string.classifier_error_empty_response,
            ClassifierErrorMapper.mapToFriendlyMessage(ErrorCategory.EMPTY_RESPONSE)
        )
        assertEquals(
            R.string.classifier_error_unexpected,
            ClassifierErrorMapper.mapToFriendlyMessage(ErrorCategory.UNEXPECTED)
        )
    }

    @Test
    fun `ErrorCategory enum has expected values`() {
        val expected = listOf(
            "IMAGE_INVALID",
            "EMPTY_RESPONSE",
            "TIMEOUT",
            "NETWORK_ERROR",
            "SERVICE_UNAVAILABLE",
            "UNEXPECTED"
        )
        val actual = ErrorCategory.entries.map { it.name }
        assertEquals(expected, actual)
    }
}
