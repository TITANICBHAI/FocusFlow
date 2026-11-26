package com.tbtechs.focusflow.ui.home.subtask

/** Which kind of timer is running. The words shown for it come from string resources, not from here. */
internal enum class SubtaskTimerMode {
    REST,
    DURATION,
}

/** What happened to a timer. The screen turns this into a sentence in the current language. */
internal enum class SubtaskTimerEvent { STARTED, FINISHED, STOPPED }

internal data class SubtaskTimerAnnouncement(
    val mode: SubtaskTimerMode,
    val event: SubtaskTimerEvent,
)

internal data class SubtaskTimerState(
    val mode: SubtaskTimerMode? = null,
    val remainingSeconds: Int = 0,
    val announcement: SubtaskTimerAnnouncement? = null,
) {
    val isRunning: Boolean
        get() = mode != null && remainingSeconds > 0

    fun start(mode: SubtaskTimerMode, seconds: Int): SubtaskTimerState {
        require(seconds > 0) { "Timer duration must be positive." }
        return SubtaskTimerState(
            mode = mode,
            remainingSeconds = seconds,
            announcement = SubtaskTimerAnnouncement(mode, SubtaskTimerEvent.STARTED),
        )
    }

    fun tick(): SubtaskTimerTick {
        val currentMode = mode
        if (currentMode == null || remainingSeconds <= 0) {
            return SubtaskTimerTick(this, finished = false)
        }

        val nextSeconds = remainingSeconds - 1
        return if (nextSeconds == 0) {
            SubtaskTimerTick(
                state = copy(
                    mode = null,
                    remainingSeconds = 0,
                    announcement = SubtaskTimerAnnouncement(currentMode, SubtaskTimerEvent.FINISHED),
                ),
                finished = true,
            )
        } else {
            SubtaskTimerTick(
                state = copy(remainingSeconds = nextSeconds),
                finished = false,
            )
        }
    }

    fun stop(): SubtaskTimerState {
        val currentMode = mode ?: return this
        return copy(
            mode = null,
            remainingSeconds = 0,
            announcement = SubtaskTimerAnnouncement(currentMode, SubtaskTimerEvent.STOPPED),
        )
    }
}

internal data class SubtaskTimerTick(
    val state: SubtaskTimerState,
    val finished: Boolean,
)
