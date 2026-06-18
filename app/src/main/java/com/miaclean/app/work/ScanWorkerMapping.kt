package com.miaclean.app.work

import androidx.work.ListenableWorker.Result
import com.miaclean.app.domain.ScanErrorCode
import com.miaclean.app.R

fun ScanErrorCode.toWorkerResult(): Result = when (this) {
    ScanErrorCode.UNEXPECTED -> Result.retry()
    ScanErrorCode.PERMISSION_REVOKED -> Result.failure()
    ScanErrorCode.MEDIA_UNAVAILABLE -> Result.failure()
    else -> Result.failure()
}

fun ScanErrorCode.toResourceId(): Int = when (this) {
    ScanErrorCode.UNEXPECTED -> R.string.scan_error_unexpected
    ScanErrorCode.PERMISSION_REVOKED -> R.string.scan_error_permission_revoked
    ScanErrorCode.MEDIA_UNAVAILABLE -> R.string.scan_error_media_unavailable
    else -> R.string.scan_error_unexpected
}
