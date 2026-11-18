package com.tbtechs.focusflow.enforcement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowanceCountPolicyTest {
    @Test
    fun theOnlyOpenOfOneIsUsable() {
        // Limit 1: the first open is counted before the check, so the count reads 1.
        assertTrue(AllowanceCountPolicy.isOpenUsable(1, 1, sessionAlreadyAdmitted = false))
    }

    @Test
    fun nMinusOneOpensThenTheNthStillWorks() {
        for (open in 1..3) {
            assertTrue("open $open of 3", AllowanceCountPolicy.isOpenUsable(open, 3, false))
        }
    }

    @Test
    fun theNextOpenAfterTheLimitIsBlocked() {
        assertFalse(AllowanceCountPolicy.isOpenUsable(2, 1, sessionAlreadyAdmitted = false))
        assertFalse(AllowanceCountPolicy.isOpenUsable(4, 3, sessionAlreadyAdmitted = false))
    }

    @Test
    fun aSessionAlreadyLetInStaysLetIn() {
        // Later window events from the same open must not turn it into a block.
        assertTrue(AllowanceCountPolicy.isOpenUsable(1, 1, sessionAlreadyAdmitted = true))
        assertTrue(AllowanceCountPolicy.isOpenUsable(3, 3, sessionAlreadyAdmitted = true))
    }
}
