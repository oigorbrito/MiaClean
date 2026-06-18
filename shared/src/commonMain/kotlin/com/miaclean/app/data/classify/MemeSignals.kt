package com.miaclean.app.data.classify

/**
 * Raw signals extracted from a candidate image's text-recognition pass. Every field is optional
 * or zero-defaulted so [MemeEvaluator] can be exercised without depending on ML Kit at test time
 * and so the detector can short-circuit (skip decode / skip recognition) without producing junk.
 *
 * Geometry is normalized: coverage and half-bands are expressed as ratios of the decoded bitmap,
 * not absolute pixel counts - this way the evaluator thresholds don't have to know about the
 * downscaling that happens in the detector.
 */
data class MemeSignals(
    /** Total text bounding-box area divided by the image area, in `[0f, 1f]`. */
    val textCoverageRatio: Float,
    /** Total character count across all recognized text blocks (whitespace excluded). */
    val totalCharacterCount: Int,
    /** True when at least [MemeSignals.MIN_BAND_COVERAGE] of the top 35% of the image has text. */
    val topBandHasText: Boolean,
    /** True when at least [MemeSignals.MIN_BAND_COVERAGE] of the bottom 35% of the image has text. */
    val bottomBandHasText: Boolean,
) {
    companion object {
        /** Min textCoverageRatio to classify on coverage alone. Selected conservatively: ambient
         *  photos with a single sign/label usually stay below ~5%, classic memes carry 10-25%. */
        internal const val MIN_COVERAGE_RATIO = 0.10f

        /** Fraction of a top/bottom band that must contain text bounding boxes for the band to
         *  count as "has text" - 1.5% of the full image area per band is enough to distinguish
         *  caption strips from incidental signs. */
        internal const val MIN_BAND_COVERAGE = 0.015f

        /** Minimum total characters to trust any meme rule - guards against OCR noise on
         *  photos where the recognizer hallucinates 1-3 character blocks. */
        internal const val MIN_CHARS_FOR_COVERAGE = 15

        /** Extra chars required for the cheaper top+bottom rule, since that rule fires on less
         *  area than the pure-coverage rule and is more exposed to false positives. */
        internal const val MIN_CHARS_FOR_TOP_BOTTOM = 30
    }
}
