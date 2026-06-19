package com.miaclean.app.data.adapter
import android.net.Uri; import com.miaclean.app.data.hash.PerceptualHasher; import com.miaclean.app.data.hash.InternalPerceptualHasher; import javax.inject.Inject
class AndroidPerceptualHasher @Inject constructor(private val delegate: InternalPerceptualHasher) : PerceptualHasher {
    override fun hash(uri: String): String? = delegate.hash(Uri.parse(uri))
}
