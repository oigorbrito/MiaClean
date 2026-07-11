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
        // For unit tests we don't need an actual file; return a unique content Uri.
        return Uri.parse("content://dummy/${System.currentTimeMillis()}")
    }
}
