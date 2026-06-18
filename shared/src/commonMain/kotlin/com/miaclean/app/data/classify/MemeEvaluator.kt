package com.miaclean.app.data.classify

/**
 * Decides whether a set of [MemeSignals] should reclassify a
 * [com.miaclean.app.domain.MediaCategory.Photo] to [com.miaclean.app.domain.MediaCategory.Meme].
 *
 * Pure function, no Android dependencies - unit-testable without Robolectric.
 *
 * Decision rules (any one triggers):
 *  1. [MemeSignals.textCoverageRatio] >= 10% AND at least [MemeSignals.MIN_CHARS_FOR_COVERAGE]
 *     characters. Classic image-macro memes sit at 10-25% coverage; single-sign photos stay
 *     under 5%.
 *  2. Both top and bottom bands carry text AND total chars >= [MemeSignals.MIN_CHARS_FOR_TOP_BOTTOM].
 *     This is the "top/bottom impact font" template that most memes still follow - useful for
 *     memes that compress to lower coverage (short captions) but retain the bi-banded layout.
 *
 * Screenshots are filtered upstream by [MediaClassifier] on path/filename hints, so the bi-banded
 * rule does not misclassify chat screenshots as memes even though they share the layout.
 */
object MemeEvaluator {
    fun isMeme(signals: MemeSignals): Boolean {
        if (signals.totalCharacterCount < MemeSignals.MIN_CHARS_FOR_COVERAGE) return false
        if (hasHighCoverage(signals)) return true
        if (hasTopBottomBandsWithLongCaption(signals)) return true
        return false
    }

    private fun hasHighCoverage(signals: MemeSignals): Boolean {
        return signals.textCoverageRatio >= MemeSignals.MIN_COVERAGE_RATIO
    }

    private fun hasTopBottomBandsWithLongCaption(signals: MemeSignals): Boolean {
        if (!signals.topBandHasText || !signals.bottomBandHasText) return false
        return signals.totalCharacterCount >= MemeSignals.MIN_CHARS_FOR_TOP_BOTTOM
    }
}
