package com.miaclean.app.data.hash
import com.miaclean.app.data.hash.Md5Hasher

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes exact-file MD5 digests. Matches the bytes of the underlying file without transformation,
 * so two JPEGs with identical bytes will always collide.
 */
@Singleton
class AndroidMd5Hasher @Inject constructor(
    @ApplicationContext private val context: Context,
) : Md5Hasher {
    override suspend fun hash(uri: String): String? {
        return context.contentResolver.openInputStream(Uri.parse(uri))?.use(::hashStream)
    }

    private fun hashStream(input: InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
