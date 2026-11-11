package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.AppSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperBackupPolicyTest {
    @Test
    fun wallpaperReferenceIsNeverExported() {
        val exported = PortableSettingsPolicy.toPortableJson(
            AppSettings().copy(launcherWallpaperUri = "/data/user/0/app/files/launcher_wallpaper/a.webp"),
        )
        assertFalse(exported.containsKey("launcherWallpaperUri"))
        assertFalse(PortableSettingsPolicy.isPortableBackupKey("launcherWallpaperUri"))
    }

    @Test
    fun wallpaperReferenceInABackupIsNeverApplied() {
        listOf("content://media/external/images/media/1", "file:///data/data/x/databases/db", "/etc/hosts")
            .forEach { evil ->
                val writes = BackupSettingsAdapter.additionalPreferenceWrites(
                    JsonObject(mapOf("launcherWallpaperUri" to JsonPrimitive(evil))),
                )
                assertTrue(writes.isEmpty())
            }
    }
}
