package com.miaclean.app.data.adapter
import android.net.Uri
import com.miaclean.app.data.hash.Md5Hasher
import javax.inject.Inject
class AndroidMd5Hasher @Inject constructor(private val delegate: com.miaclean.app.data.hash.Md5Hasher) : Md5Hasher {
    override fun hash(uri: String): String? = delegate.hash(Uri.parse(uri))
}
