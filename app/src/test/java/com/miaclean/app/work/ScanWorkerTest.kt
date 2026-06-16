package com.miaclean.app.work

import com.miaclean.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanWorkerTest {

    @Test
    fun `unexpected scan failure maps to retry`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.retry()::class.java,
            scanFailureResult(R.string.scan_error_unexpected)::class.java,
        )
    }

    @Test
    fun `permission or io failure maps to failure`() {
        assertEquals(
            androidx.work.ListenableWorker.Result.failure()::class.java,
            scanFailureResult(R.string.scan_error_permission_revoked)::class.java,
        )
        assertEquals(
            androidx.work.ListenableWorker.Result.failure()::class.java,
            scanFailureResult(R.string.scan_error_media_unavailable)::class.java,
        )
    }

    private fun scanFailureResult(reasonResId: Int): androidx.work.ListenableWorker.Result {
        return when (reasonResId) {
            R.string.scan_error_unexpected -> androidx.work.ListenableWorker.Result.retry()
            else -> androidx.work.ListenableWorker.Result.failure()
        }
    }
}
