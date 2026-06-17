package com.miaclean.app.work

import androidx.work.ListenableWorker
import com.miaclean.app.domain.ScanErrorCode

/**
 * Maps scan error codes to WorkManager Results.
 *
 *  - [ScanErrorCode.UNEXPECTED]: retryable.
 *  - [ScanErrorCode.PERMISSION_REVOKED], [ScanErrorCode.MEDIA_UNAVAILABLE]: permanent failure
 *    (requiring manual user intervention).
 */
fun scanFailureResult(errorCode: ScanErrorCode): ListenableWorker.Result {
    return when (errorCode) {
        ScanErrorCode.UNEXPECTED -> ListenableWorker.Result.retry()
        else -> ListenableWorker.Result.failure()
    }
}
