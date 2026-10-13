package com.tbtechs.focusflow.ui

import com.tbtechs.focusflow.data.repository.NetworkBlockingChangeRejectedException

internal object NetworkBlockSettingsUpdatePolicy {
    suspend fun persistToggleOrRestoreStoredValue(
        requestedValue: Boolean,
        persist: suspend (Boolean) -> Unit,
        readStoredValue: () -> Boolean,
        reportRejection: (NetworkBlockingChangeRejectedException) -> Unit,
    ): Boolean =
        try {
            persist(requestedValue)
            requestedValue
        } catch (exception: NetworkBlockingChangeRejectedException) {
            reportRejection(exception)
            readStoredValue()
        }
}
