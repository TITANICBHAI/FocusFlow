package com.tbtechs.focusflow.modules

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.tbtechs.focusflow.services.AppBlockerAccessibilityService

/**
 * SharedPrefsModule
 *
 * JS name: NativeModules.SharedPrefs
 *
 * Lets JS write focus-mode and standalone-block state into Android SharedPreferences
 * so AppBlockerAccessibilityService and BootReceiver can read it even when the JS
 * bundle is not running (app killed, phone rebooted).
 *
 * Methods:
 *   - setFocusActive(active)                          → Promise<null>
 *   - setAllowedPackages(packages)                    → Promise<null>
 *   - setActiveTask(name, endMs, nextName?)            → Promise<null>
 *   - setStandaloneBlock(active, packages, untilMs)   → Promise<null>
 */
class SharedPrefsModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        const val NAME = "SharedPrefs"
    }

    override fun getName(): String = NAME

    private fun prefs() = reactContext.getSharedPreferences(
        AppBlockerAccessibilityService.PREFS_NAME, android.content.Context.MODE_PRIVATE
    )

    /** Tells the AccessibilityService and BootReceiver whether task focus mode is active. */
    @ReactMethod
    fun setFocusActive(active: Boolean, promise: Promise) {
        prefs().edit().putBoolean("focus_active", active).apply()
        promise.resolve(null)
    }

    /**
     * Writes the list of ALLOWED package names for task-based focus blocking.
     * The AccessibilityService blocks any app NOT in this list during a task focus.
     * Pass the full allow-list every call — the service replaces the previous value.
     */
    @ReactMethod
    fun setAllowedPackages(packages: ReadableArray, promise: Promise) {
        val list = (0 until packages.size()).map { "\"${packages.getString(it)}\"" }
        val json = "[${list.joinToString(",")}]"
        prefs().edit().putString("allowed_packages", json).apply()
        promise.resolve(null)
    }

    /**
     * Stores the active task details so BootReceiver can restart the service after reboot.
     *
     * @param taskId   DB id of the task — passed to ForegroundTaskService so notification
     *                 action buttons (Done / +15m / +30m / Skip) carry the correct id after reboot.
     * @param name     Task display name
     * @param endMs    Task end time as epoch milliseconds
     * @param nextName Name of the next task (pass null or empty string if none)
     */
    @ReactMethod
    fun setActiveTask(taskId: String, name: String, endMs: Double, nextName: String?, promise: Promise) {
        prefs().edit()
            .putString("task_id", taskId)
            .putString("task_name", name)
            .putLong("task_end_ms", endMs.toLong())
            .putString("next_task_name", nextName?.takeIf { it.isNotBlank() })
            .apply()
        promise.resolve(null)
    }

    /**
     * Controls standalone app blocking — independent of any task.
     *
     * When active = true, the AccessibilityService will block every package in the
     * provided list until untilMs is reached, even if no task focus is running.
     *
     * Collision with task-based blocking: union (both block lists are enforced).
     *
     * @param active    Whether standalone blocking is currently enabled
     * @param packages  ReadableArray of package names to block
     * @param untilMs   Epoch milliseconds when standalone blocking expires (0 = no expiry)
     */
    @ReactMethod
    fun setStandaloneBlock(active: Boolean, packages: ReadableArray, untilMs: Double, promise: Promise) {
        val list = (0 until packages.size()).map { "\"${packages.getString(it)}\"" }
        val json = "[${list.joinToString(",")}]"
        prefs().edit()
            .putBoolean("standalone_block_active", active)
            .putString("standalone_blocked_packages", json)
            .putLong("standalone_block_until_ms", untilMs.toLong())
            .apply()
        promise.resolve(null)
    }
}
