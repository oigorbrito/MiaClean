package com.miaclean.app.ui.scan

import androidx.annotation.StringRes
import com.miaclean.app.R
import com.miaclean.app.data.classify.ErrorCategory

/**
 * Maps internal classification [ErrorCategory] to friendly user-facing strings.
 */
object ClassifierErrorMapper {

    @StringRes
    fun mapToFriendlyMessage(error: ErrorCategory): Int {
        return when (error) {
            ErrorCategory.IMAGE_INVALID -> R.string.classifier_error_image_invalid
            ErrorCategory.TIMEOUT -> R.string.classifier_error_timeout
            ErrorCategory.NETWORK_ERROR -> R.string.classifier_error_network
            ErrorCategory.SERVICE_UNAVAILABLE -> R.string.classifier_error_service_unavailable
            ErrorCategory.EMPTY_RESPONSE -> R.string.classifier_error_empty_response
            ErrorCategory.UNEXPECTED -> R.string.classifier_error_unexpected
        }
    }
}
