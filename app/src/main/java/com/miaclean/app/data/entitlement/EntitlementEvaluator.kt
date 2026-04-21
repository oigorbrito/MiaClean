package com.miaclean.app.data.entitlement

/**
 * Pure, zero-Android-deps decision function for freemium delete gating. Kept separate from
 * [EntitlementRepository] so the "may I delete N items?" logic is trivially unit-testable
 * without Robolectric or DataStore fakes.
 */
object EntitlementEvaluator {

    /** Default free-tier budget. Paid users are [Entitlement.Pro] and never hit this cap. */
    const val FREE_DELETES_PER_MONTH: Int = 50

    /**
     * Decides what to do when the user asks to delete [requested] items and has already spent
     * [used] of their monthly budget. [Entitlement.Pro] always returns [Decision.Allow].
     *
     * The [Decision.PartialAllow] branch exists so a selection of 30 items with 25 already used
     * doesn't hard-block — we let the user delete the remaining 25 *and* surface the paywall so
     * they know why 5 were dropped.
     */
    fun decide(
        entitlement: Entitlement,
        requested: Int,
        used: Int,
        limit: Int = FREE_DELETES_PER_MONTH,
    ): Decision {
        require(requested >= 0) { "requested must be non-negative" }
        require(used >= 0) { "used must be non-negative" }
        require(limit > 0) { "limit must be positive" }
        if (entitlement == Entitlement.Pro || requested == 0) return Decision.Allow
        val remaining = (limit - used).coerceAtLeast(0)
        return when {
            remaining >= requested -> Decision.Allow
            remaining == 0 -> Decision.Blocked(used = used, limit = limit)
            else -> Decision.PartialAllow(
                allowed = remaining,
                denied = requested - remaining,
                used = used,
                limit = limit,
            )
        }
    }

    sealed interface Decision {
        /** Entire selection fits in the budget (or user is Pro). No paywall surfaced. */
        data object Allow : Decision

        /**
         * Only the first [allowed] items fit; the caller should trim the selection, proceed with
         * delete, and then surface a paywall explaining the [denied] drop.
         */
        data class PartialAllow(
            val allowed: Int,
            val denied: Int,
            val used: Int,
            val limit: Int,
        ) : Decision

        /** Budget exhausted. No delete should run; caller must open the paywall. */
        data class Blocked(val used: Int, val limit: Int) : Decision
    }
}

enum class Entitlement { Free, Pro }
