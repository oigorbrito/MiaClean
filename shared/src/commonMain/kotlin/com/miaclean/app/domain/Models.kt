package com.miaclean.app.domain
enum class MediaCategory { Screenshot, Selfie, Meme, Document, Photo, Video, Other }
data class MediaItem(val id: Long, val uri: String, val displayName: String, val mimeType: String, val sizeBytes: Long, val dateTakenMs: Long, val relativePath: String, val isFromWhatsApp: Boolean, val category: MediaCategory = MediaCategory.Other)
data class MediaHash(val md5: String, val pHash: String?, val embeddingHash: String?, val item: MediaItem)
enum class ScanErrorCode { PERMISSION_REVOKED, MEDIA_UNAVAILABLE, UNEXPECTED }
sealed interface ScanProgress {
    data object Idle : ScanProgress
    data class Running(val processed: Int, val total: Int) : ScanProgress
    data class Done(val duplicates: Int, val groups: Int, val errorCode: ScanErrorCode? = null) : ScanProgress
    data class Failed(val errorCode: ScanErrorCode) : ScanProgress
}
data class DuplicateGroup(val groupId: Long, val strategy: Strategy, val items: List<MediaItem>, val totalBytes: Long) {
    enum class Strategy { EXACT_MD5, PERCEPTUAL_PHASH, SEMANTIC_EMBED }
    val dominantCategory: MediaCategory get() = items.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: MediaCategory.Other
}
