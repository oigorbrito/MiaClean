package com.miaclean.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.miaclean.app.data.settings.SettingsRepository
import com.miaclean.app.domain.DuplicateGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the post-scan snapshot consumed by [DuplicatesWidget] and pushes the widget to re-render
 * if any instance is pinned. Called from every path that produces a fresh group list:
 *  - [com.miaclean.app.work.ScanWorker] after a successful periodic scan.
 *  - [com.miaclean.app.ui.results.ResultsViewModel] after every in-app `publishGroups` (scan
 *    completion, post-delete refresh, undo restore). This keeps the widget honest when the user
 *    deletes duplicates in-app: the next time they look at the home screen, the counter reflects
 *    what's actually left without waiting for the 24h worker cycle.
 *
 * The "keeper-aware" accounting matches [com.miaclean.app.work.ScanWorker.maybeNotifyDelta]:
 *  - `count` = sum of (group.items.size − 1) across groups (items the user could batch-delete
 *    while keeping one copy per group).
 *  - `reclaimableBytes` = sum of (group.totalBytes − smallest item) as a conservative upper
 *    bound on what a batch-delete would actually free.
 *
 * Invariants worth preserving if this is ever restructured:
 *  - The summary write precedes [GlanceAppWidgetManager.updateAll]. Calling updateAll before the
 *    DataStore edit commits would race against the widget's `provideGlance` read and briefly
 *    render stale numbers on a pinned widget.
 *  - Every failure mode — DataStore write and `updateAll` IPC alike — is swallowed. The
 *    launcher process can be dead, the widget can be unpinned, the disk can be full, or Preferences
 *    DataStore can hit its (very rare) corruption recovery path. None of those should fail a
 *    scan: the worker would return `Result.failure()` and skip the delta notification the user
 *    actually cares about. We intentionally drop the widget update instead and let the next
 *    scan cycle retry.
 *  - `CancellationException` is re-thrown. Structured concurrency must not be short-circuited by
 *    a blanket catch-Throwable here — if the caller's scope cancels (e.g. worker stopped,
 *    ViewModel cleared), the refresh must propagate cancellation like any other suspending call.
 */
@Singleton
class WidgetSummaryUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun refreshFromGroups(groups: List<DuplicateGroup>) {
        val count = groups.sumOf { (it.items.size - 1).coerceAtLeast(0) }
        val reclaimable = groups.sumOf { group ->
            val keeper = group.items.minOfOrNull { it.sizeBytes } ?: 0L
            (group.totalBytes - keeper).coerceAtLeast(0L)
        }
        try {
            settingsRepository.setWidgetSummary(
                WidgetSummary(
                    hasScanned = true,
                    duplicateCount = count,
                    reclaimableBytes = reclaimable,
                ),
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // DataStore write failures (disk full, I/O error during atomic rename) must not
            // fail the scan — the widget simply stays on the previous snapshot until the next
            // scan cycle retries.
            return
        }
        try {
            DuplicatesWidget().updateAll(context)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Launcher-process IPC; not fatal to the scan itself.
        }
    }
}
