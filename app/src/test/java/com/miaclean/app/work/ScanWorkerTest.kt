package com.miaclean.app.work

import com.miaclean.app.domain.ScanErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanWorkerTest {

    @Test
    fun `unexpected scan failure maps to retry`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.retry()::class.java,
            scanFailureResult(ScanErrorCode.UNEXPECTED)::class.java,
        )
    }

    @Test
    fun `permission or io failure maps to failure`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.failure()::class.java,
            scanFailureResult(ScanErrorCode.PERMISSION_REVOKED)::class.java,
        )
        assertEquals(
            androidx.work.ListenableWorker.Result.failure()::class.java,
            scanFailureResult(ScanErrorCode.MEDIA_UNAVAILABLE)::class.java,
        )
    }

    private fun scanFailureResult(errorCode: ScanErrorCode): androidx.work.ListenableWorker.Result {
        return when (errorCode) {
            ScanErrorCode.PERMISSION_REVOKED -> androidx.work.ListenableWorker.Result.failure()
            ScanErrorCode.MEDIA_UNAVAILABLE -> androidx.work.ListenableWorker.Result.failure()
            ScanErrorCode.UNEXPECTED -> androidx.work.ListenableWorker.Result.retry()
        }
    }
}
