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
 *  - Failures from `updateAll` are swallowed. The launcher process can be dead, the widget can
 *    be unpinned, or the AppWidgetHost can throw transient IPC errors — none of those should
 *    fail a scan. Swallowing Throwable keeps the worker green.
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
        settingsRepository.setWidgetSummary(
            WidgetSummary(
                hasScanned = true,
                duplicateCount = count,
                reclaimableBytes = reclaimable,
            ),
        )
        try {
            DuplicatesWidget().updateAll(context)
        } catch (_: Throwable) {
            // Launcher-process IPC; not fatal to the scan itself.
        }
    }
}
