package com.miaclean.app.data.classify

import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem

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
class MediaClassifier {

    fun classify(item: MediaItem): MediaCategory {
        if (isDocument(item)) return MediaCategory.Document
        if (item.mimeType.isImage().not() && item.mimeType.isVideo().not()) return MediaCategory.Other
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
        if (!path.contains("whatsapp images")) return false
        if (item.sizeBytes > MEME_MAX_BYTES) return false

        val looksLikeForward = WHATSAPP_FORWARD_NAME.matches(name)
        val tinyLikelyForward = item.sizeBytes <= MEME_TINY_MAX_BYTES &&
            WHATSAPP_CAMERA_NAME.matches(name).not()
        return looksLikeForward || tinyLikelyForward
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

    private fun isDocument(item: MediaItem): Boolean {
        val mime = item.mimeType.lowercase()
        val path = item.relativePath.lowercase()
        val name = item.displayName.lowercase()

        if (mime.startsWith("application/pdf")) return true
        if (mime.startsWith("text/")) return true
        if (mime in DOCUMENT_EXACT_MIME_TYPES) return true
        if (DOCUMENT_MIME_PREFIXES.any { prefix -> mime.startsWith(prefix) }) return true

        if (DOCUMENT_PATH_HINTS.any { hint -> path.contains(hint) }) return true
        return DOCUMENT_EXTENSIONS.any { ext -> name.endsWith(ext) }
    }

    private fun String.isImage(): Boolean = startsWith("image/")
    private fun String.isVideo(): Boolean = startsWith("video/")

    companion object {
        private const val MEME_MAX_BYTES = 500 * 1024L // 500 KB
        private const val MEME_TINY_MAX_BYTES = 250 * 1024L // 250 KB

        private val SCREENSHOT_PATH_HINTS = listOf(
            "/screenshots/",
            "/screenshot/",
            "/screenrecordings/",
            "pictures/screenshots",
            "dcim/screenshots",
        )

        private val SCREENSHOT_NAME_PREFIXES = listOf(
            "screenshot",
            "screen_",
            "screen-",
            "screencap",
            "captura",
            "captura de tela",
        )

        private val SELFIE_NAME_HINTS = listOf(
            "selfie",
            "_front",
            "-front",
            "_selfie",
            "frontcam",
            "front_camera",
            "frontal",
        )

        private val DOCUMENT_PATH_HINTS = listOf(
            "/documents/",
            "/documentos/",
            "whatsapp documents",
        )

        private val DOCUMENT_EXTENSIONS = listOf(
            ".pdf",
            ".doc",
            ".docx",
            ".ppt",
            ".pptx",
            ".xls",
            ".xlsx",
            ".txt",
            ".rtf",
            ".odt",
            ".csv",
        )

        private val DOCUMENT_EXACT_MIME_TYPES = setOf(
            "application/pdf",
            "application/msword",
            "application/rtf",
            "application/epub+zip",
        )

        private val DOCUMENT_MIME_PREFIXES = listOf(
            "text/",
            "application/vnd.openxmlformats-officedocument",
            "application/vnd.ms-",
            "application/vnd.oasis.opendocument",
        )

        private val WHATSAPP_FORWARD_NAME = Regex("^img-\\d{8}-wa\\d+\\.(jpg|jpeg|png|webp)$")
        private val WHATSAPP_CAMERA_NAME = Regex("^img_\\d{8}_\\d{6}\\.(jpg|jpeg|png|webp)$")
    }
}
