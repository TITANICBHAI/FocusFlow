package com.tbtechs.focusflow.enforcement.customrules

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleGuardTest {
    @Test
    fun activeBlockDeniesEvenWithoutPin() {
        assertEquals(DisableDecision.DENIED_BLOCK_ACTIVE, RuleGuard.decide(blockActive = true, pinProtectionEnabled = false))
        assertEquals(DisableDecision.DENIED_BLOCK_ACTIVE, RuleGuard.decide(blockActive = true, pinProtectionEnabled = true))
    }

    @Test
    fun pinProtectionAsksForThePinWhenNoBlockIsRunning() {
        assertEquals(DisableDecision.NEED_PIN, RuleGuard.decide(blockActive = false, pinProtectionEnabled = true))
    }

    @Test
    fun otherwiseAllowed() {
        assertEquals(DisableDecision.ALLOW, RuleGuard.decide(blockActive = false, pinProtectionEnabled = false))
    }
}
