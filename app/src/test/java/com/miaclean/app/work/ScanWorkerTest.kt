package com.miaclean.app.work

import com.miaclean.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

import io.mockk.mockk
import androidx.work.WorkerParameters
import android.content.Context
import com.miaclean.app.data.ScanRepository
import com.miaclean.app.data.settings.SettingsRepository
import com.miaclean.app.data.settings.UserSettingsRepository
import com.miaclean.app.widget.WidgetSummaryUpdater

class ScanWorkerTest {

    private val context = mockk<Context>(relaxed = true)
    private val params = mockk<WorkerParameters>(relaxed = true)
    private val scanRepository = mockk<ScanRepository>()
    private val userSettings = mockk<UserSettingsRepository>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val notifier = mockk<DuplicateFinderNotifier>()
    private val widgetSummaryUpdater = mockk<WidgetSummaryUpdater>()

    private val worker = ScanWorker(
        context,
        params,
        scanRepository,
        userSettings,
        settingsRepository,
        notifier,
        widgetSummaryUpdater
    )

    @Test
    fun `unexpected scan failure maps to retry`() {
        val result = worker.mapFailure(R.string.scan_error_unexpected)
        assertEquals(
            androidx.work.ListenableWorker.Result.retry().javaClass,
            result.javaClass,
        )
    }

    @Test
    fun `permission or io failure maps to failure`() {
        val permissionResult = worker.mapFailure(R.string.scan_error_permission_revoked)
        assertEquals(
            androidx.work.ListenableWorker.Result.failure().javaClass,
            permissionResult.javaClass,
        )
        val ioResult = worker.mapFailure(R.string.scan_error_media_unavailable)
        assertEquals(
            androidx.work.ListenableWorker.Result.failure().javaClass,
            ioResult.javaClass,
        )
    }
}
