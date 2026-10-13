package com.tbtechs.focusflow.data.restore

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Serializes repository mutations. Nested writes in one coroutine reuse the
 * current permit so a repository operation can safely call another repository.
 */
class RestoreGate {
    private class WriterPermit(val gate: RestoreGate) :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<WriterPermit>
    }

    private val mutex = Mutex()

    @Suppress("UNUSED_PARAMETER")
    suspend fun <T> write(owner: String, block: suspend () -> T): T {
        if (currentCoroutineContext()[WriterPermit]?.gate === this) return block()

        mutex.lock()
        try {
            return withContext(WriterPermit(this)) { block() }
        } finally {
            mutex.unlock()
        }
    }
}
