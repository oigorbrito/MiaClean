package com.miaclean.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.miaclean.app.MainActivity
import com.miaclean.app.R
import com.miaclean.app.util.formatBytes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emits the "found N new duplicates" notification after a background scan. Kept separate from
 * [ScanWorker]'s foreground notification (scan-in-progress) so each can have a distinct channel
 * with its own importance and user-facing toggle in system settings.
 */
@Singleton
class DuplicateFinderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Posts the "new duplicates" notification if and only if:
     *   1. The system has granted [Manifest.permission.POST_NOTIFICATIONS] (no-op otherwise).
     *   2. [newDuplicateDelta] > 0 — delta, not absolute. Callers are responsible for tracking
     *      the previously-notified count and passing only the positive difference.
     *
     * Returns `true` when the notification was posted; `false` otherwise (so the caller can skip
     * updating the "last notified" persisted counter and try again next cycle).
     */
    fun notifyIfNewDuplicatesFound(newDuplicateDelta: Int, reclaimableBytes: Long): Boolean {
        if (newDuplicateDelta <= 0) return false
        if (!hasPostPermission()) return false

        ensureChannel()

        val title = context.resources.getQuantityString(
            R.plurals.finder_notification_title,
            newDuplicateDelta,
            newDuplicateDelta,
        )
        val body = context.getString(
            R.string.finder_notification_body,
            formatBytes(reclaimableBytes),
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            // Single-task so tapping the notification reuses the existing activity stack rather
            // than spawning a duplicate MainActivity on top.
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_RESULTS, true)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val contentIntent = PendingIntent.getActivity(
            context,
            FINDER_REQUEST_CODE,
            intent,
            pendingFlags,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        // Wrap the post in try/catch for OEM ROMs (e.g. some Samsung/Huawei builds) that have
        // historically thrown SecurityException from the notification manager even when the
        // standard POST_NOTIFICATIONS check returns GRANTED. Returning false on failure keeps
        // the caller from advancing the baseline, so the next scan cycle retries cleanly.
        return try {
            NotificationManagerCompat.from(context).notify(FINDER_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun hasPostPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.finder_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.finder_notification_channel_description)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "mia-clean-finder"
        const val FINDER_NOTIFICATION_ID = 1002
        private const val FINDER_REQUEST_CODE = 2001
    }
}
