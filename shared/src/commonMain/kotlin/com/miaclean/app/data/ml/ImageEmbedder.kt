package com.miaclean.app.data.ml
interface ImageEmbedder {
    fun embed(uri: String): FloatArray?
    fun cosine(left: FloatArray, right: FloatArray): Float
}
