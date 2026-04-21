package com.miaclean.app.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.miaclean.app.MiaCleanApp
import com.miaclean.app.R
import com.miaclean.app.data.ScanRepository
import com.miaclean.app.data.settings.SettingsRepository
import com.miaclean.app.data.settings.UserSettingsRepository
import com.miaclean.app.domain.ScanProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.last

/** Runs the full scan pipeline in the background via [androidx.work.WorkManager]. */
@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scanRepository: ScanRepository,
    private val userSettings: UserSettingsRepository,
    private val settingsRepository: SettingsRepository,
    private val notifier: DuplicateFinderNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())
        val safUris = userSettings.currentSafTreeUris().toList()
        val final = scanRepository.scan(additionalSafTreeUris = safUris).last()
        return when (final) {
            is ScanProgress.Done -> {
                maybeNotifyDelta(final.duplicates)
                Result.success()
            }
            is ScanProgress.Failed -> Result.failure()
            else -> Result.success()
        }
    }

    /**
     * Posts the "N new duplicates" notification when two conditions hold:
     *   1. The absolute duplicate count went UP since the last notification (a DELTA, not a
     *      snapshot — otherwise the user sees the same "5 duplicates" every 24h forever).
     *   2. The user still has `notifyOnNewDuplicates` enabled in Settings.
     *
     * The "last notified" counter is only advanced after a successful post so that a cycle where
     * POST_NOTIFICATIONS is revoked or the permission check silently fails doesn't "burn" the
     * delta — the next cycle tries again against the same baseline.
     */
    private suspend fun maybeNotifyDelta(currentDuplicates: Int) {
        if (!settingsRepository.currentNotifyOnNewDuplicates()) return
        val baseline = settingsRepository.currentLastNotifiedDuplicateCount()
        val delta = DuplicateDelta.computeNotifiableDelta(currentDuplicates, baseline) ?: return

        val reclaimable = scanRepository.loadGroups().sumOf { it.totalBytes }
        val posted = notifier.notifyIfNewDuplicatesFound(
            newDuplicateDelta = delta,
            reclaimableBytes = reclaimable,
        )
        if (posted) {
            settingsRepository.setLastNotifiedDuplicateCount(currentDuplicates)
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val ctx = applicationContext
        val notification: Notification = NotificationCompat.Builder(ctx, MiaCleanApp.SCAN_CHANNEL_ID)
            .setContentTitle(ctx.getString(R.string.work_notification_title))
            .setContentText(ctx.getString(R.string.work_notification_content))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                SCAN_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(SCAN_NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val SCAN_NOTIFICATION_ID = 1001
    }
}
