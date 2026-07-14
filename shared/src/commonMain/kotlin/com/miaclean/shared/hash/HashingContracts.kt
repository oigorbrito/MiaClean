package com.miaclean.shared.hash

/**
 * Platform-neutral representation of a media source that can be hashed.
 */
interface MediaSource {
    val identifier: String
}

/**
 * Platform-neutral contract for computing exact-file or perceptual digests.
 */
interface Hasher {
    fun hash(source: MediaSource): String?
}
