package com.miaclean.app.widget.action

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.miaclean.app.MainActivity
import com.miaclean.app.widget.DuplicatesWidget
import com.miaclean.app.widget.WidgetDependenciesEntryPoint
import com.miaclean.app.work.ScanDispatchResult
import dagger.hilt.android.EntryPointAccessors

/**
 * Handler for the widget's "Escanear" button. Runs on the AppWidgetHost's background executor
 * (Glance routes ActionCallbacks there), so the DataStore + permission reads inside
 * [com.miaclean.app.work.ScanDispatcher.dispatch] are safe to block on directly.
 *
 * Behaviour mirrors the Quick Settings tile ([com.miaclean.app.work.ScanTileService]) and the
 * launcher long-press shortcut:
 *  - `ReadyToEnqueue` → one-time work is enqueued by the dispatcher; no further UI from the
 *    widget. The scan's own foreground notification is the progress surface.
 *  - `NeedsOnboarding` / `NeedsPermission` → launch MainActivity so the user can complete the
 *    missing step. The widget otherwise has no way to guide them.
 *
 * After dispatching, [DuplicatesWidget.updateAll] re-runs `provideGlance`, which picks up any
 * state change (e.g. the first scan of a freshly-permissioned app will flip the widget from
 * `ReadyToScan` to `NoDuplicates`/`HasDuplicates` on the next pass once the scan completes).
 * Calling it synchronously here is a cheap pre-refresh so the "scanning..." narrative on the
 * tile is visible before the scan's foreground notification takes over.
 */
class EnqueueScanAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        val deps = EntryPointAccessors.fromApplication(
            context,
            WidgetDependenciesEntryPoint::class.java,
        )
        when (deps.scanDispatcher().dispatch()) {
            ScanDispatchResult.ReadyToEnqueue -> Unit
            ScanDispatchResult.NeedsOnboarding,
            ScanDispatchResult.NeedsPermission -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
            }
        }
        try {
            DuplicatesWidget().updateAll(context)
        } catch (_: Throwable) {
            // Launcher-process IPC; not fatal to the action itself. Same rationale as
            // [com.miaclean.app.widget.WidgetSummaryUpdater.refreshFromGroups]: widget
            // unpinned, host dead, or a transient binder error should never propagate out
            // of an action callback and tear down the widget's tap handling.
        }
    }
}
