package com.miaclean.app.ui.scan

import androidx.annotation.StringRes
import com.miaclean.app.R
import com.miaclean.app.domain.ScanErrorCode

/**
 * Maps shared [ScanErrorCode] to friendly Android resource strings.
 */
object ClassifierErrorMapper {

    @StringRes
    fun mapToFriendlyMessage(error: ScanErrorCode): Int {
        return when (error) {
            ScanErrorCode.CLASSIFICATION_ISSUE -> R.string.classifier_error_unexpected
            ScanErrorCode.PERMISSION_REVOKED -> R.string.scan_error_permission_revoked
            ScanErrorCode.MEDIA_UNAVAILABLE -> R.string.scan_error_media_unavailable
            ScanErrorCode.UNEXPECTED -> R.string.scan_error_unexpected
        }
    }
}
