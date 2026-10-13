package com.tbtechs.focusflow.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnListSavePolicyTest {
    @Test
    fun savingAnEmptyListPreservesBothMasterSwitches() {
        assertEquals(
            VpnListMasterSwitches(enabled = true, vpn = true),
            VpnListSavePolicy.masterSwitchesAfterSave(
                currentEnabled = true,
                currentVpn = true,
                hasPackages = false,
            ),
        )
        assertEquals(
            VpnListMasterSwitches(enabled = false, vpn = false),
            VpnListSavePolicy.masterSwitchesAfterSave(
                currentEnabled = false,
                currentVpn = false,
                hasPackages = false,
            ),
        )
    }

    @Test
    fun aNonEmptyListTurnsOnButNeverTurnsOffTheMasterSwitches() {
        assertEquals(
            VpnListMasterSwitches(enabled = true, vpn = true),
            VpnListSavePolicy.masterSwitchesAfterSave(
                currentEnabled = false,
                currentVpn = false,
                hasPackages = true,
            ),
        )
        assertEquals(
            VpnListMasterSwitches(enabled = true, vpn = true),
            VpnListSavePolicy.masterSwitchesAfterSave(
                currentEnabled = true,
                currentVpn = true,
                hasPackages = true,
            ),
        )
    }

    @Test
    fun legacyVpnListMigrationMergesSortedDistinctEntries() {
        assertEquals(
            listOf("com.a", "com.b", "com.c"),
            VpnPackageListMigrationPolicy.mergeLegacyPackages(
                explicitPackages = listOf("com.b", "com.a"),
                legacyPackages = listOf("com.c", "com.b", ""),
            ),
        )
    }
}
