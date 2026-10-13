package com.tbtechs.focusflow.ui

import com.tbtechs.focusflow.data.repository.NetworkBlockingChangeRejectedException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkBlockSettingsUpdatePolicyTest {
    @Test
    fun successfulEnablePersistsAndPublishesTheRequestedValue() = runBlocking {
        var persisted: Boolean? = null
        var reported: IllegalStateException? = null
        val result = NetworkBlockSettingsUpdatePolicy.persistToggleOrRestoreStoredValue(
            requestedValue = true,
            persist = { persisted = it },
            readStoredValue = { false },
            reportRejection = { reported = it },
        )

        assertEquals(true, persisted)
        assertEquals(true, result)
        assertNull(reported)
    }

    @Test
    fun rejectedDisableReportsTheErrorAndReturnsTheStoredSwitchValue() = runBlocking {
        val rejection = NetworkBlockingChangeRejectedException()
        var reported: NetworkBlockingChangeRejectedException? = null
        var storedValueRead = false
        val result = NetworkBlockSettingsUpdatePolicy.persistToggleOrRestoreStoredValue(
            requestedValue = false,
            persist = { throw rejection },
            readStoredValue = {
                storedValueRead = true
                true
            },
            reportRejection = { reported = it },
        )

        assertEquals(true, storedValueRead)
        assertEquals(rejection, reported)
        assertEquals(true, result)
    }
}
