package com.miaclean.app.data.adapter
import com.miaclean.app.data.scan.MediaStoreScanner
import com.miaclean.app.data.scan.SafWhatsAppScanner
import com.miaclean.app.data.scan.MediaScanner
import com.miaclean.app.domain.MediaItem
import javax.inject.Inject
class AndroidMediaScanner @Inject constructor(
    private val mediaStoreScanner: MediaStoreScanner,
    private val safScanner: SafWhatsAppScanner,
) : MediaScanner {
    override fun scanAll(): List<MediaItem> = mediaStoreScanner.scanAll() + safScanner.scan(emptyList())
}
