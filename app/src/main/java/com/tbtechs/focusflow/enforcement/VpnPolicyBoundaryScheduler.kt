package com.tbtechs.focusflow.enforcement

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.tbtechs.focusflow.enforcement.receivers.VpnPolicyBoundaryReceiver
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/**
 * Arms the next standalone-expiry or VPN schedule transition. The same
 * persisted windows feed both the VPN coordinator and this scheduler.
 */
internal object VpnPolicyBoundaryScheduler {
    const val ACTION_BOUNDARY = "com.tbtechs.focusflow.action.VPN_POLICY_BOUNDARY"

    private const val TAG = "VpnPolicyBoundary"
    private const val PREFS_NAME = "focusday_prefs"
    private const val REQUEST_CODE = 7803

    fun scheduleNextBoundary(
        context: Context,
        nowMs: Long = System.currentTimeMillis(),
    ): Long? {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val windows = readScheduleWindows(prefs)
        val nextBoundary = VpnPolicyBoundaryPolicy.nextBoundaryMs(
            activeStandalone = prefs.getBoolean("standalone_block_active", false),
            standaloneUntilMs = prefs.getLong("standalone_block_until_ms", 0L),
            windows = windows,
            nowMs = nowMs,
        )
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return nextBoundary

        if (nextBoundary == null) {
            pendingIntent(appContext, PendingIntent.FLAG_NO_CREATE)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            return null
        }

        val alarmIntent = requireNotNull(
            pendingIntent(appContext, PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()),
        ) { "Could not create the VPN policy boundary alarm." }
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)

        if (canScheduleExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextBoundary,
                    alarmIntent,
                )
                return nextBoundary
            } catch (error: Exception) {
                Log.w(TAG, "Exact boundary scheduling failed; using an inexact alarm.", error)
            }
        }

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextBoundary,
                alarmIntent,
            )
        } catch (error: Exception) {
            Log.e(TAG, "Could not schedule the VPN policy boundary alarm.", error)
        }
        return nextBoundary
    }

    fun scheduleWindows(prefs: SharedPreferences): List<VpnScheduleWindow> =
        readScheduleWindows(prefs)

    fun currentScheduleTargets(
        prefs: SharedPreferences,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<String> =
        VpnPolicyBoundaryPolicy.scheduleTargets(
            windows = readScheduleWindows(prefs),
            nowMs = nowMs,
            timeZone = timeZone,
        )

    private fun readScheduleWindows(prefs: SharedPreferences): List<VpnScheduleWindow> {
        val raw = prefs.getString("greyout_schedule", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val packages = readPackages(item)
                    val days = item.optJSONArray("days")
                        ?: item.optJSONArray("daysOfWeek")
                        ?: continue
                    add(
                        VpnScheduleWindow(
                            packages = packages,
                            daysOfWeek = (0 until days.length()).map(days::optInt),
                            startMinuteOfDay =
                                item.optInt("startHour") * 60 + item.optInt("startMin"),
                            endMinuteOfDay =
                                item.optInt("endHour") * 60 + item.optInt("endMin"),
                            enabled = item.optBoolean("enabled", true),
                            vpnEnabled = item.optBoolean("vpnEnabled", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun readPackages(item: JSONObject): List<String> {
        val array = item.optJSONArray("pkgs") ?: item.optJSONArray("packages")
        val packages = if (array != null && array.length() > 0) {
            (0 until array.length()).map(array::optString)
        } else {
            listOf(item.optString("pkg"))
        }
        return packages.filter(String::isNotBlank).distinct()
    }

    private fun pendingIntent(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, VpnPolicyBoundaryReceiver::class.java).setAction(ACTION_BOUNDARY),
            flags or immutableFlag(),
        )

    private fun immutableFlag(): Int =
        PendingIntent.FLAG_IMMUTABLE
}
