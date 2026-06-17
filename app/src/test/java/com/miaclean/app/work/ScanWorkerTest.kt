package com.miaclean.app.work

import com.miaclean.app.domain.ScanErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanWorkerTest {

    @Test
    fun unexpected_scan_failure_maps_to_retry() {
        assertEquals(
            androidx.work.ListenableWorker.Result.retry().javaClass,
            scanFailureResult(ScanErrorCode.UNEXPECTED).javaClass,
        )
    }

    @Test
    fun permission_or_io_failure_maps_to_failure() {
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
