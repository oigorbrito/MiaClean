package com.miaclean.app.data.hash

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.miaclean.app.util.BitmapUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.avicorp.phashcalc.pHashCalc
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps `ru.avicorp:phashcalc` to compute a compact perceptual hash string for an image.
 *
 * The upstream library expects a file path on disk. Because [MediaStore] gives us content URIs we
 * stream the bitmap through a small temporary file under the app cache.
 */
@Singleton
class PerceptualHasher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val phash by lazy { pHashCalc() }

    fun hash(uri: Uri): String? {
        val cacheDir = File(context.cacheDir, "phash").apply { mkdirs() }
        val tmp = File.createTempFile("phash_", ".jpg", cacheDir)
        return try {
            val bitmap = BitmapUtils.decodeDownscaled(context, uri, PHASH_TARGET_PX) ?: return null
            try {
                tmp.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            } finally {
                bitmap.recycle()
            }
            // Library computes the pair hash for (a, b); loading the same path twice yields the
            // hash once via getHashOne().
            if (!phash.loadSourceFile(tmp.absolutePath, tmp.absolutePath)) return null
            if (!phash.checkCondition()) return null
            phash.getHashOne()
        } catch (_: Throwable) {
            null
        } finally {
            tmp.delete()
        }
    }

    /** Returns `true` when [left] and [right] are similar enough to be considered duplicates. */
    fun isSimilar(left: String, right: String, threshold: Int = DEFAULT_THRESHOLD): Boolean {
        if (left.length != right.length) return false
        var distance = 0
        for (i in left.indices) {
            if (left[i] != right[i]) distance++
            if (distance > threshold) return false
        }
        return true
    }

    private companion object {
        const val JPEG_QUALITY = 85
        const val DEFAULT_THRESHOLD = 5 // bits
        const val PHASH_TARGET_PX = 320
    }
}
