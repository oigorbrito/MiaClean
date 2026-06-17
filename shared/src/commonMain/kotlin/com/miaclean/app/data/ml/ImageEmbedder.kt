package com.miaclean.app.data.ml

/** Abstraction for generating vector embeddings of images. */
interface ImageEmbedder {
    /** Returns a floating-point embedding for the given image URI, or null when unavailable. */
    suspend fun embed(uri: String): FloatArray?

    /** Cosine similarity between two L2-normalized embeddings; 1.0 means identical. */
    fun cosine(left: FloatArray, right: FloatArray): Float
}
