package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.NuclearUninstallPolicy

/**
 * NuclearModeRepository
 *
 * Builds the requests for Android's own uninstall screen; the user still confirms each one.
 * The uninstall intent asks for a result, so Nuclear Mode can tell removed, cancelled and failed
 * apart. It must be started for a result and without FLAG_ACTIVITY_NEW_TASK, or Android reports
 * "cancelled" immediately.
 */
class NuclearModeRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        AppBlockerAccessibilityService.PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$packageName")
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }

    /** The app's page in Settings, where a system app can be disabled. */
    fun appInfoIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Lets FocusFlow's own guards leave the uninstall screen alone for a short while. Written
     * synchronously so the accessibility service sees it before the screen appears.
     */
    fun openGuardWindow() {
        prefs.edit()
            .putLong(
                NuclearUninstallPolicy.KEY_WINDOW_UNTIL_MS,
                System.currentTimeMillis() + NuclearUninstallPolicy.WINDOW_MS,
            )
            .commit()
    }

    fun closeGuardWindow() {
        prefs.edit().remove(NuclearUninstallPolicy.KEY_WINDOW_UNTIL_MS).commit()
    }

    fun kindOf(packageName: String): NuclearUninstallPolicy.Kind =
        try {
            NuclearUninstallPolicy.kindOf(
                appContext.packageManager.getApplicationInfo(packageName, 0).flags,
            )
        } catch (_: Exception) {
            NuclearUninstallPolicy.Kind.NORMAL
        }

    fun isAppInstalled(packageName: String): Boolean =
        try {
            appContext.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
}
