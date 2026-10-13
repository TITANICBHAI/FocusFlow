package com.tbtechs.focusflow.enforcement.health

import com.tbtechs.focusflow.ui.permissions.PermissionId

data class EnforcementHealth(
    val missingRequiredPermissions: List<PermissionId>,
    val isKnown: Boolean = true,
) {
    val needsAttention: Boolean
        get() = isKnown && missingRequiredPermissions.isNotEmpty()

    companion object {
        val UNKNOWN = EnforcementHealth(emptyList(), isKnown = false)

        fun fromRequiredPermissions(
            required: List<PermissionId>,
            granted: Set<PermissionId>,
        ) = EnforcementHealth(required.filterNot { it in granted })
    }
}

class AccessibilityStateChangeTracker {
    private var lastValue: Boolean? = null

    /** Returns true only after a previously observed value changes. */
    fun observe(value: Boolean): Boolean {
        val changed = lastValue != null && lastValue != value
        lastValue = value
        return changed
    }
}
