package com.miaclean.app.data.classify

import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaClassifierTest {

    private val classifier = MediaClassifier()

    @Test
    fun `video mime maps to Video`() {
        val item = item(mime = "video/mp4", name = "VID_20240101.mp4", path = "DCIM/Camera/")
        assertEquals(MediaCategory.Video, classifier.classify(item))
    }

    @Test
    fun `unknown mime maps to Other`() {
        val item = item(mime = "application/octet-stream", name = "blob.bin", path = "Download/")
        assertEquals(MediaCategory.Other, classifier.classify(item))
    }

    @Test
    fun `screenshot by relative path`() {
        val item = item(
            mime = "image/png",
            name = "Screenshot_20240101_120000.png",
            path = "Pictures/Screenshots/",
        )
        assertEquals(MediaCategory.Screenshot, classifier.classify(item))
    }

    @Test
    fun `screenshot by localized name prefix`() {
        val item = item(
            mime = "image/png",
            name = "Captura-de-tela-2024-01-01.png",
            path = "Pictures/",
        )
        assertEquals(MediaCategory.Screenshot, classifier.classify(item))
    }

    @Test
    fun `whatsapp received image under size cap is Meme`() {
        val item = item(
            mime = "image/jpeg",
            name = "IMG-20240101-WA0001.jpg",
            path = "WhatsApp/Media/WhatsApp Images/",
            whatsApp = true,
            size = 120 * 1024,
        )
        assertEquals(MediaCategory.Meme, classifier.classify(item))
    }

    @Test
    fun `whatsapp sent image is not classified as Meme`() {
        val item = item(
            mime = "image/jpeg",
            name = "IMG-20240101-WA0001.jpg",
            path = "WhatsApp/Media/WhatsApp Images/Sent/",
            whatsApp = true,
            size = 120 * 1024,
        )
        assertEquals(MediaCategory.Photo, classifier.classify(item))
    }

    @Test
    fun `whatsapp image above size cap falls through to Photo`() {
        val item = item(
            mime = "image/jpeg",
            name = "IMG-20240101-WA0001.jpg",
            path = "WhatsApp/Media/WhatsApp Images/",
            whatsApp = true,
            size = 4L * 1024 * 1024,
        )
        assertEquals(MediaCategory.Photo, classifier.classify(item))
    }

    @Test
    fun `selfie hint in filename under DCIM is Selfie`() {
        val item = item(
            mime = "image/jpeg",
            name = "IMG_20240101_selfie.jpg",
            path = "DCIM/Camera/",
        )
        assertEquals(MediaCategory.Selfie, classifier.classify(item))
    }

    @Test
    fun `selfie hint outside Pictures or DCIM is Photo`() {
        val item = item(
            mime = "image/jpeg",
            name = "selfie.jpg",
            path = "Download/",
        )
        assertEquals(MediaCategory.Photo, classifier.classify(item))
    }

    @Test
    fun `plain camera image is Photo`() {
        val item = item(
            mime = "image/jpeg",
            name = "IMG_20240101_120000.jpg",
            path = "DCIM/Camera/",
        )
        assertEquals(MediaCategory.Photo, classifier.classify(item))
    }

    @Test
    fun `screenshot wins over whatsapp meme heuristic`() {
        val item = item(
            mime = "image/png",
            name = "Screenshot_20240101.png",
            path = "WhatsApp/Media/WhatsApp Images/",
            whatsApp = true,
            size = 100 * 1024,
        )
        assertEquals(MediaCategory.Screenshot, classifier.classify(item))
    }

    private fun item(
        mime: String,
        name: String,
        path: String,
        whatsApp: Boolean = false,
        size: Long = 1_000_000,
    ) = MediaItem(
        id = 1L,
        uri = "content://media/$name",
        displayName = name,
        mimeType = mime,
        sizeBytes = size,
        dateTakenMs = 0L,
        relativePath = path,
        isFromWhatsApp = whatsApp,
    )
}
