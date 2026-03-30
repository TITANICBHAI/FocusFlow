package com.tbtechs.focusflow.modules

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import java.io.ByteArrayOutputStream

/**
 * InstalledAppsModule
 *
 * JS name: NativeModules.InstalledApps
 *
 * Exposes a single method to JS:
 *   - getInstalledApps() → Promise<Array<{ packageName, appName, iconBase64? }>>
 *
 * Only returns user-installed apps (system apps are filtered out by FLAG_SYSTEM).
 */
class InstalledAppsModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "InstalledApps"

    @ReactMethod
    fun getInstalledApps(promise: Promise) {
        try {
            val pm = reactContext.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val result: WritableArray = Arguments.createArray()

            for (app in apps) {
                val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (isSystemApp || isUpdatedSystemApp) continue

                val map: WritableMap = Arguments.createMap()
                map.putString("packageName", app.packageName)

                val label = pm.getApplicationLabel(app).toString()
                map.putString("appName", label)

                try {
                    val icon: Drawable = pm.getApplicationIcon(app.packageName)
                    val bitmap = drawableToBitmap(icon)
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 85, stream)
                    val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    map.putString("iconBase64", b64)
                } catch (_: Exception) {
                    map.putNull("iconBase64")
                }

                result.pushMap(map)
            }

            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("INSTALLED_APPS_ERROR", e.message, e)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width  = drawable.intrinsicWidth.takeIf  { it > 0 } ?: 48
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 48
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
