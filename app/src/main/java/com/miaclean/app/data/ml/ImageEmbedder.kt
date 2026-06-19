package com.miaclean.app.data.ml
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class ImageEmbedderWrapper @Inject constructor(@ApplicationContext private val context: Context) : Closeable {
    private val embedderLazy = lazy { try {
        val options = ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("image_embedder.tflite").build())
            .setRunningMode(RunningMode.IMAGE).setL2Normalize(true).setQuantize(false).build()
        ImageEmbedder.createFromOptions(context, options)
    } catch (_: Throwable) { null } }
    private val embedder: ImageEmbedder? get() = embedderLazy.value
    fun embed(uri: Uri): FloatArray? {
        val e = embedder ?: return null
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            e.embed(mpImage).embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
        } finally { bitmap.recycle() }
    }
    fun cosine(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size) return 0f
        var dot = 0f; for (i in left.indices) dot += left[i] * right[i]; return dot
    }
    override fun close() { if (embedderLazy.isInitialized()) embedderLazy.value?.close() }
}
