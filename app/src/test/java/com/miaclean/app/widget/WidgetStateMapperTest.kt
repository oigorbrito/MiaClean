package com.miaclean.app.widget

import com.miaclean.app.domain.MediaCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exhaustive branch coverage for [WidgetStateMapper.map]. Kept deliberately focused on the
 * precedence rules documented in the mapper's KDoc — every test line-items one of those
 * transitions so a future reader can reason about why a given tuple renders a specific state
 * without re-deriving the logic.
 */
class WidgetStateMapperTest {

    @Test
    fun `onboarding pending outranks every other input`() {
        val state = WidgetStateMapper.map(
            summary = WidgetSummary(hasScanned = true, duplicateCount = 42, reclaimableBytes = 1_000_000),
            onboardingComplete = false,
            hasMediaPermission = true,
        )
        assertEquals(WidgetState.OnboardingPending, state)
    }

    @Test
    fun `needs permission when onboarded but permission revoked`() {
        val state = WidgetStateMapper.map(
            summary = WidgetSummary(hasScanned = true, duplicateCount = 3, reclaimableBytes = 500),
            onboardingComplete = true,
            hasMediaPermission = false,
        )
        assertEquals(WidgetState.NeedsPermission, state)
    }

    @Test
    fun `ready to scan when onboarded, permitted, and no scan has completed yet`() {
        val state = WidgetStateMapper.map(
            summary = WidgetSummary.Empty,
            onboardingComplete = true,
            hasMediaPermission = true,
        )
        assertEquals(WidgetState.ReadyToScan, state)
    }

    @Test
    fun `zombie count without hasScanned still routes to ready to scan`() {
        // Defensive: if DataStore gets into an inconsistent state where a count is persisted but
        // `hasScanned` is false, we prefer to prompt a fresh scan rather than render stale data.
        val state = WidgetStateMapper.map(
            summary = WidgetSummary(hasScanned = false, duplicateCount = 9, reclaimableBytes = 999),
            onboardingComplete = true,
            hasMediaPermission = true,
        )
        assertEquals(WidgetState.ReadyToScan, state)
    }

    @Test
    fun `no duplicates when scan ran but count is zero`() {
        val state = WidgetStateMapper.map(
            summary = WidgetSummary(hasScanned = true, duplicateCount = 0, reclaimableBytes = 0),
            onboardingComplete = true,
            hasMediaPermission = true,
        )
        assertEquals(WidgetState.NoDuplicates, state)
    }

    @Test
    fun `negative count coerces to no duplicates`() {
        // Belt-and-suspenders: the updater already clamps at zero, but the mapper should not
        // produce a `HasDuplicates(-1)` under any circumstance.
        val state = WidgetStateMapper.map(
            summary = WidgetSummary(hasScanned = true, duplicateCount = -1, reclaimableBytes = 0),
            onboardingComplete = true,
            hasMediaPermission = true,
        )
        assertEquals(WidgetState.NoDuplicates, state)
    }

    @Test
    fun `has duplicates surfaces count and bytes verbatim`() {
        val state = WidgetStateMapper.map(
            summary = WidgetSummary(hasScanned = true, duplicateCount = 7, reclaimableBytes = 12_345_678),
            onboardingComplete = true,
            hasMediaPermission = true,
        )
        assertEquals(WidgetState.HasDuplicates(count = 7, reclaimableBytes = 12_345_678), state)
    }

    @Test
    fun `has duplicates clamps negative reclaimable bytes to zero`() {
        val state = WidgetStateMapper.map(
            summary = WidgetSummary(hasScanned = true, duplicateCount = 2, reclaimableBytes = -100),
            onboardingComplete = true,
            hasMediaPermission = true,
        )
        assertEquals(WidgetState.HasDuplicates(count = 2, reclaimableBytes = 0L), state)
    }

    @Test
    fun `has duplicates propagates thumbnails and category counts`() {
        // The mapper must surface the enrichment from `WidgetSummary` so the 2x2 layout has
        // something to render. Zero-count entries are stripped here as a defence-in-depth even
        // though the updater already filters them — a downgrade or hand-edited prefs could
        // still inject one.
        val state = WidgetStateMapper.map(
            summary = WidgetSummary(
                hasScanned = true,
                duplicateCount = 5,
                reclaimableBytes = 1_000,
                thumbnailUris = listOf("u1", "u2", "u3"),
                categoryCounts = mapOf(
                    MediaCategory.Screenshot to 3,
                    MediaCategory.Selfie to 0,
                    MediaCategory.Meme to 2,
                ),
            ),
            onboardingComplete = true,
            hasMediaPermission = true,
        )
        assertEquals(
            WidgetState.HasDuplicates(
                count = 5,
                reclaimableBytes = 1_000,
                thumbnailUris = listOf("u1", "u2", "u3"),
                categoryCounts = mapOf(
                    MediaCategory.Screenshot to 3,
                    MediaCategory.Meme to 2,
                ),
            ),
            state,
        )
    }
}
