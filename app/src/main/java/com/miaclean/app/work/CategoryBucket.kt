package com.miaclean.app.work

import com.miaclean.app.domain.MediaCategory

/**
 * Per-[MediaCategory] aggregate computed on every worker cycle. `excess` is the "keeper-aware"
 * count — for each duplicate group, items minus one (the one the user would keep on batch-delete),
 * summed across groups whose [com.miaclean.app.domain.DuplicateGroup.dominantCategory] matches.
 * `reclaimableBytes` is the same accounting applied to bytes: total group bytes minus the smallest
 * item per group as an upper-bound estimate of what a one-tap clean would free.
 *
 * Why excess instead of raw item count: the notification is a call to action ("N items you could
 * delete right now"), so it must not include the item the user will keep. PR #16 figured this out
 * after a round of bad-number comments on the global counter — this per-category variant carries
 * the same invariant forward.
 */
data class CategoryBucket(val excess: Int, val reclaimableBytes: Long)

/**
 * What [DuplicateFinderNotifier] consumes per-category: the *delta* (new items since last post)
 * plus the category's *total* reclaimable bytes. The bytes figure is intentionally a snapshot,
 * not a delta, because we can't reliably distinguish "new in this cycle" items from "old items
 * that are still there" at the byte level without per-item first-seen timestamps. Matches the
 * global-variant behaviour in PR #16.
 */
data class NotifiableCategoryDelta(val newItems: Int, val reclaimableBytes: Long)
