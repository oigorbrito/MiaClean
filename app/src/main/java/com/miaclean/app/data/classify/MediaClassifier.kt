package com.miaclean.app.data.classify

import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Heuristic classifier that maps a [MediaItem]'s metadata onto a coarse [MediaCategory].
 *
 * This is intentionally a cheap, metadata-only classifier — no bitmap decoding, no ML, no EXIF
 * parsing yet. It's accurate enough to power the "Screenshots" and "Memes" filters on the Results
 * screen, where false positives/negatives are cosmetic rather than destructive.
 *
 * Ordering matters: the first matching rule wins. Rules are stacked from most specific
 * (Screenshot) to most generic (Photo), so adding a new signal only requires inserting it at the
 * right rung.
 */
@Singleton
class MediaClassifier @Inject constructor() {

    fun classify(item: MediaItem): MediaCategory {
        if (item.mimeType.isImage().not() && item.mimeType.isVideo().not()) {
            return MediaCategory.Other
        }
        if (item.mimeType.isVideo()) return MediaCategory.Video

        val path = item.relativePath.lowercase()
        val name = item.displayName.lowercase()

        if (isScreenshot(path, name)) return MediaCategory.Screenshot
        if (isMeme(item, path, name)) return MediaCategory.Meme
        if (isSelfie(path, name)) return MediaCategory.Selfie
        return MediaCategory.Photo
    }

    private fun isScreenshot(path: String, name: String): Boolean {
        if (SCREENSHOT_PATH_HINTS.any { hint -> path.contains(hint) }) return true
        return SCREENSHOT_NAME_PREFIXES.any { prefix -> name.startsWith(prefix) }
    }

    /**
     * WhatsApp images received from contacts are usually re-encoded small JPEGs (<500 KB) with
     * names that follow the `IMG-YYYYMMDD-WAXXXX.jpg` pattern. Sent items land in `Sent/` and
     * aren't counted as memes here — users rarely send memes from their own camera.
     */
    private fun isMeme(item: MediaItem, path: String, name: String): Boolean {
        if (!item.isFromWhatsApp) return false
        if (path.contains("/sent/")) return false
        if (item.sizeBytes > MEME_MAX_BYTES) return false
        return name.startsWith("img-") || path.contains("whatsapp images")
    }

    /**
     * Without EXIF/face detection we can only recognise the handful of vendor filenames that
     * mark a shot as front-facing. This list is short and will misclassify — worth revisiting
     * once we wire a lightweight EXIF read or the MediaPipe Face Detector from the scan pipeline.
     */
    private fun isSelfie(path: String, name: String): Boolean {
        if (!path.contains("dcim/") && !path.contains("pictures/")) return false
        return SELFIE_NAME_HINTS.any { hint -> name.contains(hint) }
    }

    private fun String.isImage(): Boolean = startsWith("image/")
    private fun String.isVideo(): Boolean = startsWith("video/")

    companion object {
        private const val MEME_MAX_BYTES = 500 * 1024L // 500 KB

        private val SCREENSHOT_PATH_HINTS = listOf(
            "/screenshots/",
            "/screenshot/",
            "pictures/screenshots",
            "dcim/screenshots",
        )

        private val SCREENSHOT_NAME_PREFIXES = listOf(
            "screenshot",
            "screen_",
            "screen-",
            "captura",
        )

        private val SELFIE_NAME_HINTS = listOf(
            "selfie",
            "_front",
            "-front",
            "_selfie",
        )
    }
}
