package com.miaclean.app.work

import com.miaclean.app.domain.ScanErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanWorkerTest {

    @Test
    fun `unexpected scan failure maps to retry`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.retry().javaClass,
            ScanErrorCode.UNEXPECTED.toWorkerResult().javaClass,
        )
    }

    @Test
    fun `permission or io failure maps to failure`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.failure().javaClass,
            ScanErrorCode.PERMISSION_REVOKED.toWorkerResult().javaClass,
        )
        assertEquals(
            androidx.work.ListenableWorker.Result.failure().javaClass,
            ScanErrorCode.MEDIA_UNAVAILABLE.toWorkerResult().javaClass,
        )
    }
}
