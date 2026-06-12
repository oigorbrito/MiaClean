package com.miaclean.app.data.classify


/** Base exception for all classification-related failures. */
sealed class ClassificationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thrown when an image file cannot be read or decoded. */
class InvalidImageException(message: String, cause: Throwable? = null) : ClassificationException(message, cause)

/** Thrown when a network-dependent resource (e.g. unbundled ML model) is unavailable. */
class ClassificationNetworkException(message: String, cause: Throwable? = null) : ClassificationException(message, cause)

/** Thrown when classification takes longer than the allowed timeout. */
class ClassificationTimeoutException(message: String, cause: Throwable? = null) : ClassificationException(message, cause)

/** Thrown when the classification service (MediaPipe, ML Kit) returns an empty/invalid response. */
class EmptyClassificationResponseException(message: String) : ClassificationException(message)

/** Thrown when the underlying ML service is temporarily unavailable. */
class ClassificationServiceUnavailableException(message: String, cause: Throwable? = null) : ClassificationException(message, cause)

/** Fallback for any other unexpected errors during classification. */
class UnexpectedClassificationException(message: String, cause: Throwable? = null) : ClassificationException(message, cause)
