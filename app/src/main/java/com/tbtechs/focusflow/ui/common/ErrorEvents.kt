package com.tbtechs.focusflow.ui.common

import com.tbtechs.focusflow.data.repository.StartupLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class AppErrorEvent(
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)

/**
 * Lightweight process-local error stream used by the Compose root overlays.
 * It intentionally carries only the latest user-safe message and tag.
 */
object AppErrorEvents {
    private val _events = MutableSharedFlow<AppErrorEvent>(
        extraBufferCapacity = 32,
    )
    private val history = mutableListOf<AppErrorEvent>()
    val events = _events.asSharedFlow()

    fun report(tag: String, message: String, throwable: Throwable? = null) {
        StartupLogger.error(tag, message, throwable)
    }

    internal fun reportFromStartupLogger(tag: String, message: String) {
        val event = AppErrorEvent(tag, message)
        synchronized(history) {
            history += event
            if (history.size > 100) history.removeAt(0)
        }
        _events.tryEmit(event)
    }

    fun snapshot(): List<AppErrorEvent> = synchronized(history) { history.toList() }
}