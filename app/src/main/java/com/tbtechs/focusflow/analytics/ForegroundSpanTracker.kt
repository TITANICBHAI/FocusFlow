package com.tbtechs.focusflow.analytics

/**
 * Pure reducer for ordered UsageEvents. Platform event constants are translated
 * before reaching this class.
 */
class ForegroundSpanTracker(
    private val inferredTailCapMs: Long = DEFAULT_INFERRED_TAIL_CAP_MS,
    private val excludedPackages: Set<String> = DEFAULT_EXCLUDED_PACKAGES,
) {
    init {
        require(inferredTailCapMs > 0L)
    }

    fun sessions(
        events: List<ForegroundUsageEvent>,
        windowStartMs: Long,
        windowEndMs: Long,
        nowMs: Long,
    ): List<ForegroundSession> {
        if (windowStartMs >= windowEndMs) return emptyList()

        data class Active(
            val packageName: String,
            val startedAtMs: Long,
            var activityClassName: String?,
            val isNewOpen: Boolean,
        )

        val result = mutableListOf<ForegroundSession>()
        var active: Active? = null
        var previousForegroundPackage: String? = null

        fun close(atMs: Long, inferredTail: Boolean = false) {
            val open = active ?: return
            active = null
            val endedAtMs = atMs.coerceAtLeast(open.startedAtMs)
            if (endedAtMs > open.startedAtMs && open.packageName !in excludedPackages) {
                result += ForegroundSession(
                    packageName = open.packageName,
                    startedAtMs = open.startedAtMs,
                    endedAtMs = endedAtMs,
                    activityClassName = open.activityClassName,
                    isNewOpen = open.isNewOpen,
                    isInferredTail = inferredTail,
                )
            }
        }

        events.withIndex()
            .asSequence()
            .filter { it.value.timestampMs <= windowEndMs }
            .sortedWith(compareBy<IndexedValue<ForegroundUsageEvent>> { it.value.timestampMs }
                .thenBy { it.index })
            .forEach { indexed ->
                val event = indexed.value
                when (event.type) {
                    ForegroundEventType.ACTIVITY_RESUMED -> {
                        val packageName = event.packageName?.takeIf(String::isNotBlank) ?: return@forEach
                        val open = active
                        if (open == null) {
                            active = Active(
                                packageName = packageName,
                                startedAtMs = event.timestampMs,
                                activityClassName = event.activityClassName,
                                isNewOpen = previousForegroundPackage != packageName,
                            )
                            previousForegroundPackage = packageName
                        } else if (open.packageName != packageName) {
                            close(event.timestampMs)
                            active = Active(
                                packageName = packageName,
                                startedAtMs = event.timestampMs,
                                activityClassName = event.activityClassName,
                                isNewOpen = previousForegroundPackage != packageName,
                            )
                            previousForegroundPackage = packageName
                        } else if (
                            open.activityClassName != event.activityClassName &&
                            event.activityClassName != null
                        ) {
                            // A new activity in the same app supersedes the old one.
                            // Its delayed STOPPED event cannot close this foreground span.
                            open.activityClassName = event.activityClassName
                        }
                    }

                    ForegroundEventType.ACTIVITY_PAUSED -> {
                        val open = active
                        if (
                            open != null &&
                            open.packageName == event.packageName &&
                            (event.activityClassName == null ||
                                event.activityClassName == open.activityClassName)
                        ) {
                            close(event.timestampMs)
                        }
                    }

                    ForegroundEventType.ACTIVITY_STOPPED -> {
                        val open = active
                        // Match STOPPED to the exact activity; package-only matching
                        // lets a delayed stop close a newer activity in the same app.
                        if (
                            open != null &&
                            open.packageName == event.packageName &&
                            event.activityClassName != null &&
                            event.activityClassName == open.activityClassName
                        ) {
                            close(event.timestampMs)
                        }
                    }

                    ForegroundEventType.SCREEN_NON_INTERACTIVE,
                    ForegroundEventType.KEYGUARD_SHOWN,
                    ForegroundEventType.DEVICE_SHUTDOWN,
                    ForegroundEventType.DEVICE_STARTUP -> close(event.timestampMs)

                    ForegroundEventType.SCREEN_INTERACTIVE,
                    ForegroundEventType.KEYGUARD_HIDDEN -> Unit
                }
            }

        active?.let { open ->
            val inferredEndMs = minOf(
                nowMs,
                windowEndMs,
                open.startedAtMs + inferredTailCapMs,
            )
            close(inferredEndMs, inferredTail = true)
        }

        return result
            .filter { it.endedAtMs > windowStartMs && it.startedAtMs < windowEndMs }
            .sortedWith(compareBy<ForegroundSession> { it.startedAtMs }.thenBy { it.packageName })
    }

    companion object {
        const val DEFAULT_INFERRED_TAIL_CAP_MS = 4L * 60 * 60 * 1_000L

        val DEFAULT_EXCLUDED_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher",
            "com.oneplus.launcher",
        )
    }
}
