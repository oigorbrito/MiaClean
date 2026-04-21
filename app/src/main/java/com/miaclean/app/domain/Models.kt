package com.miaclean.app.domain

/**
 * Coarse buckets used by the Results screen to help the user reason about what a duplicate group
 * is. The classification is a best-effort heuristic — treat it as a hint, not ground truth.
 */
enum class MediaCategory {
    /** Screenshots (system UI, browser captures). Usually the safest category to bulk-delete. */
    Screenshot,

    /** Frontal-camera photos. Detection is metadata-only today; may expand to face detection. */
    Selfie,

    /** Low-resolution/image messages that look like WhatsApp forwards — memes, jokes, images. */
    Meme,

    /** Any other image (camera rolls, downloads, etc.). */
    Photo,

    /** Video (any MIME starting with `video/`). */
    Video,

    /** Fallback when MIME is unknown or not image/video. */
    Other,
}

/** A media item discovered on the device. */
data class MediaItem(
    val id: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateTakenMs: Long,
    val relativePath: String,
    val isFromWhatsApp: Boolean,
    val category: MediaCategory = MediaCategory.Other,
)

/** A hash pair used for duplicate detection. */
data class MediaHash(
    val mediaId: Long,
    val md5: String,
    val pHash: String?,
    val embeddingHash: String?,
)

/** A group of duplicated media detected by MD5 or pHash similarity. */
data class DuplicateGroup(
    val groupId: Long,
    val strategy: Strategy,
    val items: List<MediaItem>,
    val totalBytes: Long,
) {
    enum class Strategy { EXACT_MD5, PERCEPTUAL_PHASH, SEMANTIC_EMBED }

    /** Category that best represents the group, used for the category chip + filter. */
    val dominantCategory: MediaCategory
        get() = items.groupingBy { it.category }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: MediaCategory.Other
}

/** Progress emitted by the scan pipeline. */
sealed interface ScanProgress {
    data object Idle : ScanProgress
    data class Running(val processed: Int, val total: Int) : ScanProgress
    data class Done(val duplicates: Int, val groups: Int) : ScanProgress
    data class Failed(val reason: String) : ScanProgress
}
