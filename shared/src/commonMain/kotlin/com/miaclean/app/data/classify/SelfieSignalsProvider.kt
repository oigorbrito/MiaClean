package com.miaclean.app.data.classify

import com.miaclean.app.domain.MediaItem

interface SelfieSignalsProvider {
    suspend fun provideSignals(item: MediaItem, onError: (ErrorCategory) -> Unit = {}): SelfieSignals?
}
