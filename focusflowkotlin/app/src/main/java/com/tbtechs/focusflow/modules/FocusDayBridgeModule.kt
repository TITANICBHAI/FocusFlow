package com.tbtechs.focusflow.modules

/**
 * Compatibility contract for enforcement code that still publishes the
 * historical FocusFlow broadcast names.
 *
 * This intentionally keeps the old symbol and constants. It is not a React
 * Native module and has no React dependency; the pure-Kotlin app can migrate
 * consumers to StateFlow/SharedFlow later without breaking native broadcasts
 * in the meantime.
 */
object FocusDayBridgeModule {
    const val NAME = "FocusDayBridge"
    const val JS_EVENT_NAME = "FocusDayEvent"
    const val ACTION_APP_BLOCKED = "com.tbtechs.focusflow.APP_BLOCKED"
    const val EXTRA_BLOCKED_PKG = "blockedPackage"
    const val ACTION_NOTIF_ACTION = "com.tbtechs.focusflow.NOTIF_ACTION"
    const val EXTRA_NOTIF_ACTION_TYPE = "notifActionType"
}