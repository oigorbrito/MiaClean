package com.miaclean.app.data.hash
interface PerceptualHasher {
    fun hash(uri: String): String?
}
