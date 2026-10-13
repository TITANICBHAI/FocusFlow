package com.tbtechs.focusflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupImportIntentRelayTest {
    @Test
    fun consumeReturnsStagedValueOnlyOnce() {
        val relay = PendingValueRelay<String>()
        relay.stage("content://backup/one")

        assertEquals("content://backup/one", relay.consume())
        assertNull(relay.consume())
    }

    @Test
    fun latestStagedValueReplacesUnconsumedValue() {
        val relay = PendingValueRelay<String>()
        relay.stage("content://backup/one")
        relay.stage("content://backup/two")

        assertEquals("content://backup/two", relay.consume())
        assertNull(relay.consume())
    }
}
