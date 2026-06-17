package com.miaclean.app.ui.scan

import androidx.annotation.StringRes
import com.miaclean.app.R
import com.miaclean.app.domain.ScanErrorCode

/**
 * Maps [ScanErrorCode] to friendly user-facing strings.
 */
object ClassifierErrorMapper {

    @StringRes
    fun mapToFriendlyMessage(errorCode: ScanErrorCode): Int {
        return when (errorCode) {
            ScanErrorCode.PERMISSION_REVOKED -> R.string.scan_error_permission_revoked
            ScanErrorCode.MEDIA_UNAVAILABLE -> R.string.scan_error_media_unavailable
            ScanErrorCode.CLASSIFICATION_ISSUE -> R.string.scan_error_classification
            ScanErrorCode.UNEXPECTED -> R.string.classifier_error_unexpected
        }
    }
}
