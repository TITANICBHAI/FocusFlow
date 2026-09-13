package com.tbtechs.focusflow.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * LauncherController
 *
 * Converted from ForegroundLaunchModule.
 * Controls the real LauncherActivity through Android's home and package-launch
 * intents; it does not own or duplicate LauncherActivity's UI or state.
 */
class LauncherController(private val context: Context) {

    private val appContext = context.applicationContext

    /** Sends the device to the system home screen. */
    suspend fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
    }

    /**
     * Brings FocusFlow's existing launch activity to the foreground without
     * stacking a second copy.
     */
    suspend fun bringToFront() {
        val intent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
            }
        intent?.let { appContext.startActivity(it) }
    }

    /**
     * Full-screen lock UI remains deferred, matching the bridge behavior:
     * currently this simply brings FocusFlow to the front.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun showOverlay(message: String) {
        bringToFront()
    }

    suspend fun hasOverlayPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(appContext)
        } else {
            true
        }

    suspend fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(appContext)
        ) {
            appContext.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${appContext.packageName}"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    private fun startActivity(intent: Intent) {
        val activity = context as? Activity
        if (activity != null && !activity.isFinishing) {
            activity.startActivity(intent)
        } else {
            appContext.startActivity(intent)
        }
    }
}