package com.miaclean.app.widget

import com.miaclean.app.domain.MediaCategory

/**
 * Persistent snapshot written by [com.miaclean.app.work.ScanWorker] / [com.miaclean.app.data.ScanRepository]
 * after each successful scan. The widget reads it directly from DataStore — no Room query path
 * so launcher-process renders don't pay a database attach + decryption cost every 30 minutes.
 *
 * [thumbnailUris] and [categoryCounts] are best-effort enrichment for the 2x2 layout:
 *  - [thumbnailUris] holds up to 3 `content://` URIs of individual items picked from the top
 *    groups by reclaimable bytes. Widget rendering tolerates missing/invalid URIs (item deleted
 *    between scan and refresh) by falling back to the thumb-less text layout for that slot.
 *  - [categoryCounts] is the keeper-aware excess per `dominantCategory`, used for the chip-row
 *    breakdown. Sum across entries MAY NOT equal [duplicateCount] when a group has mixed-category
 *    items (dominantCategory wins, tied items undercount) — treat the chip row as a hint, same
 *    spirit as [com.miaclean.app.domain.DuplicateGroup.dominantCategory] itself.
 */
data class WidgetSummary(
    val hasScanned: Boolean,
    val duplicateCount: Int,
    val reclaimableBytes: Long,
    val thumbnailUris: List<String> = emptyList(),
    val categoryCounts: Map<MediaCategory, Int> = emptyMap(),
) {
    companion object {
        val Empty = WidgetSummary(
            hasScanned = false,
            duplicateCount = 0,
            reclaimableBytes = 0L,
            thumbnailUris = emptyList(),
            categoryCounts = emptyMap(),
        )
    }
}

/**
 * Rendered state of the duplicates home-screen widget. Four mutually-exclusive branches — picked
 * by [WidgetStateMapper.map] from the (summary, onboardingComplete, hasMediaPermission) triple.
 *
 * Kept as a sealed hierarchy (not enum) so the content branch can carry `count + bytes` without
 * the other states needing meaningless zero fields.
 */
sealed interface WidgetState {
    /** Fresh install / onboarding not finished. Tap opens the welcome flow. */
    data object OnboardingPending : WidgetState

    /**
     * Onboarding complete but the user revoked media permission in system settings. We cannot
     * scan on their behalf; tapping opens MainActivity so the runtime-permission flow can re-run.
     */
    data object NeedsPermission : WidgetState

    /** Permissions fine, scan has not run yet. Button enqueues the first scan. */
    data object ReadyToScan : WidgetState

    /** Latest scan found no duplicates. Button re-runs the scan. */
    data object NoDuplicates : WidgetState

    /**
     * Latest scan found duplicates. Button re-runs the scan; body taps open Results.
     *
     * [thumbnailUris] and [categoryCounts] are best-effort — absent or empty on pre-upgrade
     * snapshots persisted before the enrichment landed, and also empty if every candidate URI
     * has become unreadable between scan and render. The widget layout falls back to the
     * text-only rendering when these are empty, so the mapper does not need to gate on them.
     */
    data class HasDuplicates(
        val count: Int,
        val reclaimableBytes: Long,
        val thumbnailUris: List<String> = emptyList(),
        val categoryCounts: Map<MediaCategory, Int> = emptyMap(),
    ) : WidgetState
}

/**
 * Pure decision function: widget state from persisted inputs. Extracted from the Glance composable
 * so it can be unit-tested without a Glance host / AppWidgetManager, and so future preconditions
 * (e.g. billing / freemium gate on widget content) slot into one place.
 *
 * Precedence rationale:
 *   1. Onboarding before permission — same as [com.miaclean.app.work.ScanGate.decide] so the
 *      widget, tile, and shortcut all agree on what the user should see first.
 *   2. `!hasScanned` beats count-based branches — a stale `duplicateCount > 0` from a previous
 *      install should not be possible (DataStore is cleared on uninstall), but if the flag is
 *      missing while count > 0 we still want to offer a scan rather than surface zombie data.
 *   3. `count == 0` with `hasScanned` branches to [WidgetState.NoDuplicates], so the user sees
 *      a deliberate "tudo limpo" instead of the scan-CTA they had before running the scan.
 */
object WidgetStateMapper {
    fun map(
        summary: WidgetSummary,
        onboardingComplete: Boolean,
        hasMediaPermission: Boolean,
    ): WidgetState = when {
        !onboardingComplete -> WidgetState.OnboardingPending
        !hasMediaPermission -> WidgetState.NeedsPermission
        !summary.hasScanned -> WidgetState.ReadyToScan
        summary.duplicateCount <= 0 -> WidgetState.NoDuplicates
        else -> WidgetState.HasDuplicates(
            count = summary.duplicateCount,
            reclaimableBytes = summary.reclaimableBytes.coerceAtLeast(0L),
            thumbnailUris = summary.thumbnailUris,
            categoryCounts = summary.categoryCounts.filterValues { it > 0 },
        )
    }
}
