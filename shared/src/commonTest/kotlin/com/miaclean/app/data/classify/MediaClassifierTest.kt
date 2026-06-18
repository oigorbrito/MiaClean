package com.miaclean.app.data.classify

import com.miaclean.app.domain.MediaCategory
import com.miaclean.app.domain.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaClassifierTest {

    private val classifier = MediaClassifier()

    @Test
    fun testClassification() {
        val item = MediaItem(
            id = 1L,
            uri = "content://media/test.jpg",
            displayName = "Screenshot_2024.png",
            mimeType = "image/png",
            sizeBytes = 1000,
            dateTakenMs = 0L,
            relativePath = "Pictures/Screenshots/",
            isFromWhatsApp = false
        )
        assertEquals(MediaCategory.Screenshot, classifier.classify(item))
    }
}
