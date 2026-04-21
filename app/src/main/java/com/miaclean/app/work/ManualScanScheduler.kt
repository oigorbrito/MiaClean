package com.miaclean.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues a one-shot [ScanWorker] — the counterpart to [PeriodicScanScheduler].
 *
 * Used by:
 *  - [ScanTileService] when the user taps the Quick Settings tile.
 *  - [com.miaclean.app.MainActivity] when launched via the "Escanear agora" app shortcut.
 *
 * Constraints are intentionally thinner than the periodic scheduler's: this is a user-initiated
 * action, so `batteryNotLow` is still respected (no sense draining a critically-low battery to
 * satisfy a tap), but we don't require charging or idle — the user asked for the scan *now*.
 *
 * Enqueued as [ExistingWorkPolicy.KEEP] under [UNIQUE_WORK_NAME] so rapid-fire taps (tile
 * double-tap, shortcut + tile within the same second) coalesce into a single run. The unique
 * name also makes it trivial for the tile to bind its active/inactive state to the work's
 * lifecycle via [observeRunning].
 */
@Singleton
class ManualScanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enqueue() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setConstraints(constraints)
            .addTag(PeriodicScanScheduler.TAG)
            .addTag(MANUAL_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Emits `true` while a manual scan is enqueued/running and `false` otherwise. Intended for
     * the tile's active/inactive binding. The flow is driven by [WorkManager]'s own change
     * stream, so it cold-starts with the current state on subscribe and re-emits on every
     * transition.
     */
    fun observeRunning(): Flow<Boolean> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
            .map { infos -> infos.any { !it.state.isFinished } }

    companion object {
        const val UNIQUE_WORK_NAME = "mia-clean-manual-scan"
        const val MANUAL_TAG = "mia-clean-manual-scan-tag"
    }
}
