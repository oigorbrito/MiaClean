package com.miaclean.app.data.classify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemeEvaluatorTest {

    @Test
    fun `empty signals are not a meme`() {
        assertFalse(MemeEvaluator.isMeme(empty()))
    }

    @Test
    fun `high coverage with enough characters flags meme`() {
        assertTrue(
            MemeEvaluator.isMeme(
                empty().copy(textCoverageRatio = 0.12f, totalCharacterCount = 40),
            ),
        )
    }

    @Test
    fun `high coverage but short caption is not a meme`() {
        // Guards against the recognizer latching onto a 2-char logo and reporting high coverage
        // because the bounding box around "OK" happens to be fat.
        assertFalse(
            MemeEvaluator.isMeme(
                empty().copy(textCoverageRatio = 0.15f, totalCharacterCount = 5),
            ),
        )
    }

    @Test
    fun `coverage right at the threshold flags meme`() {
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
    fun `low coverage with no band banners is not a meme`() {
        // Single storefront sign in a photo: moderate chars, low coverage, text only in mid-frame.
        assertFalse(
            MemeEvaluator.isMeme(
                empty().copy(textCoverageRatio = 0.04f, totalCharacterCount = 20),
            ),
        )
    }

    @Test
    fun `top and bottom bands with long caption flags meme`() {
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
    fun `top band only is not a meme`() {
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
    fun `bottom band only is not a meme`() {
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
    fun `both bands but short caption is not a meme`() {
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
    fun `high coverage short caption still not meme even with both bands`() {
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
    fun `top-bottom rule floor is higher than coverage rule floor`() {
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
