package com.miaclean.app.data.classify

/**
 * Robustness and observability categories for classification errors.
 */
enum class ErrorCategory {
    IMAGE_INVALID,
    EMPTY_RESPONSE,
    TIMEOUT,
    NETWORK_ERROR,
    SERVICE_UNAVAILABLE,
    UNEXPECTED
}
