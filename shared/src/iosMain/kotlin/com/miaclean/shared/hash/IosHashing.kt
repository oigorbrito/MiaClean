package com.miaclean.shared.hash

import kotlinx.cinterop.*
import platform.CoreCrypto.*
import platform.Foundation.*

/**
 * iOS implementation of [MediaSource] wrapping a native [NSURL].
 */
data class IosMediaSource(val url: NSURL) : MediaSource {
    override val identifier: String = url.absoluteString ?: ""
}

/**
 * iOS implementation of [Hasher] using Apple's [CoreCrypto] CC_MD5.
 */
class IosHasher : Hasher {
    override fun hash(source: MediaSource): String? {
        val iosSource = source as? IosMediaSource ?: return null
        
        // NSData Reading option that supports direct file URL resolution.
        val data = NSData.dataWithContentsOfURL(iosSource.url) ?: return null
        
        return memScoped {
            val digest = allocArray<UByteVar>(CC_MD5_DIGEST_LENGTH)
            
            // CC_MD5 takes: const void *data, CC_LONG len, unsigned char *md
            CC_MD5(data.bytes, data.length.toUInt(), digest)
            
            (0 until CC_MD5_DIGEST_LENGTH).joinToString("") { i ->
                digest[i].toString(16).padStart(2, '0')
            }
        }
    }
}
