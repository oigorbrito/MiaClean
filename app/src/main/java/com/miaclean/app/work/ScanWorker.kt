package com.miaclean.app.work

import android.app.Notification
import android.app.NotificationManager
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
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())
        val final = scanRepository.scan().last()
        return when (final) {
            is ScanProgress.Done -> Result.success()
            is ScanProgress.Failed -> Result.failure()
            else -> Result.success()
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
