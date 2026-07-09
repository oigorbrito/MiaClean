package com.miaclean.app.data.hash

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.random.Random

/**
 * Minimal helper that produces deterministic dummy byte streams for hasher unit tests.
 * No Android framework dependency — works in pure JVM tests.
 */
object TestMediaHelper {

    /** Creates a deterministic byte array of [size] using [seed] for reproducibility. */
    fun deterministicBytes(size: Int, seed: Int = 42): ByteArray =
        Random(seed).nextBytes(size)

    /** Wraps [data] in a fresh [InputStream]. */
    fun streamOf(data: ByteArray): InputStream = ByteArrayInputStream(data)

    /** Creates a byte array filled with [value] repeated [count] times. */
    fun repeatedBytes(value: Byte, count: Int): ByteArray = ByteArray(count) { value }

    /**
     * Known MD5 test vectors (input → expected hex digest).
     * Useful for regression-proofing [Md5Hasher].
     */
    val MD5_VECTORS: List<Pair<ByteArray, String>> = listOf(
        "".toByteArray()          to "d41d8cd98f00b204e9800998ecf8427e",
        "hello world".toByteArray() to "5eb63bbbe01eeed093cb22bb8f5acdc3",
        "abc".toByteArray()       to "900150983cd24fb0d6963f7d28e17f72",
        "MiaClean".toByteArray()  to java.security.MessageDigest.getInstance("MD5")
            .digest("MiaClean".toByteArray())
            .joinToString("") { "%02x".format(it) },
    )
}
