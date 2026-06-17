package com.miaclean.app.data.hash

/** Abstraction for calculating perceptual hash of an image. */
interface PerceptualHasher {
    /** [uri] is a platform-specific identifier. */
    suspend fun hash(uri: String): String?

    /** Returns `true` when [left] and [right] are similar enough to be considered duplicates. */
    fun isSimilar(left: String, right: String, threshold: Int = 5): Boolean
}
