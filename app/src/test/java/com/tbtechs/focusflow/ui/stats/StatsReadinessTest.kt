package com.tbtechs.focusflow.ui.stats

import com.tbtechs.focusflow.ui.permissions.PermissionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsReadinessTest {
    @Test
    fun nothingMissingMeansNothingToShow() {
        assertTrue(readinessItems(emptySet(), nudgesOn = true).isEmpty())
    }

    @Test
    fun coreProtectionPermissionsAreListedInOrder() {
        val items = readinessItems(
            setOf(PermissionId.OVERLAY, PermissionId.ACCESSIBILITY, PermissionId.USAGE),
            nudgesOn = false,
        )
        assertEquals(
            listOf(PermissionId.USAGE, PermissionId.ACCESSIBILITY, PermissionId.OVERLAY),
            items.map { it.id },
        )
    }

    @Test
    fun notificationsOnlyMatterOnceHeadsUpsAreOn() {
        val missing = setOf(PermissionId.NOTIFICATIONS)
        assertTrue(readinessItems(missing, nudgesOn = false).isEmpty())
        assertEquals(listOf(PermissionId.NOTIFICATIONS), readinessItems(missing, nudgesOn = true).map { it.id })
    }

    @Test
    fun minutesAreWrittenCompactly() {
        assertEquals("45m", minutesLabel(45.0))
        assertEquals("2h", minutesLabel(120.0))
        assertEquals("1h 20m", minutesLabel(80.4))
    }
}
