package com.tbtechs.focusflow.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.delay
import org.json.JSONArray

/**
 * NuclearModeRepository
 *
 * Converted from NuclearModeModule.
 * Opens Android's package uninstall confirmation UI; the system dialog still
 * requires the user to confirm each uninstall.
 */
class NuclearModeRepository(private val context: Context) {

    private val appContext = context.applicationContext

    suspend fun requestUninstallApp(packageName: String) {
        launchUninstallDialog(packageName)
    }

    /**
     * Opens each uninstall dialog with the legacy 500 ms stagger. The suspend
     * boundary replaces the bridge Handler without creating a new thread.
     */
    suspend fun requestUninstallApps(packagesJson: String) {
        val array = JSONArray(packagesJson)
        val packages = (0 until array.length()).map { array.getString(it) }

        packages.forEachIndexed { index, packageName ->
            if (index > 0) delay(500L)
            try {
                launchUninstallDialog(packageName)
            } catch (_: Exception) {
                // The bridge swallowed failures inside each delayed callback.
            }
        }
    }

    suspend fun isAppInstalled(packageName: String): Boolean =
        try {
            appContext.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }

    private fun launchUninstallDialog(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val activity = context as? Activity
        if (activity != null && !activity.isFinishing) {
            activity.startActivity(intent)
        } else {
            appContext.startActivity(intent)
        }
    }
}