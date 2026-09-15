package com.tbtechs.focusflow.ui.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class AppErrorEvent(
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
)

/**
 * Lightweight process-local error stream used by the Compose root overlays.
 * It intentionally carries only the latest user-safe message and tag.
 */
object AppErrorEvents {
    private val _events = MutableSharedFlow<AppErrorEvent>(
        extraBufferCapacity = 32,
    )
    val events = _events.asSharedFlow()

    fun report(tag: String, message: String, throwable: Throwable? = null) {
        _events.tryEmit(AppErrorEvent(tag, message, throwable))
    }
}