package com.miaclean.app.data.classify

/**
 * Raw signals extracted from a candidate image: EXIF metadata (cheap, no bitmap decode) plus
 * optional MediaPipe Face Detector output. Both halves are optional — [SelfieEvaluator] deals
 * with partial or absent data so we can short-circuit without running the detector when EXIF
 * alone is confident.
 */
data class SelfieSignals(
    /** 35mm-equivalent focal length in millimetres; front-facing lenses are typically ≤ 28mm. */
    val focalLength35mm: Int?,
    /** `TAG_LENS_MODEL` — sometimes contains "front"/"frontal" on Samsung/Xiaomi. */
    val lensModel: String?,
    /** `TAG_MODEL` — some OEMs suffix with "(front)" or "front cam". */
    val model: String?,
    /** `TAG_IMAGE_DESCRIPTION` — occasionally contains a front-camera hint. */
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

/**
 * Decides whether a set of [SelfieSignals] should reclassify a [com.miaclean.app.domain.MediaCategory.Photo]
 * to [com.miaclean.app.domain.MediaCategory.Selfie].
 *
 * Pure function with no Android dependencies so it can be unit-tested without Robolectric.
 *
 * Decision rules (any one triggers):
 *  1. The 35mm-equivalent focal length is non-null and ≤ 28mm. Selfie cameras sit in this range;
 *     rear main lenses are typically 24–35mm but almost always carry EXIF with values > 28mm on
 *     Android devices that write the tag, so the false-positive risk is acceptable.
 *  2. Any of `lensModel`, `model`, `imageDescription` contains a front-camera token. This covers
 *     OEMs that annotate the capturing lens (Samsung's "Front Camera", Xiaomi, etc.). `TAG_MAKE`
 *     is deliberately excluded — it names the manufacturer ("Samsung", "Google") and should not
 *     carry a lens-orientation hint.
 *  3. Face Detector returned ≥ 1 face AND the largest face covers ≥ 15% of the image. Group
 *     photos and crowds have many small faces; a dominant single face framed close is the strong
 *     selfie signal.
 */
object SelfieEvaluator {
    fun isSelfie(signals: SelfieSignals): Boolean {
        if (hasShortFocalLength(signals)) return true
        if (hasFrontCameraToken(signals)) return true
        if (hasDominantFace(signals)) return true
        return false
    }

    private fun hasShortFocalLength(signals: SelfieSignals): Boolean {
        val focal = signals.focalLength35mm ?: return false
        return focal in 1..SelfieSignals.MAX_FRONT_FOCAL_35MM
    }

    private fun hasFrontCameraToken(signals: SelfieSignals): Boolean {
        val haystacks = listOf(
            signals.lensModel,
            signals.model,
            signals.imageDescription,
        )
        return haystacks.any { field -> containsFrontToken(field) }
    }

    private fun containsFrontToken(field: String?): Boolean {
        if (field.isNullOrBlank()) return false
        val lower = field.lowercase()
        return SelfieSignals.FRONT_TOKENS.any { token -> lower.contains(token) }
    }

    private fun hasDominantFace(signals: SelfieSignals): Boolean {
        if (signals.faceCount <= 0) return false
        return signals.largestFaceAreaRatio >= SelfieSignals.MIN_SELFIE_FACE_AREA_RATIO
    }
}
