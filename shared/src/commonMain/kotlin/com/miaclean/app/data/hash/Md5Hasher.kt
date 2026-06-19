package com.miaclean.app.data.hash
interface Md5Hasher {
    fun hash(uri: String): String?
}
