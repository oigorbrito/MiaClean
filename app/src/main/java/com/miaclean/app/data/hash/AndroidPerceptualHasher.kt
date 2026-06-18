package com.miaclean.app.data.hash
import com.miaclean.app.data.hash.PerceptualHasher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
class AndroidPerceptualHasher @Inject constructor(
    @ApplicationContext private val context: Context,
) : PerceptualHasher {
    private val phash by lazy { pHashCalc() }

    override suspend fun hash(uri: String): String? {
        val contentUri = Uri.parse(uri)
        val cacheDir = File(context.cacheDir, "phash").apply { mkdirs() }
        val tmp = File.createTempFile("phash_", ".jpg", cacheDir)
        return try {
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return null
                tmp.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                bitmap.recycle()
            } ?: return null
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
    override fun isSimilar(left: String, right: String, threshold: Int): Boolean {
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
    }
}
