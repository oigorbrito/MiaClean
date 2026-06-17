package com.miaclean.app.data.classify

import com.miaclean.app.R
import com.miaclean.app.domain.ScanErrorCode
import com.miaclean.app.ui.scan.ClassifierErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifierRobustnessTest {

    @Test
    fun mapToFriendlyMessage_returns_correct_string_resource_for_each_ScanErrorCode() {
        assertEquals(
            R.string.scan_error_permission_revoked,
            ClassifierErrorMapper.mapToFriendlyMessage(ScanErrorCode.PERMISSION_REVOKED)
        )
        assertEquals(
            R.string.scan_error_media_unavailable,
            ClassifierErrorMapper.mapToFriendlyMessage(ScanErrorCode.MEDIA_UNAVAILABLE)
        )
        assertEquals(
            R.string.scan_error_classification,
            ClassifierErrorMapper.mapToFriendlyMessage(ScanErrorCode.CLASSIFICATION_ISSUE)
        )
        assertEquals(
            R.string.classifier_error_unexpected,
            ClassifierErrorMapper.mapToFriendlyMessage(ScanErrorCode.UNEXPECTED)
        )
    }

    @Test
    fun ScanErrorCode_enum_has_expected_values() {
        val expected = listOf(
            "PERMISSION_REVOKED",
            "MEDIA_UNAVAILABLE",
            "CLASSIFICATION_ISSUE",
            "UNEXPECTED"
        )
        val actual = ScanErrorCode.entries.map { it.name }
        assertEquals(expected, actual)
    }
}
