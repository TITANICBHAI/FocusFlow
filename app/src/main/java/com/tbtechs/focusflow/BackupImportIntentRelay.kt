package com.tbtechs.focusflow

import android.net.Uri

/**
 * Holds one external backup URI until the in-app settings flow is ready to read it.
 */
object BackupImportIntentRelay {
    private val pendingUri = PendingValueRelay<Uri>()

    fun stage(uri: Uri) {
        pendingUri.stage(uri)
    }

    fun consume(): Uri? = pendingUri.consume()
}

internal class PendingValueRelay<T : Any> {
    private val lock = Any()
    private var pendingValue: T? = null

    fun stage(value: T) {
        synchronized(lock) {
            pendingValue = value
        }
    }

    fun consume(): T? = synchronized(lock) {
        pendingValue.also { pendingValue = null }
    }
}
