package com.miaclean.app.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedderResult
import com.miaclean.app.util.BitmapUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around MediaPipe's [ImageEmbedder] task.
 *
 * Ported from the official Google MediaPipe Android example:
 *   https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/image_embedder/android
 *
 * The model file (`mobilenet_v3_small_100_224_embedder.tflite` by default) must be placed under
 * `app/src/main/assets/` as `image_embedder.tflite`. If it is missing, calls to [embed] return
 * null so the rest of the pipeline can keep working without semantic grouping.
 */
@Singleton
class ImageEmbedderWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
) : Closeable {

    // Hold the Lazy delegate itself so [close] can check `isInitialized()` and skip triggering
    // MediaPipe's native initialization just to immediately tear it down. Without this, a caller
    // that closes the wrapper before any scan runs would load the TFLite model for nothing.
    // Mirrors the pattern in `SelfieDetector`.
    private val embedderLazy: Lazy<ImageEmbedder?> = lazy { tryCreateEmbedder() }
    private val embedder: ImageEmbedder? get() = embedderLazy.value

    private fun tryCreateEmbedder(): ImageEmbedder? {
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()
            val options = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setL2Normalize(true)
                .setQuantize(false)
                .build()
            ImageEmbedder.createFromOptions(context, options)
        } catch (_: Throwable) {
            null
        }
    }

    /** Returns a floating-point embedding for the given image URI, or null when unavailable. */
    fun embed(uri: Uri): FloatArray? {
        val e = embedder ?: return null
        val bitmap = BitmapUtils.decodeDownscaled(context, uri, EMBEDDER_TARGET_PX) ?: return null
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: ImageEmbedderResult = e.embed(mpImage)
            result.embeddingResult().embeddings().firstOrNull()?.toFloats()
        } finally {
            bitmap.recycle()
        }
    }

    /** Cosine similarity between two L2-normalized embeddings; 1.0 means identical. */
    fun cosine(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size) return 0f
        var dot = 0f
        for (i in left.indices) dot += left[i] * right[i]
        return dot
    }

    private fun Embedding.toFloats(): FloatArray = floatEmbedding()

    override fun close() {
        if (embedderLazy.isInitialized()) {
            embedderLazy.value?.close()
        }
    }

    private companion object {
        const val MODEL_ASSET_PATH = "image_embedder.tflite"
        const val EMBEDDER_TARGET_PX = 320
    }
}
