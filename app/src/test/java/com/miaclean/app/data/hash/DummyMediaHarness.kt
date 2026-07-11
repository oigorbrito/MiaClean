package com.miaclean.app.data.hash

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Minimal test harness that creates temporary media files from raw byte arrays and returns a [Uri]
 * usable by [Md5Hasher] and [PerceptualHasher] in unit tests.
 *
 * This helper lives in the test sources only and does not affect runtime code.
 */
object DummyMediaHarness {
    /**
     * Writes [data] to a temporary file under the given [context]'s cache directory and returns a
     * content [Uri] pointing to that file.
     *
     * The file is created with a unique name to avoid collisions between parallel tests.
     */
    fun uriFromBytes(context: Context, data: ByteArray, prefix: String = "dummy_media"): Uri {
        // Ensure the cache subdirectory exists
        val dir = File(context.cacheDir, "dummy_media").apply { mkdirs() }
        // Create a uniquely named temporary file
        val tmpFile = File.createTempFile("${prefix}_", ".bin", dir)
        tmpFile.writeBytes(data)
        // Return a file Uri; Android's content resolver can handle file:// URIs in tests
        return Uri.fromFile(tmpFile)
    }
}
