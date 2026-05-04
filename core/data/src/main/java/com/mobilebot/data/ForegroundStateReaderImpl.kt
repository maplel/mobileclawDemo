package com.mobilebot.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mobilebot.domain.tools.ForegroundStateReader
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks process foreground using [ProcessLifecycleOwner] (any activity of this app is visible).
 */
@Singleton
class ForegroundStateReaderImpl
    @Inject
    constructor() : ForegroundStateReader {
        private val interactive = AtomicBoolean(false)
        private var trackingStarted = false

        private val observer =
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    interactive.set(true)
                }

                override fun onStop(owner: LifecycleOwner) {
                    interactive.set(false)
                }
            }

        fun startTracking() {
            synchronized(this) {
                if (trackingStarted) return
                trackingStarted = true
                val lifecycle = ProcessLifecycleOwner.get().lifecycle
                lifecycle.addObserver(observer)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    interactive.set(true)
                }
            }
        }

        override fun isInteractiveForeground(): Boolean = interactive.get()
    }
