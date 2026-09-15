package com.tbtechs.focusflow.enforcement

/**
 * Application-local broadcast contract shared by native enforcement components.
 *
 * This replaces the historical React Native bridge constants. The broadcasts
 * remain intentionally explicit because receivers and the UI may be running
 * in different process lifetimes.
 */
object EnforcementEventContract {
    const val ACTION_APP_BLOCKED = "com.tbtechs.focusflow.APP_BLOCKED"
    const val EXTRA_BLOCKED_PKG = "blockedPackage"
    const val ACTION_NOTIF_ACTION = "com.tbtechs.focusflow.NOTIF_ACTION"
    const val EXTRA_NOTIF_ACTION_TYPE = "notifActionType"
}