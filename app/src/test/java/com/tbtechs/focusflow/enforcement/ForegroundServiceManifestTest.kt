package com.tbtechs.focusflow.enforcement

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the foreground-service declarations. On Android 14+ a service that calls
 * startForeground() without a manifest type crashes with
 * MissingForegroundServiceTypeException, and Android 15 refuses dataSync services
 * from BOOT_COMPLETED and stops them after six hours.
 */
class ForegroundServiceManifestTest {
    private val manifest: String by lazy {
        val file = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File)
            .firstOrNull(File::exists)
        assertNotNull("AndroidManifest.xml not found from ${File(".").absolutePath}", file)
        file!!.readText()
    }

    private fun serviceBlock(name: String): String {
        val match = Regex("""<service\b[^>]*android:name="$name"[^>]*>""").find(manifest)
        assertNotNull("service $name is not declared", match)
        return match!!.value
    }

    private fun typeOf(name: String): String? =
        Regex("""android:foregroundServiceType="([^"]+)"""")
            .find(serviceBlock(name))?.groupValues?.get(1)

    @Test
    fun taskServiceDeclaresOnlySpecialUse() {
        assertEquals("specialUse", typeOf(".enforcement.ForegroundTaskService"))
    }

    @Test
    fun vpnServiceDeclaresSpecialUse() {
        assertEquals("specialUse", typeOf(".enforcement.NetworkBlockerVpnService"))
    }

    @Test
    fun noServiceDeclaresDataSync() {
        assertFalse(manifest.contains("dataSync"))
        assertFalse(manifest.contains("FOREGROUND_SERVICE_DATA_SYNC"))
    }

    @Test
    fun specialUsePermissionIsDeclared() {
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
    }
}
