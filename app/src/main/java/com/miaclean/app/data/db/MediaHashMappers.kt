package com.miaclean.app.data.db

import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem

fun MediaItem.toEntity(
    md5: String,
    pHash: String?,
    embeddingHash: String?,
    category: MediaCategory,
): MediaHashEntity = MediaHashEntity(
    mediaId = id,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    dateTakenMs = dateTakenMs,
    relativePath = relativePath,
    isWhatsApp = isFromWhatsApp,
    md5 = md5,
    pHash = pHash,
    embeddingHash = embeddingHash,
    lastScannedMs = System.currentTimeMillis(),
    category = category.name,
)

fun MediaHashEntity.toMediaItem(): MediaItem = MediaItem(
    id = mediaId,
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    dateTakenMs = dateTakenMs,
    relativePath = relativePath,
    isFromWhatsApp = isWhatsApp,
    category = runCatching { MediaCategory.valueOf(category) }.getOrDefault(MediaCategory.Other),
)
