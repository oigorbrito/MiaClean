package com.miaclean.app.data.classify

import android.content.Context
import android.util.Log
import com.miaclean.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps technical [Throwable]s from the classification pipeline into user-friendly localized strings.
 */
@Singleton
class ClassifierErrorMapper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Maps the error to a friendly string without exposing internal details. */
    fun mapToFriendlyMessage(error: Throwable): String {
        val resId = when (error) {
            is InvalidImageException -> R.string.error_classifier_invalid_image
            is ClassificationNetworkException -> R.string.error_classifier_network
            is ClassificationTimeoutException -> R.string.error_classifier_timeout
            is EmptyClassificationResponseException -> R.string.error_classifier_empty
            is ClassificationServiceUnavailableException -> R.string.error_classifier_service
            else -> R.string.error_classifier_unexpected
        }
        return context.getString(resId)
    }

    /** Logs technical details for internal tracking. Does not log sensitive URIs or payloads. */
    fun logInternal(error: Throwable) {
        // We log the type and message but avoid logging personal data.
        // Stack trace is included for debugging.
        Log.e(TAG, "Non-fatal classification error: ${error.javaClass.simpleName}", error)
    }

    private companion object {
        const val TAG = "ClassifierErrorMapper"
    }
}
