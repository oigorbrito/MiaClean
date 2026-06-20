package com.miaclean.app.work

import androidx.work.ListenableWorker.Result
import com.miaclean.app.domain.ScanErrorCode

/**
 * Maps [ScanErrorCode] to WorkManager [Result] objects.
 */
fun scanFailureResult(errorCode: ScanErrorCode): Result {
    return when (errorCode) {
        ScanErrorCode.UNEXPECTED -> Result.retry()
        else -> Result.failure()
    }
}
