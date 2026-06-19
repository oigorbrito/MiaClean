package com.miaclean.app.data.hash
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class InternalMd5Hasher @Inject constructor(@ApplicationContext private val context: Context) {
    fun hash(uri: Uri): String? = context.contentResolver.openInputStream(uri)?.use(::hashStream)
    private fun hashStream(input: InputStream): String {
        val digest = MessageDigest.getInstance("MD5"); val buffer = ByteArray(64 * 1024)
        while (true) { val read = input.read(buffer); if (read <= 0) break; digest.update(buffer, 0, read) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
