package com.tbtechs.focusflow.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnSelfHealStaleWriteTest {
    @Test
    fun unchangedCachedValueDoesNotOverwriteTheNewerStoredValue() {
        assertEquals(
            true,
            VpnSelfHealPolicy.valueToPersist(
                loadedValue = false,
                requestedValue = false,
                storedValue = true,
            ),
        )
    }

    @Test
    fun explicitUserChangesWinOverTheStoredValue() {
        assertEquals(
            false,
            VpnSelfHealPolicy.valueToPersist(
                loadedValue = true,
                requestedValue = false,
                storedValue = true,
            ),
        )
        assertEquals(
            true,
            VpnSelfHealPolicy.valueToPersist(
                loadedValue = false,
                requestedValue = true,
                storedValue = false,
            ),
        )
    }

    @Test
    fun consistentStateIsPreserved() {
        for (value in listOf(true, false)) {
            assertEquals(
                value,
                VpnSelfHealPolicy.valueToPersist(
                    loadedValue = value,
                    requestedValue = value,
                    storedValue = value,
                ),
            )
        }
    }
}
