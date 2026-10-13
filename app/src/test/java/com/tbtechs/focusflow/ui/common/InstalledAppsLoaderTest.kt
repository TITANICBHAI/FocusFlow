package com.tbtechs.focusflow.ui.common

import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppsLoaderTest {
    @Test
    fun resolveReturnsCatalogMetadataForInstalledPackage() {
        val app = InstalledAppInfo(
            packageName = "com.example.app",
            appName = "Example",
            isIme = false,
            icon = null,
        )

        val resolved = InstalledAppsLoadState(apps = listOf(app), loading = false)
            .resolve(app.packageName)

        assertSame(app, resolved)
    }

    @Test
    fun resolveWaitsForCatalogBeforeCallingPackageMissing() {
        val resolved = InstalledAppsLoadState(loading = true)
            .resolve("com.example.app")

        assertNull(resolved)
    }

    @Test
    fun resolveMarksMissingPackageExplicitly() {
        val resolved = InstalledAppsLoadState(loading = false)
            .resolve("com.example.app")

        assertEquals("App not installed", resolved?.appName)
        assertEquals("com.example.app", resolved?.packageName)
        assertFalse(resolved?.isInstalled ?: true)
    }

    @Test
    fun resolveDistinguishesCatalogFailureFromMissingPackage() {
        val resolved = InstalledAppsLoadState(
            loading = false,
            error = IllegalStateException("catalog unavailable"),
        ).resolve("com.example.app")

        assertEquals("App details unavailable", resolved?.appName)
        assertTrue(resolved?.isInstalled == false)
    }
}