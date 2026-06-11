package com.miaclean.app.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedderResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageEmbedderWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
) : Closeable {

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

    fun embed(uri: Uri): FloatArray? {
        val e = embedder ?: throw com.miaclean.app.data.classify.ClassificationServiceUnavailableException("Image embedder model not loaded")
        val bitmap = decode(uri) ?: throw com.miaclean.app.data.classify.InvalidImageException("Failed to decode image $uri")
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: ImageEmbedderResult = e.embed(mpImage)
            result.embeddingResult().embeddings().firstOrNull()?.toFloats()
                ?: throw com.miaclean.app.data.classify.EmptyClassificationResponseException("Empty embedding result for $uri")
        } catch (e: com.miaclean.app.data.classify.ClassificationException) {
            throw e
        } catch (e: Exception) {
            throw com.miaclean.app.data.classify.UnexpectedClassificationException("Embedding failed for $uri", e)
        } finally {
            bitmap.recycle()
        }
    }

    fun cosine(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size) return 0f
        var dot = 0f
        for (i in left.indices) dot += left[i] * right[i]
        return dot
    }

    private fun decode(uri: Uri): Bitmap? = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    }

    private fun Embedding.toFloats(): FloatArray = floatEmbedding()

    override fun close() {
        if (embedderLazy.isInitialized()) {
            embedderLazy.value?.close()
        }
    }

    private companion object {
        const val MODEL_ASSET_PATH = "image_embedder.tflite"
    }
}
