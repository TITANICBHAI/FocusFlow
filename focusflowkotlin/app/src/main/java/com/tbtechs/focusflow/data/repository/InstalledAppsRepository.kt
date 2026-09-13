package com.tbtechs.focusflow.data.repository

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.inputmethod.InputMethodManager
import android.content.pm.PackageManager

/**
 * An installed application visible to FocusFlow's app-selection surfaces.
 *
 * [icon] is the PackageManager Drawable directly. The old bridge converted it
 * to PNG/base64 only because React Native could not receive Android Drawables.
 */
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isIme: Boolean,
    val icon: Drawable?,
)

/**
 * InstalledAppsRepository
 *
 * Returns launcher-visible applications and registered input methods, excluding
 * FocusFlow itself. IMEs remain included even when they have no launcher icon.
 */
class InstalledAppsRepository(context: Context) {

    private val appContext = context.applicationContext

    suspend fun getInstalledApps(): List<InstalledAppInfo> {
        val packageManager = appContext.packageManager
        val imePackages = try {
            val inputMethodManager = appContext.getSystemService(
                Context.INPUT_METHOD_SERVICE,
            ) as? InputMethodManager
            inputMethodManager
                ?.inputMethodList
                ?.mapTo(mutableSetOf()) { it.packageName }
                ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }

        @Suppress("DEPRECATION")
        val applications = packageManager.getInstalledApplications(
            PackageManager.GET_META_DATA,
        )

        return buildList {
            for (application in applications) {
                if (application.packageName == appContext.packageName) continue

                val isIme = application.packageName in imePackages
                val launchIntent = packageManager.getLaunchIntentForPackage(
                    application.packageName,
                )
                if (launchIntent == null && !isIme) continue

                val appName = try {
                    packageManager.getApplicationLabel(application).toString()
                } catch (_: Exception) {
                    application.packageName
                }

                val icon = try {
                    packageManager.getApplicationIcon(application.packageName)
                } catch (_: Exception) {
                    null
                }

                add(
                    InstalledAppInfo(
                        packageName = application.packageName,
                        appName = appName,
                        isIme = isIme,
                        icon = icon,
                    ),
                )
            }
        }
    }
}