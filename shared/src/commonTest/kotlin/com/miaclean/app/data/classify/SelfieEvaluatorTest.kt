package com.miaclean.app.data.classify

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfieEvaluatorTest {

    @Test
    fun empty_signals_are_not_a_selfie() {
        assertFalse(SelfieEvaluator.isSelfie(empty()))
    }

    @Test
    fun short_35mm_focal_length_flags_selfie() {
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = 24)))
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = 28)))
    }

    @Test
    fun long_focal_length_is_not_a_selfie() {
        assertFalse(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = 35)))
        assertFalse(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = 70)))
    }

    @Test
    fun zero_or_negative_focal_length_is_ignored() {
        // `ExifInterface.getAttributeInt` returns the default for missing tags; we filter them
        // out in the reader but guard the evaluator too for safety.
        assertFalse(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = 0)))
        assertFalse(SelfieEvaluator.isSelfie(empty().copy(focalLength35mm = -5)))
    }

    @Test
    fun front_camera_token_in_lens_model_flags_selfie() {
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(lensModel = "Front Camera")))
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(lensModel = "SELFIE cam")))
    }

    @Test
    fun front_camera_token_in_model_flags_selfie() {
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(model = "SM-G998 (Frontal)")))
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(model = "Pixel 8 Front Cam")))
    }

    @Test
    fun front_camera_token_in_image_description_flags_selfie() {
        assertTrue(SelfieEvaluator.isSelfie(empty().copy(imageDescription = "Selfie with friends")))
    }

    @Test
    fun rear_camera_tokens_are_ignored() {
        val rear = empty().copy(
            lensModel = "Wide",
            model = "Pixel 8",
            imageDescription = "Sunset at the beach",
        )
        assertFalse(SelfieEvaluator.isSelfie(rear))
    }

    @Test
    fun one_dominant_face_flags_selfie() {
        assertTrue(
            SelfieEvaluator.isSelfie(
                empty().copy(faceCount = 1, largestFaceAreaRatio = 0.25f),
            ),
        )
    }

    @Test
    fun small_faces_do not_flag_selfie() {
        assertFalse(
            SelfieEvaluator.isSelfie(
                empty().copy(faceCount = 1, largestFaceAreaRatio = 0.05f),
            ),
        )
    }

    @Test
    fun crowd_with_small_faces_is_not_a_selfie() {
        assertFalse(
            SelfieEvaluator.isSelfie(
                empty().copy(faceCount = 6, largestFaceAreaRatio = 0.03f),
            ),
        )
    }

    @Test
    fun large_dominant_face_even_with_many_others_flags_selfie() {
        assertTrue(
            SelfieEvaluator.isSelfie(
                empty().copy(faceCount = 3, largestFaceAreaRatio = 0.30f),
            ),
        )
    }

    @Test
    fun threshold_face_area_is_inclusive() {
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
