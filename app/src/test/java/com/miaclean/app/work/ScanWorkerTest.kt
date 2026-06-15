package com.miaclean.app.work

import com.miaclean.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanWorkerTest {

    @Test
    fun `unexpected scan failure maps to retry`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.retry(),
            scanFailureResult(com.miaclean.app.domain.ScanErrorCode.UNEXPECTED),
        )
    }

    @Test
    fun `permission or io failure maps to failure`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.failure(),
            scanFailureResult(com.miaclean.app.domain.ScanErrorCode.PERMISSION_REVOKED),
        )
        assertEquals(
            androidx.work.ListenableWorker.Result.failure(),
            scanFailureResult(com.miaclean.app.domain.ScanErrorCode.MEDIA_UNAVAILABLE),
        )
    }
}
