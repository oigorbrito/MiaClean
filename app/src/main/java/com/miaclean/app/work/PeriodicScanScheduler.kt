package com.miaclean.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules (or cancels) the periodic background [ScanWorker] using [WorkManager].
 *
 * The schedule is enqueued as a unique periodic work request so that repeated calls to [enable]
 * from e.g. [com.miaclean.app.MiaCleanApp.onCreate] don't pile up multiple runs. The default
 * policy is [ExistingPeriodicWorkPolicy.KEEP]: on app restart the in-flight schedule is reused;
 * only toggling the preference off and on creates a fresh schedule.
 */
@Singleton
class PeriodicScanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enable() {
        val constraints = Constraints.Builder()
            // Scan touches the local filesystem only; unmetered is declared mainly to hint the
            // scheduler that we'd prefer wifi windows (where the device is more likely to be
            // idle/charging) rather than to actually consume bandwidth.
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .build()

        val request = PeriodicWorkRequestBuilder<ScanWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 6,
            flexTimeIntervalUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "mia-clean-periodic-scan"
        const val TAG = "mia-clean-scan"
    }
}
