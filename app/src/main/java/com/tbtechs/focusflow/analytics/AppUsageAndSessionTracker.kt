package com.tbtechs.focusflow.analytics

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import com.tbtechs.focusflow.data.local.dao.AppSessionDao
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.entity.AppSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Persists live foreground usage and closed app sessions. It is fed by the
 * accessibility service, so it does not depend on the OS UsageEvents retention window.
 */
class AppUsageAndSessionTracker(
    private val context: Context,
    private val dailyUsageDao: DailyAppUsageDao,
    private val sessionDao: AppSessionDao,
    private val legacyWriteAllowed: suspend () -> Boolean = { true },
) {
    private companion object {
        private const val TAG = "UsageSessionTracker"
        private const val HEARTBEAT_MS = 20_000L
        private const val MIN_SESSION_MS = 1_000L
        private const val MAX_SESSION_MS = 4L * 60 * 60 * 1_000L
        private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher",
            "com.oneplus.launcher",
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var heartbeatJob: Job? = null
    @Volatile private var currentPackage = ""
    @Volatile private var sessionStartElapsed = 0L
    @Volatile private var sessionStartWall = 0L
    @Volatile private var lastCommitElapsed = 0L
    @Volatile private var screenOn = true

    fun onWindowStateChanged(pkg: String, nowMs: Long, nowElapsed: Long) {
        if (!screenOn || pkg in IGNORED_PACKAGES || pkg == context.packageName || pkg == currentPackage) {
            return
        }
        closeCurrentSession(nowElapsed)
        commitDailyUsage(nowElapsed)
        stopHeartbeat()

        currentPackage = pkg
        sessionStartElapsed = nowElapsed
        sessionStartWall = nowMs
        lastCommitElapsed = nowElapsed
        val appName = resolveAppName(pkg)
        val category = resolveCategory(pkg)
        scope.launch {
            if (!legacyWriteAllowed()) return@launch
            dailyUsageDao.incrementLaunchCount(
                date = epochMsToLocalDate(nowMs),
                packageName = pkg,
                appName = appName,
                category = category,
                lastUsedAt = nowMs,
            )
        }
        startHeartbeat()
    }

    fun onScreenOff(nowMs: Long, nowElapsed: Long) {
        screenOn = false
        stopHeartbeat()
        closeCurrentSession(nowElapsed)
        commitDailyUsage(nowElapsed)
    }

    fun onUserPresent() {
        screenOn = true
    }

    fun destroy() {
        stopHeartbeat()
        scope.cancel()
        Log.d(TAG, "destroyed")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_MS)
                commitDailyUsage(SystemClock.elapsedRealtime())
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun commitDailyUsage(nowElapsed: Long) {
        val pkg = currentPackage.takeIf { it.isNotEmpty() } ?: return
        if (nowElapsed <= lastCommitElapsed) return
        val startWall = sessionStartWall + (lastCommitElapsed - sessionStartElapsed)
        val endWall = sessionStartWall + (nowElapsed - sessionStartElapsed)
        lastCommitElapsed = nowElapsed
        if (endWall <= startWall) return

        val appName = resolveAppName(pkg)
        val category = resolveCategory(pkg)
        splitIntoHourlySegments(startWall, endWall).forEach { segment ->
            scope.launch {
                if (!legacyWriteAllowed()) return@launch
                dailyUsageDao.addForegroundTime(
                    date = segment.date,
                    packageName = pkg,
                    appName = appName,
                    category = category,
                    hour = segment.hour,
                    durationMs = segment.durationMs,
                    lastUsedAt = segment.endWall,
                )
            }
        }
    }

    private fun closeCurrentSession(nowElapsed: Long) {
        val pkg = currentPackage.takeIf { it.isNotEmpty() } ?: return
        currentPackage = ""
        val endWall = sessionStartWall + (nowElapsed - sessionStartElapsed)
        val durationMs = endWall - sessionStartWall
        if (durationMs !in MIN_SESSION_MS..MAX_SESSION_MS) return
        scope.launch {
            if (!legacyWriteAllowed()) return@launch
            sessionDao.insert(
                AppSessionEntity(
                    packageName = pkg,
                    appName = resolveAppName(pkg),
                    startedAt = sessionStartWall,
                    endedAt = endWall,
                    durationMs = durationMs,
                    localDate = epochMsToLocalDate(sessionStartWall),
                ),
            )
        }
    }

    private data class HourSegment(
        val date: String,
        val hour: Int,
        val durationMs: Long,
        val endWall: Long,
    )

    private fun splitIntoHourlySegments(startWall: Long, endWall: Long): List<HourSegment> {
        if (endWall <= startWall) return emptyList()
        val zone = ZoneId.systemDefault()
        val segments = mutableListOf<HourSegment>()
        var cursor = startWall
        while (cursor < endWall) {
            val zdt = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = zdt.withMinute(0).withSecond(0).withNano(0)
                .plusHours(1).toInstant().toEpochMilli()
            val segmentEnd = minOf(endWall, nextHour)
            segments += HourSegment(
                date = zdt.toLocalDate().format(DATE_FMT),
                hour = zdt.hour,
                durationMs = segmentEnd - cursor,
                endWall = segmentEnd,
            )
            cursor = segmentEnd
        }
        return segments
    }

    private fun epochMsToLocalDate(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT)

    private fun resolveAppName(packageName: String): String =
        UsageAppMetadata.resolveAppName(context, packageName)

    private fun resolveCategory(packageName: String): String =
        UsageAppMetadata.resolveCategory(context, packageName)
}