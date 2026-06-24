package com.miaclean.app.work

import androidx.annotation.StringRes
import androidx.work.ListenableWorker.Result
import com.miaclean.app.R

/**
 * Maps a fatal scan error (represented by its UI string resource ID) to a WorkManager [Result].
 *
 * Transient or unexpected errors trigger a [Result.retry] to allow the system to attempt the scan
 * again later (e.g. when the device is idle/charging). Permanent errors like revoked permissions
 * or missing media map to [Result.failure].
 */
fun scanFailureResult(@StringRes errorStringRes: Int): Result {
    return when (errorStringRes) {
        R.string.scan_error_unexpected -> Result.retry()
        R.string.scan_error_permission_revoked -> Result.failure()
        R.string.scan_error_media_unavailable -> Result.failure()
        else -> Result.failure()
    }
}
