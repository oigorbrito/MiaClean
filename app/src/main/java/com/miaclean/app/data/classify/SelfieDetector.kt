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

/**
 * Runs after [MediaClassifier] and promotes `Photo` candidates to `Selfie` when EXIF or a
 * MediaPipe face detection pass agrees. Kept behind a separate class because it touches the
 * [ContentResolver][android.content.ContentResolver] and MediaPipe — the metadata-only
 * [MediaClassifier] stays a pure function we can unit-test trivially.
 *
 * The detector is deliberately conservative:
 *  - EXIF is checked first (no bitmap decode). If EXIF alone is confident (short focal length or
 *    an OEM "front" token) we skip the bitmap path entirely.
 *  - Face detection only runs when EXIF was inconclusive AND the file is small enough to decode
 *    cheaply ([MAX_DECODE_BYTES]). Large files are left as `Photo` rather than burning battery.
 *  - The downscaled bitmap is capped at [FACE_DETECTOR_TARGET_PX] on the long edge. Short-range
 *    BlazeFace is happy with ~128px faces, so 320px gives us plenty of room without pulling full
 *    resolution into memory.
 *  - If the model asset is missing (CI without internet, first run before the download task) the
 *    detector returns `false` gracefully and we fall back to EXIF-only.
 */
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

    /**
     * Returns true when the image at [uri] should be reclassified as a selfie. Safe to call on
     * any thread — performs blocking I/O, so callers are expected to already be on
     * [kotlinx.coroutines.Dispatchers.IO] (the scan pipeline is).
     */
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
                        /* defaultValue = */ 0,
                    ).takeIf { it > 0 },
                    lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                    model = exif.getAttribute(ExifInterface.TAG_MODEL),
                    imageDescription = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
                    faceCount = 0,
                    largestFaceAreaRatio = 0f,
                )
            } ?: throw InvalidImageException("Could not open stream")
        } catch (e: ClassificationException) {
            throw e
        } catch (e: Exception) {
            throw InvalidImageException("Failed to read EXIF", e)
        }
    }

    private fun runFaceDetection(uri: Uri): FaceSignals? {
        val detector = faceDetector ?: throw ClassificationServiceUnavailableException("Face detector model not loaded")
        val bitmap = decodeDownscaled(uri) ?: throw InvalidImageException("Failed to decode image")
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
            throw UnexpectedClassificationException("Face detection failed", e)
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
