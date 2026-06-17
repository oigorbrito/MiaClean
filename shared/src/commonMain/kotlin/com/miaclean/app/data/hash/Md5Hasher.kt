package com.miaclean.app.data.hash

/** Abstraction for calculating MD5 hash of a media item. */
interface Md5Hasher {
    /** [uri] is a platform-specific identifier (URI on Android, local path or identifier on iOS). */
    suspend fun hash(uri: String): String?
}
