package com.tbtechs.focusflow.enforcement

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NuclearUninstallPolicyTest {
    private val now = 1_000_000L

    @Test
    fun windowIsClosedWhenNothingWasRequested() {
        assertFalse(NuclearUninstallPolicy.isOpen(untilMs = 0L, nowMs = now))
    }

    @Test
    fun windowIsOpenUntilItsEnd() {
        assertTrue(NuclearUninstallPolicy.isOpen(now + 1, now))
        assertFalse(NuclearUninstallPolicy.isOpen(now, now))
        assertFalse(NuclearUninstallPolicy.isOpen(now - 1, now))
    }

    @Test
    fun aWindowFurtherAwayThanTheLimitIsIgnored() {
        // A wrong clock must not leave the guards open for hours.
        val tooFar = now + NuclearUninstallPolicy.WINDOW_MS + 1
        assertFalse(NuclearUninstallPolicy.isOpen(tooFar, now))
        assertTrue(NuclearUninstallPolicy.isOpen(now + NuclearUninstallPolicy.WINDOW_MS, now))
    }

    @Test
    fun theDialogOfAnotherAppIsLetThroughInsideTheWindow() {
        assertTrue(
            NuclearUninstallPolicy.allowsDialog(
                untilMs = now + 10_000, nowMs = now,
                dialogText = "Do you want to uninstall this app? Instagram",
            ),
        )
    }

    @Test
    fun theDialogIsBlockedOutsideTheWindow() {
        assertFalse(
            NuclearUninstallPolicy.allowsDialog(
                untilMs = now - 5, nowMs = now,
                dialogText = "Do you want to uninstall this app? Instagram",
            ),
        )
    }

    @Test
    fun uninstallingFocusFlowItselfIsNeverLetThrough() {
        assertFalse(
            NuclearUninstallPolicy.allowsDialog(
                untilMs = now + 10_000, nowMs = now,
                dialogText = "Do you want to uninstall this app? FocusFlow",
            ),
        )
        assertFalse(
            NuclearUninstallPolicy.allowsDialog(
                untilMs = now + 10_000, nowMs = now,
                dialogText = "Uninstall com.tbtechs.focusflow",
            ),
        )
    }

    @Test
    fun appKindFollowsTheSystemFlags() {
        assertEquals(NuclearUninstallPolicy.Kind.NORMAL, NuclearUninstallPolicy.kindOf(0))
        assertEquals(
            NuclearUninstallPolicy.Kind.SYSTEM,
            NuclearUninstallPolicy.kindOf(ApplicationInfo.FLAG_SYSTEM),
        )
        assertEquals(
            NuclearUninstallPolicy.Kind.UPDATED_SYSTEM,
            NuclearUninstallPolicy.kindOf(
                ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
            ),
        )
    }

    @Test
    fun outcomeWhenTheAppIsGone() {
        assertEquals(
            NuclearUninstallPolicy.Outcome.REMOVED,
            NuclearUninstallPolicy.outcome(-1, stillInstalled = false, NuclearUninstallPolicy.Kind.NORMAL),
        )
    }

    @Test
    fun outcomeWhenASystemAppOnlyLostItsUpdates() {
        assertEquals(
            NuclearUninstallPolicy.Outcome.UPDATES_REMOVED,
            NuclearUninstallPolicy.outcome(-1, stillInstalled = true, NuclearUninstallPolicy.Kind.UPDATED_SYSTEM),
        )
    }

    @Test
    fun outcomeWhenAnOrdinaryAppIsStillThereAfterConfirming() {
        assertEquals(
            NuclearUninstallPolicy.Outcome.NOT_REMOVED,
            NuclearUninstallPolicy.outcome(-1, stillInstalled = true, NuclearUninstallPolicy.Kind.NORMAL),
        )
    }

    @Test
    fun outcomeWhenCancelledAndFailed() {
        assertEquals(
            NuclearUninstallPolicy.Outcome.CANCELLED,
            NuclearUninstallPolicy.outcome(0, stillInstalled = true, NuclearUninstallPolicy.Kind.NORMAL),
        )
        assertEquals(
            NuclearUninstallPolicy.Outcome.FAILED,
            NuclearUninstallPolicy.outcome(1, stillInstalled = true, NuclearUninstallPolicy.Kind.NORMAL),
        )
    }

    @Test
    fun anAppThatIsGoneCountsAsRemovedWhateverTheResultCode() {
        // Some OEM uninstallers return CANCELED even after removing the app.
        assertEquals(
            NuclearUninstallPolicy.Outcome.REMOVED,
            NuclearUninstallPolicy.outcome(0, stillInstalled = false, NuclearUninstallPolicy.Kind.NORMAL),
        )
    }

    @Test
    fun everyOutcomeHasAPlainMessage() {
        NuclearUninstallPolicy.Outcome.values().forEach {
            val text = NuclearUninstallPolicy.message(it, "Instagram")
            assertTrue(it.name, text.isNotBlank())
        }
        assertTrue(
            NuclearUninstallPolicy.message(NuclearUninstallPolicy.Outcome.REMOVED, "Instagram")
                .contains("Instagram"),
        )
    }
}
