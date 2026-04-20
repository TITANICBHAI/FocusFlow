package com.tbtechs.focusflow.modules

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * NativeFilePickerModule
 *
 * JS name: NativeModules.NativeFilePicker
 *
 * Opens Android's system file picker (ACTION_OPEN_DOCUMENT) and reads the
 * selected file's content + display name back to JS.  No third-party
 * dependencies — uses only the Android Storage Access Framework.
 *
 * Methods exposed to JS:
 *   pickFile(mimeType: String)
 *       → Promise<{ name: String, content: String } | null>
 *         null if the user cancelled or reading failed.
 *
 * Usage:
 *   pickFile("application/json")  — shows only JSON files
 *   pickFile("*\/*")              — shows all files
 */
class NativeFilePickerModule(private val ctx: ReactApplicationContext) :
    ReactContextBaseJavaModule(ctx) {

    companion object {
        const val NAME = "NativeFilePicker"
        private const val REQUEST_PICK_FILE = 0xF200
    }

    private var pendingPromise: Promise? = null

    private val activityEventListener = object : ActivityEventListener {
        override fun onActivityResult(
            activity: Activity,
            requestCode: Int,
            resultCode: Int,
            data: Intent?
        ) {
            if (requestCode != REQUEST_PICK_FILE) return
            val promise = pendingPromise ?: return
            pendingPromise = null

            if (resultCode != Activity.RESULT_OK || data?.data == null) {
                promise.resolve(null)
                return
            }

            val uri: Uri = data.data!!
            try {
                val name = resolveDisplayName(uri)
                val content = ctx.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.readText()
                    ?: run { promise.resolve(null); return }

                val map = Arguments.createMap().apply {
                    putString("name", name)
                    putString("content", content)
                }
                promise.resolve(map)
            } catch (e: Exception) {
                promise.reject("E_READ_FAILED", "Could not read file: ${e.message}")
            }
        }

        override fun onNewIntent(intent: Intent) {}
    }

    init {
        ctx.addActivityEventListener(activityEventListener)
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun pickFile(mimeType: String, promise: Promise) {
        val activity: Activity? = ctx.currentActivity
        if (activity == null) {
            promise.reject("E_NO_ACTIVITY", "No foreground activity available")
            return
        }
        if (pendingPromise != null) {
            promise.reject("E_PICKER_BUSY", "A file pick is already in progress")
            return
        }
        pendingPromise = promise

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType.ifBlank { "*/*" }
        }

        @Suppress("DEPRECATION")
        activity.startActivityForResult(intent, REQUEST_PICK_FILE)
    }

    private fun resolveDisplayName(uri: Uri): String {
        ctx.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx) ?: uri.lastPathSegment ?: "file"
            }
        }
        return uri.lastPathSegment ?: "file"
    }
}
