package com.miaclean.app.data.scan
import com.miaclean.app.domain.MediaItem
interface MediaScanner {
    fun scanAll(): List<MediaItem>
}
