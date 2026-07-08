package com.miaclean.app.work

import com.miaclean.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanWorkerTest {

    @Test
    fun `unexpected scan failure maps to retry`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.retry().javaClass,
            ScanWorker.scanFailureResult(R.string.scan_error_unexpected).javaClass,
        )
    }

    @Test
    fun `permission or io failure maps to failure`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.failure().javaClass,
            ScanWorker.scanFailureResult(R.string.scan_error_permission_revoked).javaClass,
        )
        assertEquals(
            androidx.work.ListenableWorker.Result.failure().javaClass,
            ScanWorker.scanFailureResult(R.string.scan_error_media_unavailable).javaClass,
        )
    }
}
