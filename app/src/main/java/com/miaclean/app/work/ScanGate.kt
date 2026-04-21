package com.miaclean.app.work

/**
 * Outcome of trying to dispatch a manual scan. Kept as an enum (not `sealed class`) because the
 * three branches are mutually exclusive and carry no payload — every call site can decide how to
 * surface each state (tile pulls up [com.miaclean.app.MainActivity], shortcut deep-links into
 * onboarding or Results, etc.).
 */
enum class ScanDispatchResult {
    /** User has completed onboarding AND holds at least one media permission. Work enqueued. */
    ReadyToEnqueue,

    /** Onboarding never finished: the launcher / tile should fall through to the welcome flow. */
    NeedsOnboarding,

    /** Onboarded previously but the user revoked media permission in system settings. */
    NeedsPermission,
}

/**
 * Pure decision function for "should the manual scan run right now?". Extracted from
 * [ScanDispatcher] so its two-gate logic can be unit-tested without Hilt / Robolectric /
 * DataStore — the decision layer is the only place behaviour changes when we add new
 * preconditions, so it's the only place that needs deep coverage.
 *
 * Order matters: onboarding is checked before permission because onboarding's permission request
 * is the entry point for granting media access. Flipping the order would mis-route a user who
 * hasn't completed onboarding (but coincidentally granted a permission via another app) into the
 * permission-revoked branch.
 */
object ScanGate {
    fun decide(onboardingComplete: Boolean, hasMediaPermission: Boolean): ScanDispatchResult =
        when {
            !onboardingComplete -> ScanDispatchResult.NeedsOnboarding
            !hasMediaPermission -> ScanDispatchResult.NeedsPermission
            else -> ScanDispatchResult.ReadyToEnqueue
        }
}
