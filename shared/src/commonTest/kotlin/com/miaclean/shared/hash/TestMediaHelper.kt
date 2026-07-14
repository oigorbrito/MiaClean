package com.miaclean.shared.hash

import kotlin.random.Random

/**
 * Minimal helper that produces deterministic dummy byte streams for hasher unit tests.
 * Refactored to pure Kotlin for Kotlin Multiplatform commonTest.
 */
object TestMediaHelper {

    /** Creates a deterministic byte array of [size] using [seed] for reproducibility. */
    fun deterministicBytes(size: Int, seed: Int = 42): ByteArray =
        Random(seed).nextBytes(size)

    /** Creates a byte array filled with [value] repeated [count] times. */
    fun repeatedBytes(value: Byte, count: Int): ByteArray = ByteArray(count) { value }

    /**
     * Known MD5 test vectors (input → expected hex digest).
     */
    val MD5_VECTORS: List<Pair<ByteArray, String>> = listOf(
        "".encodeToByteArray() to "d41d8cd98f00b204e9800998ecf8427e",
        "hello world".encodeToByteArray() to "5eb63bbbe01eeed093cb22bb8f5acdc3",
        "abc".encodeToByteArray() to "900150983cd24fb0d6963f7d28e17f72"
    )
}
