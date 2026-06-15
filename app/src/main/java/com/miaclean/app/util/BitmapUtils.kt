package com.miaclean.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Common utilities for memory-efficient bitmap operations.
 */
object BitmapUtils {

    /**
     * Decodes a bitmap from [uri], downscaling it so its long edge does not exceed [targetLongEdge].
     * Uses [BitmapFactory.Options.inSampleSize] for efficient decoding that minimizes memory peaks.
     *
     * @return The downscaled bitmap, or null if decoding fails.
     */
    fun decodeDownscaled(context: Context, uri: Uri, targetLongEdge: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            return null
        }

        val outWidth = options.outWidth
        val outHeight = options.outHeight
        if (outWidth <= 0 || outHeight <= 0) return null

        val longEdge = maxOf(outWidth, outHeight)
        var sampleSize = 1
        while (longEdge / sampleSize > targetLongEdge) {
            sampleSize *= 2
        }

        return try {
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            null
        }
    }
}
