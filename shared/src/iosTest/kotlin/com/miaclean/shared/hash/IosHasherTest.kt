package com.miaclean.shared.hash

import platform.Foundation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.cinterop.*

class IosHasherTest {

    @Test
    fun testIosHasherMatchesMd5Vectors() {
        val fileManager = NSFileManager.defaultManager
        val tempDirectory = NSTemporaryDirectory()
        
        for ((inputBytes, expectedMd5) in TestMediaHelper.MD5_VECTORS) {
            val fileName = "test_media_${expectedMd5}.bin"
            val filePath = tempDirectory + fileName
            val fileUrl = NSURL.fileURLWithPath(filePath)
            
            // Write input bytes to temporary file URL
            val nsData = if (inputBytes.isEmpty()) {
                NSData.data()
            } else {
                inputBytes.usePinned { pinned ->
                    NSData.dataWithBytes(pinned.addressOf(0), inputBytes.size.toULong())
                }
            }
            fileManager.createFileAtPath(filePath, contents = nsData, attributes = null)
            
            val mediaSource = IosMediaSource(fileUrl)
            val hasher = IosHasher()
            val result = hasher.hash(mediaSource)
            
            // Cleanup
            fileManager.removeItemAtURL(fileUrl, error = null)
            
            assertNotNull(result)
            assertEquals(expectedMd5, result)
        }
    }

    @Test
    fun testIosHasherReturnsNullForInvalidSource() {
        val hasher = IosHasher()
        val dummySource = object : MediaSource {
            override val identifier: String = "dummy"
        }
        assertNull(hasher.hash(dummySource))
    }
}
