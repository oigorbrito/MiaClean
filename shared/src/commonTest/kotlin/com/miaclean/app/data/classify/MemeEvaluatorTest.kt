package com.miaclean.app.data.classify

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemeEvaluatorTest {

    @Test
    fun empty_signals_are_not_a_meme() {
        assertFalse(MemeEvaluator.isMeme(empty()))
    }

    @Test
    fun high_coverage_with_enough_characters_flags_meme() {
        assertTrue(
            MemeEvaluator.isMeme(
                empty().copy(textCoverageRatio = 0.12f, totalCharacterCount = 40),
            ),
        )
    }

    @Test
    fun high_coverage_but_short_caption_is_not_a_meme() {
        // Guards against the recognizer latching onto a 2-char logo and reporting high coverage
        // because the bounding box around "OK" happens to be fat.
        assertFalse(
            MemeEvaluator.isMeme(
                empty().copy(textCoverageRatio = 0.15f, totalCharacterCount = 5),
            ),
        )
    }

    @Test
    fun coverage_right_at_the_threshold_flags_meme() {
        assertTrue(
            MemeEvaluator.isMeme(
                empty().copy(
                    textCoverageRatio = MemeSignals.MIN_COVERAGE_RATIO,
                    totalCharacterCount = MemeSignals.MIN_CHARS_FOR_COVERAGE,
                ),
            ),
        )
    }

    @Test
    fun low_coverage_with_no_band_banners_is_not_a_meme() {
        // Single storefront sign in a photo: moderate chars, low coverage, text only in mid-frame.
        assertFalse(
            MemeEvaluator.isMeme(
                empty().copy(textCoverageRatio = 0.04f, totalCharacterCount = 20),
            ),
        )
    }

    @Test
    fun top_and_bottom_bands_with_long_caption_flags_meme() {
        // Classic top-caption / bottom-caption meme with moderate coverage.
        assertTrue(
            MemeEvaluator.isMeme(
                empty().copy(
                    textCoverageRatio = 0.06f,
                    totalCharacterCount = 45,
                    topBandHasText = true,
                    bottomBandHasText = true,
                ),
            ),
        )
    }

    @Test
    fun top_band_only_is_not_a_meme() {
        // Page header / watermark / app banner — one band doesn't make a meme.
        assertFalse(
            MemeEvaluator.isMeme(
                empty().copy(
                    textCoverageRatio = 0.04f,
                    totalCharacterCount = 50,
                    topBandHasText = true,
                    bottomBandHasText = false,
                ),
            ),
        )
    }

    @Test
    fun bottom_band_only_is_not_a_meme() {
        // Caption under a photo (news, recipe, stock photo attribution) — still just one band.
        assertFalse(
            MemeEvaluator.isMeme(
                empty().copy(
                    textCoverageRatio = 0.04f,
                    totalCharacterCount = 50,
                    topBandHasText = false,
                    bottomBandHasText = true,
                ),
            ),
        )
    }

    @Test
    fun both_bands_but_short_caption_is_not_a_meme() {
        // Photo with a couple of incidental text blips in top and bottom (e.g. street signs on
        // both sides of a skyline). Character floor eliminates this.
        assertFalse(
            MemeEvaluator.isMeme(
                empty().copy(
                    textCoverageRatio = 0.03f,
                    totalCharacterCount = 15,
                    topBandHasText = true,
                    bottomBandHasText = true,
                ),
            ),
        )
    }

    @Test
    fun high_coverage_short_caption_still_not_meme_even_with_both_bands() {
        // Character floor wins over every other rule — prevents OCR noise on a busy photo from
        // triggering either path.
        assertFalse(
            MemeEvaluator.isMeme(
                empty().copy(
                    textCoverageRatio = 0.20f,
                    totalCharacterCount = 8,
                    topBandHasText = true,
                    bottomBandHasText = true,
                ),
            ),
        )
    }

    @Test
    fun top_bottom_rule_floor_is_higher_than_coverage_rule_floor() {
        // At coverage=8% (below the 10% coverage threshold), the top-bottom rule kicks in with
        // 30 chars but not with 25.
        val below = empty().copy(
            textCoverageRatio = 0.08f,
            totalCharacterCount = 25,
            topBandHasText = true,
            bottomBandHasText = true,
        )
        val atFloor = below.copy(totalCharacterCount = MemeSignals.MIN_CHARS_FOR_TOP_BOTTOM)
        assertFalse(MemeEvaluator.isMeme(below))
        assertTrue(MemeEvaluator.isMeme(atFloor))
    }

    private fun empty() = MemeSignals(
        textCoverageRatio = 0f,
        totalCharacterCount = 0,
        topBandHasText = false,
        bottomBandHasText = false,
    )
}
