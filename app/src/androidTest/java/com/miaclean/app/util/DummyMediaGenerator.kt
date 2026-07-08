package com.miaclean.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A test harness utility to generate dummy media on the device's MediaStore.
 * This is used for automated integration testing of the scanning and classification pipelines.
 */
class DummyMediaGenerator(private val context: Context) {

    /**
     * Generates a dummy image with a solid color and text.
     * Useful for creating identical MD5 duplicates.
     */
    suspend fun generateExactDuplicateSet(prefix: String, count: Int, color: Int = Color.BLUE) = withContext(Dispatchers.IO) {
        val baseBitmap = createBitmapWithText("Exact", color)
        
        for (i in 1..count) {
            val name = "${prefix}_exact_$i.jpg"
            saveBitmapToMediaStore(baseBitmap, name)
        }
    }

    /**
     * Generates a set of perceptual duplicates.
     * These images are slightly different visually (e.g. different text or noise)
     * so their MD5 will differ, but their pHash should be very close.
     */
    suspend fun generatePerceptualDuplicateSet(prefix: String, count: Int, color: Int = Color.GREEN) = withContext(Dispatchers.IO) {
        for (i in 1..count) {
            val name = "${prefix}_perceptual_$i.jpg"
            // Adding dynamic text makes the pixel data differ slightly -> different MD5, similar pHash
            val bitmap = createBitmapWithText("Sim $i", color)
            saveBitmapToMediaStore(bitmap, name)
        }
    }

    /**
     * Cleans up all images created by the dummy generator.
     */
    suspend fun cleanUp(prefix: String) = withContext(Dispatchers.IO) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("${prefix}_%")
        context.contentResolver.delete(uri, selection, selectionArgs)
    }

    private fun createBitmapWithText(text: String, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        
        val paint = Paint().apply {
            this.color = Color.WHITE
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text, 128f, 128f, paint)
        return bitmap
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap, displayName: String) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val itemUri = context.contentResolver.insert(collection, values) ?: throw IOException("Failed to create new MediaStore record.")
        
        try {
            context.contentResolver.openOutputStream(itemUri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                    throw IOException("Failed to save bitmap.")
                }
            }
        } catch (e: Exception) {
            context.contentResolver.delete(itemUri, null, null)
            throw e
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(itemUri, values, null, null)
        }
    }
}
