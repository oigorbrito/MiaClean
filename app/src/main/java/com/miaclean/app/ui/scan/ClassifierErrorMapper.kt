package com.miaclean.app.ui.scan

import com.miaclean.app.R
import com.miaclean.app.data.classify.ErrorCategory
import com.miaclean.app.domain.ScanErrorCode

/** Maps internal classification/scan errors to user-facing string resource IDs. */
object ClassifierErrorMapper {

    fun mapToFriendlyMessage(category: ErrorCategory): Int = when (category) {
        ErrorCategory.IMAGE_INVALID -> R.string.classifier_error_image_invalid
        ErrorCategory.TIMEOUT -> R.string.classifier_error_timeout
        ErrorCategory.NETWORK_ERROR -> R.string.classifier_error_network
        ErrorCategory.SERVICE_UNAVAILABLE -> R.string.classifier_error_service_unavailable
        ErrorCategory.EMPTY_RESPONSE -> R.string.classifier_error_empty_response
        ErrorCategory.UNEXPECTED -> R.string.classifier_error_unexpected
    }

    fun mapScanError(errorCode: ScanErrorCode): Int = when (errorCode) {
        ScanErrorCode.PERMISSION_REVOKED -> R.string.scan_error_permission_revoked
        ScanErrorCode.MEDIA_UNAVAILABLE -> R.string.scan_error_media_unavailable
        ScanErrorCode.UNEXPECTED -> R.string.scan_error_unexpected
    }
}
