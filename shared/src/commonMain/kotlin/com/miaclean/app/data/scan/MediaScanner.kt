package com.miaclean.app.data.scan

import com.miaclean.app.domain.MediaItem

/** Abstraction for scanning the platform's media library. */
interface MediaScanner {
    /** Returns all available media items on the device. */
    suspend fun scanAll(): List<MediaItem>
}
