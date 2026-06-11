package com.miaclean.app.data.classify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class MemeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) : Closeable {

    private val recognizerLazy: Lazy<TextRecognizer?> = lazy { tryCreateRecognizer() }
    private val recognizer: TextRecognizer? get() = recognizerLazy.value

    private fun tryCreateRecognizer(): TextRecognizer? {
        return try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun isMeme(uri: Uri, sizeBytes: Long): Boolean {
        if (sizeBytes > MAX_DECODE_BYTES) return false
        val bitmap = decodeDownscaled(uri) ?: throw InvalidImageException("Failed to decode image $uri")
        val signals = try {
            runRecognition(bitmap)
        } catch (e: ClassificationException) {
            throw e
        } catch (e: Exception) {
            throw UnexpectedClassificationException("Meme detection failed for $uri", e)
        } finally {
            bitmap.recycle()
        }
        return signals != null && MemeEvaluator.isMeme(signals)
    }

    private suspend fun runRecognition(bitmap: Bitmap): MemeSignals? {
        val recognizer = recognizer ?: throw ClassificationServiceUnavailableException("Text recognizer model not loaded")
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = try {
            recognizer.process(image).await()
        } catch (e: Exception) {
            throw ClassificationNetworkException("Text recognition model unavailable", e)
        }

        val imageWidth = bitmap.width
        val imageHeight = bitmap.height
        if (imageWidth <= 0 || imageHeight <= 0) return null
        val imageArea = (imageWidth.toFloat() * imageHeight.toFloat()).coerceAtLeast(1f)

        val topBandEnd = (imageHeight * TOP_BAND_END_RATIO).toInt()
        val bottomBandStart = (imageHeight * BOTTOM_BAND_START_RATIO).toInt()

        var totalTextArea = 0f
        var topBandArea = 0f
        var bottomBandArea = 0f
        var totalChars = 0

        for (block in result.textBlocks) {
            val box = block.boundingBox ?: continue
            val area = (box.width().toFloat() * box.height().toFloat()).coerceAtLeast(0f)
            totalTextArea += area
            topBandArea += intersectArea(box, 0, topBandEnd)
            bottomBandArea += intersectArea(box, bottomBandStart, imageHeight)
            totalChars += block.text.count { !it.isWhitespace() }
        }

        return MemeSignals(
            textCoverageRatio = (totalTextArea / imageArea).coerceIn(0f, 1f),
            totalCharacterCount = totalChars,
            topBandHasText = (topBandArea / imageArea) >= MemeSignals.MIN_BAND_COVERAGE,
            bottomBandHasText = (bottomBandArea / imageArea) >= MemeSignals.MIN_BAND_COVERAGE,
        )
    }

    private fun intersectArea(box: Rect, bandTop: Int, bandBottom: Int): Float {
        val top = maxOf(box.top, bandTop)
        val bottom = minOf(box.bottom, bandBottom)
        if (bottom <= top) return 0f
        return (box.width().toFloat() * (bottom - top).toFloat()).coerceAtLeast(0f)
    }

    private fun decodeDownscaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / sample > RECOGNIZER_TARGET_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    override fun close() {
        if (recognizerLazy.isInitialized()) recognizerLazy.value?.close()
    }

    private companion object {
        const val MAX_DECODE_BYTES = 3L * 1024 * 1024
        const val RECOGNIZER_TARGET_PX = 640
        const val TOP_BAND_END_RATIO = 0.35f
        const val BOTTOM_BAND_START_RATIO = 0.65f
    }
}
