package com.tbtechs.focusflow.enforcement.customrules

enum class DisableDecision { ALLOW, DENIED_BLOCK_ACTIVE, NEED_PIN }

/**
 * Same decision the Defense tab's `protectedToggle` makes. Turning a rule ON is always allowed
 * and never asks this. Disabling, editing or deleting an enabled rule does.
 */
object RuleGuard {
    fun decide(blockActive: Boolean, pinProtectionEnabled: Boolean): DisableDecision = when {
        blockActive -> DisableDecision.DENIED_BLOCK_ACTIVE
        pinProtectionEnabled -> DisableDecision.NEED_PIN
        else -> DisableDecision.ALLOW
    }
}
