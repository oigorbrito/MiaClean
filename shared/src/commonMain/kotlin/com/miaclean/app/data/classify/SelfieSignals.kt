package com.miaclean.app.data.classify

/**
 * Raw signals extracted from a candidate image: EXIF metadata (cheap, no bitmap decode) plus
 * optional MediaPipe Face Detector output. Both halves are optional - [SelfieEvaluator] deals
 * with partial or absent data so we can short-circuit without running the detector when EXIF
 * alone is confident.
 */
data class SelfieSignals(
    /** 35mm-equivalent focal length in millimetres; front-facing lenses are typically <= 28mm. */
    val focalLength35mm: Int?,
    /** `TAG_LENS_MODEL` - sometimes contains "front"/"frontal" on Samsung/Xiaomi. */
    val lensModel: String?,
    /** `TAG_MODEL` - some OEMs suffix with "(front)" or "front cam". */
    val model: String?,
    /** `TAG_IMAGE_DESCRIPTION` - occasionally contains a front-camera hint. */
    val imageDescription: String?,
    /** Number of faces the MediaPipe Face Detector returned. Zero when detector didn't run. */
    val faceCount: Int,
    /** Largest detected face area divided by image area; in `[0f, 1f]`. Zero when detector didn't run. */
    val largestFaceAreaRatio: Float,
) {
    companion object {
        /** Short-range (selfie) front-camera focal length budget in 35mm-equivalent mm. */
        internal const val MAX_FRONT_FOCAL_35MM = 28

        /** Minimum face-area ratio for a single face to look like a selfie (not a group photo). */
        internal const val MIN_SELFIE_FACE_AREA_RATIO = 0.15f

        internal val FRONT_TOKENS = listOf("front", "frontal", "selfie")
    }
}
