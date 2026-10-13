package com.tbtechs.focusflow.analytics

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.Locale

/** Shared app-name and category resolution for legacy and shadow usage writers. */
object UsageAppMetadata {
    fun resolveAppName(context: Context, packageName: String): String =
        try {
            val packageManager = context.packageManager
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }

    fun resolveCategory(context: Context, packageName: String): String {
        val apiCategory = try {
            context.packageManager.getApplicationInfo(packageName, 0).category
        } catch (_: PackageManager.NameNotFoundException) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        return when (apiCategory) {
            ApplicationInfo.CATEGORY_SOCIAL -> "social"
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_GAME -> "entertainment"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
            ApplicationInfo.CATEGORY_NEWS -> "news"
            ApplicationInfo.CATEGORY_MAPS,
            ApplicationInfo.CATEGORY_IMAGE -> "utility"
            else -> packageNameHeuristic(packageName)
        }
    }

    private fun packageNameHeuristic(packageName: String): String {
        val name = packageName.lowercase(Locale.ROOT)
        return when {
            listOf("instagram", "facebook", "twitter", "snapchat", "tiktok", "linkedin", "reddit")
                .any(name::contains) -> "social"
            listOf("youtube", "netflix", "spotify", "twitch").any(name::contains) -> "entertainment"
            listOf("whatsapp", "telegram", "discord", "messenger").any(name::contains) -> "communication"
            listOf("chrome", "gmail", "drive", "maps", "calendar", "sheets").any(name::contains) -> "utility"
            else -> "other"
        }
    }
}
