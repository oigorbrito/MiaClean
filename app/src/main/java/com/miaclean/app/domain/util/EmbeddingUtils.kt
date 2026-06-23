package com.miaclean.app.domain.util

import java.util.Locale

object EmbeddingUtils {
    fun encode(values: FloatArray): String =
        values.joinToString(separator = ",") { "%.5f".format(Locale.US, it) }

    fun decode(serialized: String): FloatArray {
        val parts = serialized.split(',')
        return FloatArray(parts.size) { index -> parts[index].toFloatOrNull() ?: 0f }
    }

    /** Cosine similarity between two L2-normalized embeddings; 1.0 means identical. */
    fun cosine(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size) return 0f
        var dot = 0f
        for (i in left.indices) dot += left[i] * right[i]
        return dot
    }
}
