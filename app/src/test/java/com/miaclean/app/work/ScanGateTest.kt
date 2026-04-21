package com.miaclean.app.work

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the manual-scan gate. Pure function → no Robolectric, no Hilt, no DataStore.
 * The matrix covers every (onboarded, hasPermission) combination because the decision is a
 * 2×2 lookup — cheaper to enumerate than argue by cases.
 */
class ScanGateTest {

    @Test
    fun `fresh install (not onboarded, no permission) needs onboarding`() {
        // Onboarding takes priority over permission: the onboarding flow is what asks for
        // permission in the first place, so routing here is the only way the user can progress.
        assertEquals(
            ScanDispatchResult.NeedsOnboarding,
            ScanGate.decide(onboardingComplete = false, hasMediaPermission = false),
        )
    }

    @Test
    fun `not onboarded but permission leaked through is still onboarding`() {
        // Another app on the device might hold READ_MEDIA_IMAGES, which our call sees as granted.
        // We still need the user to see the onboarding screen first (SAF picker, explanation).
        assertEquals(
            ScanDispatchResult.NeedsOnboarding,
            ScanGate.decide(onboardingComplete = true.not(), hasMediaPermission = true),
        )
    }

    @Test
    fun `onboarded but permission revoked routes through permission repair`() {
        // User finished onboarding at some point but then revoked the permission in system
        // settings. The tile can't silently do nothing — has to bounce back to the app so the
        // user gets a chance to re-grant.
        assertEquals(
            ScanDispatchResult.NeedsPermission,
            ScanGate.decide(onboardingComplete = true, hasMediaPermission = false),
        )
    }

    @Test
    fun `happy path enqueues`() {
        assertEquals(
            ScanDispatchResult.ReadyToEnqueue,
            ScanGate.decide(onboardingComplete = true, hasMediaPermission = true),
        )
    }
}
