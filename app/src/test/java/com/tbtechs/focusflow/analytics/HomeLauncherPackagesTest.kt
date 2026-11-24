package com.tbtechs.focusflow.analytics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLauncherPackagesTest {
    @Test
    fun knownHomeScreensAreRecognised() {
        listOf(
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.mi.android.globallauncher",
            "com.huawei.android.launcher",
            "com.hihonor.android.launcher",
            "com.oneplus.launcher",
            "net.oneplus.launcher",
            "com.transsion.hilauncher",
            "com.nothing.launcher",
            "com.teslacoilsw.launcher",
            "com.microsoft.launcher",
            "com.android.systemui",
            "com.google.android.googlequicksearchbox.nexuslauncher",
        ).forEach { assertTrue(it, HomeLauncherPackages.isHomeScreenPackage(it)) }
    }

    @Test
    fun matchingIgnoresCase() {
        assertTrue(HomeLauncherPackages.isHomeScreenPackage("Com.Android.Launcher3"))
    }

    @Test
    fun ordinaryAppsAreNotHomeScreens() {
        listOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.android.settings",
            "com.google.android.apps.photos",
            "com.whatsapp",
            "com.android.chrome",
            "com.launcherlike.notes",
        ).forEach { assertFalse(it, HomeLauncherPackages.isHomeScreenPackage(it)) }
    }

    @Test
    fun blankIsNotAHomeScreen() {
        assertFalse(HomeLauncherPackages.isHomeScreenPackage(""))
    }
}
