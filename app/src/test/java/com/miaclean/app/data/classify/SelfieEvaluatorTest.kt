package com.miaclean.app.data.classify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfieEvaluatorTest {

    @Test
    fun `empty signals are not a selfie`() {
        assertFalse(SelfieEvaluator.isSelfie(empty()))
    }

    @Test
    fun `short 35mm focal length flags selfie`() {
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = 24)))
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = 28)))
    }

    @Test
    fun `long focal length is not a selfie`() {
        assertFalse(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = 35)))
        assertFalse(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = 70)))
    }

    @Test
    fun `zero or negative focal length is ignored`() {
        // `ExifInterface.getAttributeInt` returns the default for missing tags; we filter them
        // out in the reader but guard the evaluator too for safety.
        assertFalse(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = 0)))
        assertFalse(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = -5)))
    }

    @Test
    fun `front-camera token in lens model flags selfie`() {
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(lensModel = "Front Camera")))
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(lensModel = "SELFIE cam")))
    }

    @Test
    fun `front-camera token in model flags selfie`() {
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(model = "SM-G998 (Frontal)")))
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(model = "Pixel 8 Front Cam")))
    }

    @Test
    fun `front-camera token in image description flags selfie`() {
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(imageDescription = "Selfie with friends")))
    }

    @Test
    fun `rear camera tokens are ignored`() {
        val rear = empty().copy(
            lensModel = "Wide",
            model = "Pixel 8",
            imageDescription = "Sunset at the beach",
        )
        assertFalse(SelfieEvaluator.isSelfie(rear))
    }

    @Test
    fun `one dominant face flags selfie`() {
        assertTrue(
            SelfieEvaluator.isSelfie(
                empty().copy(faceCount = 1, largestFaceAreaRatio = 0.25f),
            ),
        )
    }

    @Test
    fun `small faces do not flag selfie`() {
        assertFalse(
            SelfieEvaluator.isSelfie(
                empty().copy(faceCount = 1, largestFaceAreaRatio = 0.05f),
            ),
        )
    }

    @Test
    fun `crowd with small faces is not a selfie`() {
        assertFalse(
            SelfieEvaluator.isSelfie(
                empty().copy(faceCount = 6, largestFaceAreaRatio = 0.03f),
            ),
        )
    }

    @Test
    fun `large dominant face even with many others flags selfie`() {
        assertTrue(
            SelfieEvaluator.isSelfie(
                empty().copy(faceCount = 3, largestFaceAreaRatio = 0.30f),
            ),
        )
    }

    @Test
    fun `threshold face area is inclusive`() {
        assertTrue(
            SelfieEvaluator.isSelfie(
                empty().copy(
                    faceCount = 1,
                    largestFaceAreaRatio = SelfieSignals.MIN_SELFIE_FACE_AREA_RATIO,
                ),
            ),
        )
    }

    private fun empty() = SelfieSignals(
        focalLength35mm = null,
        lensModel = null,
        model = null,
        imageDescription = null,
        faceCount = 0,
        largestFaceAreaRatio = 0f,
    )
}
