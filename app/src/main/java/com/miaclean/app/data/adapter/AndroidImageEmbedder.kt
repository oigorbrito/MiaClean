package com.miaclean.app.data.adapter
import android.net.Uri; import com.miaclean.app.data.ml.ImageEmbedder; import com.miaclean.app.data.ml.ImageEmbedderWrapper; import javax.inject.Inject
class AndroidImageEmbedder @Inject constructor(private val delegate: ImageEmbedderWrapper) : ImageEmbedder {
    override fun embed(uri: String): FloatArray? = delegate.embed(Uri.parse(uri))
    override fun cosine(left: FloatArray, right: FloatArray): Float = delegate.cosine(left, right)
}
