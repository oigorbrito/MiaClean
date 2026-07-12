package com.miaclean.app.data.hash

import android.content.Context
import android.net.Uri
import java.io.File

object DummyMediaHarness {
    /**
     * Writes the provided byte array to a temporary file in [Context.cacheDir]
     * and returns a file Uri compatible with ContentResolver in instrumented tests.
     */
    fun uriFromBytes(context: Context, bytes: ByteArray): Uri {
        val cacheDir = context.cacheDir
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val tempFile = File(cacheDir, "dummy_${System.currentTimeMillis()}.bin")
        tempFile.writeBytes(bytes)
        return Uri.fromFile(tempFile)
    }

    /**
     * Overload that accepts a MIME type string for compatibility with existing tests.
     * The MIME type is currently unused because we rely on a plain file Uri.
     */
    fun uriFromBytes(context: Context, bytes: ByteArray, mimeType: String): Uri =
        uriFromBytes(context, bytes)
}
