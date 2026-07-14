package com.miaclean.app.data.hash

import android.net.Uri

import com.miaclean.shared.hash.MediaSource

data class AndroidMediaSource(val uri: Uri) : MediaSource {
    override val identifier: String = uri.toString()
}
