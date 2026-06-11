package com.miaclean.app.data.classify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelfieDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) : Closeable {

    private val faceDetectorLazy: Lazy<FaceDetector?> = lazy { tryCreateFaceDetector() }
    private val faceDetector: FaceDetector? get() = faceDetectorLazy.value

    private fun tryCreateFaceDetector(): FaceDetector? {
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()
            val options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .build()
            FaceDetector.createFromOptions(context, options)
        } catch (_: Throwable) {
            null
        }
    }

    fun isSelfie(uri: Uri, sizeBytes: Long): Boolean {
        val exif = readExifSignals(uri)
        if (SelfieEvaluator.isSelfie(exif)) return true
        if (sizeBytes > MAX_DECODE_BYTES) return false
        val faceSignals = runFaceDetection(uri) ?: return false
        val combined = exif.copy(
            faceCount = faceSignals.faceCount,
            largestFaceAreaRatio = faceSignals.largestFaceAreaRatio,
        )
        return SelfieEvaluator.isSelfie(combined)
    }

    private fun readExifSignals(uri: Uri): SelfieSignals {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                SelfieSignals(
                    focalLength35mm = exif.getAttributeInt(
                        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                        0,
                    ).takeIf { it > 0 },
                    lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                    model = exif.getAttribute(ExifInterface.TAG_MODEL),
                    imageDescription = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
                    faceCount = 0,
                    largestFaceAreaRatio = 0f,
                )
            } ?: throw InvalidImageException("Could not open stream for $uri")
        } catch (e: ClassificationException) {
            throw e
        } catch (e: Exception) {
            throw InvalidImageException("Failed to read EXIF for $uri", e)
        }
    }

    private fun runFaceDetection(uri: Uri): FaceSignals? {
        val detector = faceDetector ?: throw ClassificationServiceUnavailableException("Face detector model not loaded")
        val bitmap = decodeDownscaled(uri) ?: throw InvalidImageException("Failed to decode image $uri")
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: FaceDetectorResult = detector.detect(mpImage)
            val detections = result.detections()
            if (detections.isEmpty()) return FaceSignals(faceCount = 0, largestFaceAreaRatio = 0f)
            val imageArea = (bitmap.width * bitmap.height).toFloat().coerceAtLeast(1f)
            val largestArea = detections.maxOf { d ->
                val box = d.boundingBox()
                (box.width() * box.height()).toFloat().coerceAtLeast(0f)
            }
            FaceSignals(
                faceCount = detections.size,
                largestFaceAreaRatio = (largestArea / imageArea).coerceIn(0f, 1f),
            )
        } catch (e: Exception) {
            throw UnexpectedClassificationException("Face detection failed for $uri", e)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeDownscaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / sample > FACE_DETECTOR_TARGET_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    override fun close() {
        if (faceDetectorLazy.isInitialized()) faceDetectorLazy.value?.close()
    }

    private data class FaceSignals(val faceCount: Int, val largestFaceAreaRatio: Float)

    private fun emptySignals() = SelfieSignals(
        focalLength35mm = null,
        lensModel = null,
        model = null,
        imageDescription = null,
        faceCount = 0,
        largestFaceAreaRatio = 0f,
    )

    private companion object {
        const val MODEL_ASSET_PATH = "face_detector.tflite"
        const val MIN_DETECTION_CONFIDENCE = 0.5f
        const val FACE_DETECTOR_TARGET_PX = 320
        const val MAX_DECODE_BYTES = 3L * 1024 * 1024 // 3 MB
    }
}
