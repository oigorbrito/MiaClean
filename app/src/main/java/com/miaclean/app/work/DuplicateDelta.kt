package com.miaclean.app.work

import com.miaclean.app.domain.MediaCategory

/**
 * Pure function that decides whether a background scan should raise a notification, separated
 * from Android APIs so it can be unit-tested.
 *
 * Rules:
 *   - `current <= 0` → never notify (nothing to clean up).
 *   - `current <= baseline` → don't re-notify. The user is either already aware of this set
 *     (baseline was set to `current` on the last successful post) or the baseline is ahead
 *     because items were restored from trash; in either case a new alert would be noise.
 *   - otherwise → notify with the POSITIVE DELTA (`current - baseline`), so the title reads
 *     "3 new duplicates found" not "27 duplicates found" on a device that has been accruing
 *     clutter for weeks.
 */
internal object DuplicateDelta {

    /** `null` means "don't notify"; otherwise the positive delta to surface in the title. */
    fun computeNotifiableDelta(current: Int, baseline: Int): Int? {
        if (current <= 0) return null
        val delta = current - baseline
        return if (delta > 0) delta else null
    }

    /**
     * Per-[MediaCategory] variant applied independently to each bucket. Rules match
     * [computeNotifiableDelta]: a category is dropped from the result map unless its `excess`
     * is strictly above both zero and its per-category baseline. Categories missing from
     * [baseline] are treated as baseline zero — so a freshly-introduced category (e.g. first
     * screenshot ever detected) surfaces on its first cycle.
     *
     * Returns only the categories that should post a child notification. Empty → no bundle.
     */
    fun computeByCategory(
        current: Map<MediaCategory, CategoryBucket>,
        baseline: Map<MediaCategory, Int>,
    ): Map<MediaCategory, NotifiableCategoryDelta> = buildMap {
        for ((category, bucket) in current) {
            val delta = computeNotifiableDelta(
                current = bucket.excess,
                baseline = baseline[category] ?: 0,
            ) ?: continue
            put(
                category,
                NotifiableCategoryDelta(
                    newItems = delta,
                    reclaimableBytes = bucket.reclaimableBytes,
                ),
            )
        }
    }
}
