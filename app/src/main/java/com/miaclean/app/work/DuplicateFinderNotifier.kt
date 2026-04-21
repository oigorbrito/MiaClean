package com.miaclean.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.PluralsRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.miaclean.app.MainActivity
import com.miaclean.app.R
import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.util.formatBytes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emits the "found N new duplicates" notification after a background scan. Kept separate from
 * [ScanWorker]'s foreground notification (scan-in-progress) so each can have a distinct channel
 * with its own importance and user-facing toggle in system settings.
 *
 * Bundle layout (PR #21):
 *   - **0 categories with delta** → no-op, returns `false`.
 *   - **1 category with delta** → a single standalone notification at [FINDER_NOTIFICATION_ID]
 *     with no group key, so Android doesn't render the collapsed-group chrome for a single item.
 *     Tap deep-links to Results with the category filter preselected.
 *   - **≥2 categories with delta** → a summary notification at [FINDER_NOTIFICATION_ID] plus one
 *     child per category, all tied together via [BUNDLE_GROUP_KEY]. Tapping a child filters;
 *     tapping the summary opens Results unfiltered.
 *
 * The summary ID intentionally reuses [FINDER_NOTIFICATION_ID] so upgrades from PR #16's
 * single-notif model replace the pre-bundle alert in the tray instead of accumulating.
 */
@Singleton
class DuplicateFinderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Posts the bundle if any categories carry a positive delta and the system has granted
     * [Manifest.permission.POST_NOTIFICATIONS]. Returns `true` when at least one notification
     * was posted; `false` otherwise so the caller can skip advancing its per-category baselines
     * and retry the same set next cycle.
     *
     * `reclaimableBytes` inside each [NotifiableCategoryDelta] is a snapshot (category total),
     * not a delta — we don't have per-item first-seen timestamps, and a "new bytes since last
     * post" figure isn't recoverable from the current schema. Acknowledged by the `~` in the
     * user-facing string.
     */
    fun notifyNewDuplicates(
        deltasByCategory: Map<MediaCategory, NotifiableCategoryDelta>,
    ): Boolean {
        if (deltasByCategory.isEmpty()) return false
        if (!hasPostPermission()) return false

        ensureChannel()
        val manager = NotificationManagerCompat.from(context)

        // Single-category path: standalone notif without the group chrome.
        if (deltasByCategory.size == 1) {
            val (category, delta) = deltasByCategory.entries.first()
            val notification = buildCategoryNotification(
                category = category,
                delta = delta,
                groupKey = null,
            )
            return tryNotify(manager, FINDER_NOTIFICATION_ID, notification)
        }

        // Multi-category path: summary + children with a shared group key. Post children first
        // so when the summary lands, the system already has members to fold into the group and
        // heads-up rendering picks the right layout on first paint.
        val totalNew = deltasByCategory.values.sumOf { it.newItems }
        val totalBytes = deltasByCategory.values.sumOf { it.reclaimableBytes }

        var postedAny = false
        for ((category, delta) in deltasByCategory) {
            val childNotification = buildCategoryNotification(
                category = category,
                delta = delta,
                groupKey = BUNDLE_GROUP_KEY,
            )
            val childPosted = tryNotify(
                manager,
                childNotificationId(category),
                childNotification,
            )
            postedAny = postedAny || childPosted
        }

        val summary = buildSummaryNotification(
            totalNewItems = totalNew,
            totalReclaimableBytes = totalBytes,
            categoryCount = deltasByCategory.size,
        )
        val summaryPosted = tryNotify(manager, FINDER_NOTIFICATION_ID, summary)

        return postedAny || summaryPosted
    }

    private fun buildCategoryNotification(
        category: MediaCategory,
        delta: NotifiableCategoryDelta,
        groupKey: String?,
    ): android.app.Notification {
        val title = context.resources.getQuantityString(
            category.childTitlePlural(),
            delta.newItems,
            delta.newItems,
        )
        val body = context.getString(
            R.string.finder_notification_body,
            formatBytes(delta.reclaimableBytes),
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(category))
        if (groupKey != null) builder.setGroup(groupKey)
        return builder.build()
    }

    private fun buildSummaryNotification(
        totalNewItems: Int,
        totalReclaimableBytes: Long,
        categoryCount: Int,
    ): android.app.Notification {
        val title = context.resources.getQuantityString(
            R.plurals.finder_notification_summary_title,
            totalNewItems,
            totalNewItems,
        )
        val body = context.getString(
            R.string.finder_notification_summary_body,
            categoryCount,
            formatBytes(totalReclaimableBytes),
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(category = null))
            .setGroup(BUNDLE_GROUP_KEY)
            .setGroupSummary(true)
            .build()
    }

    /**
     * Deep-link into Results, optionally preselecting a category filter. Each distinct target
     * (summary + N children) gets its own request code so `FLAG_UPDATE_CURRENT` refreshes the
     * right PendingIntent rather than silently overwriting a sibling's extras.
     */
    private fun contentIntent(category: MediaCategory?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_RESULTS, true)
            if (category != null) {
                putExtra(MainActivity.EXTRA_CATEGORY_FILTER, category.name)
            }
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val requestCode = requestCodeFor(category)
        return PendingIntent.getActivity(context, requestCode, intent, pendingFlags)
    }

    /**
     * Request codes must be stable per (summary, category) identity so PendingIntent lookups hit
     * the right slot across notification updates. Offset by category ordinal with a wide stride
     * so future additions to [MediaCategory] don't collide with other PendingIntents in the app.
     */
    private fun requestCodeFor(category: MediaCategory?): Int =
        if (category == null) SUMMARY_REQUEST_CODE else CHILD_REQUEST_CODE_BASE + category.ordinal

    /**
     * Per-category child notification IDs. Offset by [CHILD_NOTIFICATION_ID_BASE] so none of the
     * children collide with [FINDER_NOTIFICATION_ID] (summary) or [ScanWorker]'s foreground ID.
     */
    private fun childNotificationId(category: MediaCategory): Int =
        CHILD_NOTIFICATION_ID_BASE + category.ordinal

    private fun tryNotify(
        manager: NotificationManagerCompat,
        id: Int,
        notification: android.app.Notification,
    ): Boolean =
        // Wrap the post in try/catch for OEM ROMs (e.g. some Samsung/Huawei builds) that have
        // historically thrown SecurityException from the notification manager even when the
        // standard POST_NOTIFICATIONS check returns GRANTED. Returning false on failure keeps
        // the caller from advancing the baseline, so the next scan cycle retries cleanly.
        try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
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

    @PluralsRes
    private fun MediaCategory.childTitlePlural(): Int = when (this) {
        MediaCategory.Screenshot -> R.plurals.finder_notification_child_title_screenshot
        MediaCategory.Selfie -> R.plurals.finder_notification_child_title_selfie
        MediaCategory.Meme -> R.plurals.finder_notification_child_title_meme
        MediaCategory.Photo -> R.plurals.finder_notification_child_title_photo
        MediaCategory.Video -> R.plurals.finder_notification_child_title_video
        MediaCategory.Other -> R.plurals.finder_notification_child_title_other
    }

    companion object {
        const val CHANNEL_ID = "mia-clean-finder"

        /**
         * Notification ID for the single-category post AND the multi-category summary. Reusing
         * the same ID across modes means a cycle that emits one notif replaces a prior cycle's
         * summary (and vice-versa) without leaking stale notifications in the tray.
         */
        const val FINDER_NOTIFICATION_ID = 1002

        private const val BUNDLE_GROUP_KEY = "mia-clean-finder-bundle"
        private const val SUMMARY_REQUEST_CODE = 2001
        private const val CHILD_REQUEST_CODE_BASE = 2100
        private const val CHILD_NOTIFICATION_ID_BASE = 1100
    }
}
