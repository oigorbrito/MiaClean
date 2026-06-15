package com.miaclean.app.work

import com.miaclean.app.R
import com.miaclean.app.domain.ScanErrorCode
import com.miaclean.app.domain.ScanProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanWorkerTest {

    @Test
    fun `unexpected scan failure maps to retry`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.retry().javaClass,
            scanFailureResult(ScanErrorCode.UNEXPECTED, R.string.scan_error_unexpected).javaClass,
        )
    }

    @Test
    fun `permission or io failure maps to failure`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.failure().javaClass,
            scanFailureResult(ScanErrorCode.PERMISSION_REVOKED, R.string.scan_error_permission_revoked).javaClass,
        )
        assertEquals(
            androidx.work.ListenableWorker.Result.failure().javaClass,
            scanFailureResult(ScanErrorCode.MEDIA_UNAVAILABLE, R.string.scan_error_media_unavailable).javaClass,
        )
    }

    private fun scanFailureResult(errorCode: ScanErrorCode, reasonResId: Int): androidx.work.ListenableWorker.Result {
        val final = ScanProgress.Failed(errorCode, reasonResId)
        return if (final.errorCode == ScanErrorCode.UNEXPECTED) {
            androidx.work.ListenableWorker.Result.retry()
        } else {
            androidx.work.ListenableWorker.Result.failure()
        }
    }
}
