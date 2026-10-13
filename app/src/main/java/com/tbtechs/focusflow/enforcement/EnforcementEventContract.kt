package com.tbtechs.focusflow.enforcement

/**
 * Application-local broadcast contract shared by native enforcement components.
 *
 * This contains the native enforcement broadcasts that have active consumers.
 * Notification actions use the persisted replay path in
 * [receivers.NotificationActionReceiver] and are intentionally not duplicated
 * as an unreceived bridge broadcast.
 */
object EnforcementEventContract {
    const val ACTION_APP_BLOCKED = "com.tbtechs.focusflow.APP_BLOCKED"
    const val EXTRA_BLOCKED_PKG = "blockedPackage"
}