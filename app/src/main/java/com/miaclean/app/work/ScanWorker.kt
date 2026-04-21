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
     * Buckets the current cache by [com.miaclean.app.domain.DuplicateGroup.dominantCategory],
     * computes per-category deltas against the persisted baseline, and posts either a single
     * notification (one category changed) or a grouped bundle (≥2 categories changed).
     *
     * "Excess" and "reclaimable" semantics: for each group, we count items minus one (the copy
     * a batch-delete would keep) and sum bytes minus the smallest item (upper-bound free-space
     * estimate, aligning with the notification's `~` wording). Same invariant as PR #16 — just
     * partitioned by category.
     *
     * Baseline advances only for categories we actually posted about; unchanged categories keep
     * their previous value so a deleted category (count went to zero) still counts as "nothing
     * to re-notify" on the next cycle without being forgotten.
     *
     * Migration from PR #16's single-int baseline: the very first bundle-aware cycle on an
     * upgraded device sees `lastNotifiedDuplicateCountsByCategory == emptyMap()` while
     * `lastNotifiedDuplicateCount > 0`. Instead of treating every category as baseline zero
     * (which would splat 6 notifs), we seed each category's baseline with its current excess
     * and skip notifying this cycle. The user misses one delta on the upgrade cycle in exchange
     * for a clean, non-spammy transition.
     */
    private suspend fun maybeNotifyDelta() {
        if (!settingsRepository.currentNotifyOnNewDuplicates()) return
        val groups = scanRepository.loadGroups()

        val currentByCategory = groups
            .groupBy { it.dominantCategory }
            .mapValues { (_, groupsInCategory) ->
                val excess = groupsInCategory.sumOf { (it.items.size - 1).coerceAtLeast(0) }
                val reclaimable = groupsInCategory.sumOf { group ->
                    val keeper = group.items.minOfOrNull { it.sizeBytes } ?: 0L
                    (group.totalBytes - keeper).coerceAtLeast(0L)
                }
                CategoryBucket(excess = excess, reclaimableBytes = reclaimable)
            }

        val baselineByCategory = settingsRepository.currentLastNotifiedDuplicateCountsByCategory()
        val legacyBaseline = settingsRepository.currentLastNotifiedDuplicateCount()
        if (baselineByCategory.isEmpty() && legacyBaseline > 0) {
            val seed = currentByCategory.mapValues { (_, bucket) -> bucket.excess }
                .filterValues { it > 0 }
            settingsRepository.setLastNotifiedDuplicateCountsByCategory(seed)
            // Consume the legacy counter so subsequent cycles don't re-trigger migration if the
            // user clears the per-category map (e.g. Settings → restore defaults in a future PR).
            settingsRepository.setLastNotifiedDuplicateCount(0)
            return
        }

        val deltas = DuplicateDelta.computeByCategory(currentByCategory, baselineByCategory)
        if (deltas.isEmpty()) return

        val posted = notifier.notifyNewDuplicates(deltas)
        if (posted) {
            val updated = baselineByCategory.toMutableMap()
            for (category in deltas.keys) {
                updated[category] = currentByCategory.getValue(category).excess
            }
            settingsRepository.setLastNotifiedDuplicateCountsByCategory(updated)
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
