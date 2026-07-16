package com.miaclean.app.data.classify

import com.miaclean.app.domain.MediaItem

interface MemeSignalsProvider {
    suspend fun provideSignals(item: MediaItem, onError: (ErrorCategory) -> Unit = {}): MemeSignals?
}
