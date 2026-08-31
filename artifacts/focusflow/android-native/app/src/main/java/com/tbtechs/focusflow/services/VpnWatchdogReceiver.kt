package com.tbtechs.focusflow.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * VpnWatchdogReceiver
 *
 * A system-level watchdog for the VPN network blocker. It wakes the app process
 * on OEMs that suppress START_STICKY restarts and delegates recovery to the
 * durable VPN policy coordinator.
 *
 * Recovery is gated by the existing user opt-in self-heal setting. The
 * coordinator additionally checks VPN consent and refuses to replace another
 * VPN, so this receiver never fights the user's chosen VPN.
 */
class VpnWatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_NAME = "focusday_prefs"
        private const val ACTION_WATCHDOG = "com.tbtechs.focusflow.VPN_WATCHDOG"
        private const val REQUEST_CODE = 8801
        private const val INTERVAL_MS = 60_000L

        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = buildIntent(context) ?: return
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                pi,
            )
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = buildIntent(context) ?: return
            am.cancel(pi)
            pi.cancel()
        }

        private fun buildIntent(context: Context): PendingIntent? = try {
            val intent = Intent(context, VpnWatchdogReceiver::class.java).apply {
                action = ACTION_WATCHDOG
            }
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WATCHDOG) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("net_block_enabled", false) ||
            !prefs.getBoolean("net_block_vpn", true) ||
            !prefs.getBoolean("net_block_self_heal", false)
        ) {
            return
        }

        val now = System.currentTimeMillis()
        val focusActive = prefs.getBoolean("focus_active", false).let { active ->
            active && (
                prefs.getLong("task_end_ms", 0L) <= 0L ||
                    now < prefs.getLong("task_end_ms", 0L)
                )
        }
        val standaloneActive = prefs.getBoolean("standalone_block_active", false).let { active ->
            active && (
                prefs.getLong("standalone_block_until_ms", 0L) <= 0L ||
                    now < prefs.getLong("standalone_block_until_ms", 0L)
                )
        }
        val alwaysOn = prefs.getBoolean("always_block_active", false)

        if (!focusActive && !standaloneActive && !alwaysOn &&
            !NetworkBlockerVpnService.hasPersistentVpnConfiguration(prefs)
        ) {
            cancel(context)
            return
        }

        if (NetworkBlockerVpnService.isRunning) return

        // Recompute the effective policy before attempting recovery. This keeps
        // watchdog behavior aligned with explicit + Focus-derived targets.
        VpnPolicyCoordinator.reconcile(context)
    }
}