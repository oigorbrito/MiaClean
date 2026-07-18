package com.miaclean.app.data.classify

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Internal logger for image classification flow.
 * Provides a simple abstraction for recording start, success, failure and duration
 * without external analytics dependencies.
 */
@Singleton
class ClassifierEventLogger @Inject constructor() {

    fun logStart(classifierName: String, mediaId: Long) {
        Log.i(TAG, "[$classifierName] Started classification for mediaId=$mediaId")
    }

    fun logSuccess(classifierName: String, mediaId: Long, category: String, durationMs: Long) {
        Log.i(TAG, "[$classifierName] Success for mediaId=$mediaId. Result=$category. Duration=${durationMs}ms")
    }

    fun logFailure(classifierName: String, mediaId: Long, error: ErrorCategory, message: String?, durationMs: Long) {
        Log.e(TAG, "[$classifierName] Failure for mediaId=$mediaId. Error=$error. Message=${message ?: "None"}. Duration=${durationMs}ms")
    }

    companion object {
        private const val TAG = "ClassifierEventLogger"
    }
}
