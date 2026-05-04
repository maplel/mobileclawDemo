package com.mobilebot.data

import javax.inject.Inject
import javax.inject.Singleton

/** Attaches [ForegroundStateReaderImpl] to [androidx.lifecycle.ProcessLifecycleOwner] once at app start. */
@Singleton
class ProcessForegroundBinder
    @Inject
    constructor(
        private val foregroundStateReader: ForegroundStateReaderImpl,
    ) {
        fun attach() {
            foregroundStateReader.startTracking()
        }
    }
