package com.miaclean.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Shared logic for memory-efficient bitmap decoding using inSampleSize.
 */
object BitmapUtils {

    /**
     * Decodes the image at [uri] downscaled to stay under [targetLongEdgePx].
     * Returns null if the URI cannot be opened or decoding fails.
     */
    fun decodeDownscaled(
        context: Context,
        uri: Uri,
        targetLongEdgePx: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        } catch (_: Exception) {
            return null
        }

        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null

        var sample = 1
        while (longEdge / sample > targetLongEdgePx) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }
}
