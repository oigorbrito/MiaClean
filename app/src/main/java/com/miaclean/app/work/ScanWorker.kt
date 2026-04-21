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
                maybeNotifyDelta()
                Result.success()
            }
            is ScanProgress.Failed -> Result.failure()
            else -> Result.success()
        }
    }

    /**
     * Posts the "N new duplicates" notification when two conditions hold:
     *   1. The EXCESS duplicate count (items the user could delete while still keeping one copy
     *      per group) went UP since the last notification (a DELTA, not a snapshot — otherwise
     *      the user sees the same "5 duplicates" every 24h forever).
     *   2. The user still has `notifyOnNewDuplicates` enabled in Settings.
     *
     * "Excess" and "reclaimable" semantics are subtly different from `ScanProgress.Done.duplicates`
     * (which counts every item in every group, including the keeper). The worker-side notification
     * exists to entice the user to open the app and clean — so both numbers (count + bytes) model
     * what would actually happen on a batch-delete that keeps one copy per group.
     *
     * The "last notified" counter is only advanced after a successful post so that a cycle where
     * POST_NOTIFICATIONS is revoked or the permission check silently fails doesn't "burn" the
     * delta — the next cycle tries again against the same baseline.
     */
    private suspend fun maybeNotifyDelta() {
        if (!settingsRepository.currentNotifyOnNewDuplicates()) return
        val groups = scanRepository.loadGroups()
        val excessCount = groups.sumOf { (it.items.size - 1).coerceAtLeast(0) }
        val baseline = settingsRepository.currentLastNotifiedDuplicateCount()
        val delta = DuplicateDelta.computeNotifiableDelta(excessCount, baseline) ?: return

        // Reclaimable = total group bytes minus one keeper per group. Using the smallest item as
        // the "keeper" is a conservative (upper-bound) estimate of what the user could actually
        // free — aligns with the notification's "approximately X" wording.
        val reclaimable = groups.sumOf { group ->
            val keeper = group.items.minOfOrNull { it.sizeBytes } ?: 0L
            (group.totalBytes - keeper).coerceAtLeast(0L)
        }
        val posted = notifier.notifyIfNewDuplicatesFound(
            newDuplicateDelta = delta,
            reclaimableBytes = reclaimable,
        )
        if (posted) {
            settingsRepository.setLastNotifiedDuplicateCount(excessCount)
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
