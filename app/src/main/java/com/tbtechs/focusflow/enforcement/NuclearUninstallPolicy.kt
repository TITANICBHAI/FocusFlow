package com.tbtechs.focusflow.enforcement

import android.content.pm.ApplicationInfo

/**
 * Rules for Nuclear Mode's uninstall requests.
 *
 * Two things go wrong without them:
 *  - FocusFlow's own guards (System Protection, "Block uninstall from launcher", "Install and
 *    uninstall protection") see Android's uninstall screen and close it, including the one Nuclear
 *    Mode just opened. So Nuclear Mode opens a short window in which that screen is let through.
 *  - A preinstalled app that has been updated only loses its updates when "uninstalled", and one
 *    that was never updated cannot be uninstalled at all. The screen has to say so.
 */
object NuclearUninstallPolicy {
    /** Preference key (in the shared FocusFlow preferences) holding when the window ends. */
    const val KEY_WINDOW_UNTIL_MS = "nuclear_uninstall_until_ms"

    const val WINDOW_MS = 45_000L

    private const val OWN_LABEL = "focusflow"
    private const val OWN_PACKAGE = "com.tbtechs.focusflow"

    enum class Kind { NORMAL, UPDATED_SYSTEM, SYSTEM }

    enum class Outcome { REMOVED, UPDATES_REMOVED, NOT_REMOVED, CANCELLED, FAILED }

    /** The window is open from a request until [WINDOW_MS] later. A far-future value is ignored. */
    fun isOpen(untilMs: Long, nowMs: Long): Boolean =
        untilMs > nowMs && untilMs - nowMs <= WINDOW_MS

    /**
     * Whether a guard may let an uninstall screen through. FocusFlow itself is never let through,
     * so the window cannot be used to remove the app that is enforcing it.
     */
    fun allowsDialog(untilMs: Long, nowMs: Long, dialogText: String): Boolean {
        if (!isOpen(untilMs, nowMs)) return false
        val text = dialogText.lowercase()
        return OWN_LABEL !in text && OWN_PACKAGE !in text
    }

    fun kindOf(applicationFlags: Int): Kind = when {
        applicationFlags and ApplicationInfo.FLAG_SYSTEM == 0 -> Kind.NORMAL
        applicationFlags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 -> Kind.UPDATED_SYSTEM
        else -> Kind.SYSTEM
    }

    /**
     * What happened, from the uninstall screen's result code (RESULT_OK = -1, RESULT_CANCELED = 0,
     * RESULT_FIRST_USER = 1) and whether the app is still installed afterwards. The installed check
     * wins: some OEM uninstallers report "cancelled" after removing the app.
     */
    fun outcome(resultCode: Int, stillInstalled: Boolean, kind: Kind): Outcome = when {
        !stillInstalled -> Outcome.REMOVED
        resultCode == -1 ->
            if (kind == Kind.NORMAL) Outcome.NOT_REMOVED else Outcome.UPDATES_REMOVED
        resultCode == 0 -> Outcome.CANCELLED
        else -> Outcome.FAILED
    }

    fun message(outcome: Outcome, appName: String): String = when (outcome) {
        Outcome.REMOVED -> "$appName was uninstalled."
        Outcome.UPDATES_REMOVED ->
            "$appName is a system app. Only its updates were removed; Android keeps the original version."
        Outcome.NOT_REMOVED ->
            "$appName is still installed. Android may be protecting it (a device admin or work policy). Check Settings - Apps."
        Outcome.CANCELLED -> "Uninstall cancelled."
        Outcome.FAILED ->
            "Android could not uninstall $appName. It may be protected by a device admin or a work policy."
    }
}
