package com.miaclean.app.domain

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
}

/** Progress emitted by the scan pipeline. */
sealed interface ScanProgress {
    data object Idle : ScanProgress
    data class Running(val processed: Int, val total: Int) : ScanProgress
    data class Done(val duplicates: Int, val groups: Int) : ScanProgress
    data class Failed(val reason: String) : ScanProgress
}
