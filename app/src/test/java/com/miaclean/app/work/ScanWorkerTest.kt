package com.miaclean.app.work

import com.miaclean.app.domain.ScanErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanWorkerTest {

    @Test
    fun `unexpected scan failure maps to retry`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.retry().javaClass,
            scanFailureResult(ScanErrorCode.UNEXPECTED).javaClass,
        )
    }

    @Test
    fun `permission or io failure maps to failure`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.failure().javaClass,
            scanFailureResult(ScanErrorCode.PERMISSION_REVOKED).javaClass,
        )
        assertEquals(
            androidx.work.ListenableWorker.Result.failure().javaClass,
            scanFailureResult(ScanErrorCode.MEDIA_UNAVAILABLE).javaClass,
        )
    }
}
