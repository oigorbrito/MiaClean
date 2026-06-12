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

/**
 * Runs after [MediaClassifier] + [SelfieDetector] and promotes `Photo` candidates to `Meme` when
 * ML Kit Text Recognition agrees that the image carries caption-style text. Deliberately
 * conservative because the observable UI surface (category chip) is informational — false
 * positives are cosmetic, but false *negatives* on actual memes are the whole point of this
 * detector. Balance is tuned in [MemeEvaluator].
 *
 * Guards:
 *  - Files over [MAX_DECODE_BYTES] are left as `Photo` — decoding a 20 MB HEIF just to guess at a
 *    category isn't worth it. Memes are ~50-500 KB in practice.
 *  - Bitmap is downscaled to [RECOGNIZER_TARGET_PX] on the long edge before handing to ML Kit.
 *    640px preserves caption glyph detail while keeping the recognizer latency under ~150ms on
 *    a mid-tier device.
 *  - If Play Services hasn't downloaded the recognizer model yet (unbundled library) the detect
 *    call fails and we return `false` so the file stays `Photo`. The next scan retries.
 *  - Recognizer is held behind a [Lazy] so closing the detector without ever scanning avoids
 *    triggering Play Services module initialization just to tear it down.
 */
@Singleton
class MemeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: ClassifierEventLogger,
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

    /**
     * Returns true when the image at [uri] should be reclassified as a meme. Suspend because the
     * ML Kit API is Task-based; callers are expected to already be off the main thread (the scan
     * pipeline runs on [kotlinx.coroutines.Dispatchers.IO]).
     */
    suspend fun isMeme(
        uri: Uri,
        sizeBytes: Long,
        mediaId: Long = 0L,
        onError: (ErrorCategory) -> Unit = {},
    ): Boolean {
        val startTime = System.currentTimeMillis()
        logger.logStart("MemeDetector", mediaId)

        if (sizeBytes > MAX_DECODE_BYTES) {
            logger.logSuccess("MemeDetector", mediaId, "Photo (Too large)", System.currentTimeMillis() - startTime)
            return false
        }

        val bitmap = try {
            decodeDownscaled(uri)
        } catch (e: Exception) {
            logger.logFailure("MemeDetector", mediaId, ErrorCategory.IMAGE_INVALID, "Decoding failed: ${e.message}", System.currentTimeMillis() - startTime)
            onError(ErrorCategory.IMAGE_INVALID)
            null
        } ?: return false

        val signals = try {
            runRecognition(bitmap)
        } catch (e: Exception) {
            val category = if (e is com.google.mlkit.common.MlKitException &&
                e.errorCode == com.google.mlkit.common.MlKitException.NETWORK_ISSUE) {
                ErrorCategory.NETWORK_ERROR
            } else {
                ErrorCategory.UNEXPECTED
            }
            logger.logFailure("MemeDetector", mediaId, category, "Recognition failed: ${e.message}", System.currentTimeMillis() - startTime)
            onError(category)
            null
        } finally {
            bitmap.recycle()
        }

        val result = signals != null && MemeEvaluator.isMeme(signals)
        if (signals != null) {
            logger.logSuccess("MemeDetector", mediaId, if (result) "Meme" else "Photo", System.currentTimeMillis() - startTime)
        }
        return result
    }

    private suspend fun runRecognition(bitmap: Bitmap): MemeSignals? {
        val recognizer = recognizer ?: return null
        val image = InputImage.fromBitmap(bitmap, /* rotationDegrees = */ 0)
        val result = recognizer.process(image).await()

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
        try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: Exception) {
            throw e
        }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / sample > RECOGNIZER_TARGET_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override fun close() {
        if (recognizerLazy.isInitialized()) recognizerLazy.value?.close()
    }

    private companion object {
        /** Skip files larger than this — memes virtually never exceed a few hundred KB. */
        const val MAX_DECODE_BYTES = 3L * 1024 * 1024 // 3 MB

        /** Long-edge pixel cap before handing to the recognizer. 640px is enough for caption
         *  glyph detail without blowing the detection budget. */
        const val RECOGNIZER_TARGET_PX = 640

        /** Top 35% of the image is considered the "top caption band". */
        const val TOP_BAND_END_RATIO = 0.35f

        /** Bottom 35% of the image is considered the "bottom caption band". */
        const val BOTTOM_BAND_START_RATIO = 0.65f
    }
}
