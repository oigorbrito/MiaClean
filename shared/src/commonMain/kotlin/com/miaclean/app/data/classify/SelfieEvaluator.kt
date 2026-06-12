package com.miaclean.app.data.classify

/**
 * Decides whether a set of [SelfieSignals] should reclassify a [com.miaclean.app.domain.MediaCategory.Photo]
 * to [com.miaclean.app.domain.MediaCategory.Selfie].
 *
 * Pure function with no Android dependencies so it can be unit-tested without Robolectric.
 *
 * Decision rules (any one triggers):
 *  1. The 35mm-equivalent focal length is non-null and <= 28mm. Selfie cameras sit in this range;
 *     rear main lenses are typically 24-35mm but almost always carry EXIF with values > 28mm on
 *     Android devices that write the tag, so the false-positive risk is acceptable.
 *  2. Any of `lensModel`, `model`, `imageDescription` contains a front-camera token. This covers
 *     OEMs that annotate the capturing lens (Samsung's "Front Camera", Xiaomi, etc.). `TAG_MAKE`
 *     is deliberately excluded - it names the manufacturer ("Samsung", "Google") and should not
 *     carry a lens-orientation hint.
 *  3. Face Detector returned >= 1 face AND the largest face covers >= 15% of the image. Group
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
