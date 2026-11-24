package com.tbtechs.focusflow.analytics

import java.util.Locale

/**
 * Recognises the phone's home screen (and system chrome) so it is never counted as an app the
 * person uses. Stats count apps; going back to the home screen is not "reaching for" one.
 *
 * A fixed list only ever knew seven launchers, so every other phone (Android's own Launcher3,
 * Xiaomi's global launcher, Honor, Nothing, Nova, Microsoft...) showed its home screen as a top app
 * and as the "first app you open". So the rule is the package's last name segment: a home screen
 * is named "launcher", "launcher3", "nexuslauncher", "globallauncher" and so on.
 */
object HomeLauncherPackages {
    private val KNOWN = setOf(
        "com.android.systemui",
        "com.miui.home",
    )

    /** Last package segment that ends in "launcher" with an optional version number. */
    private val LAUNCHER_NAME = Regex("""^[a-z0-9_]*launcher[0-9]*$""")

    fun isHomeScreenPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val name = packageName.lowercase(Locale.ROOT)
        if (name in KNOWN || name.startsWith("com.android.systemui.")) return true
        return LAUNCHER_NAME.matches(name.substringAfterLast('.'))
    }
}
