package com.tbtechs.focusflow.ui.home.subtask

internal enum class SubtaskTimerMode(val label: String) {
    REST("Rest"),
    DURATION("Duration"),
}

internal data class SubtaskTimerState(
    val mode: SubtaskTimerMode? = null,
    val remainingSeconds: Int = 0,
    val announcement: String = "",
) {
    val isRunning: Boolean
        get() = mode != null && remainingSeconds > 0

    fun start(mode: SubtaskTimerMode, seconds: Int): SubtaskTimerState {
        require(seconds > 0) { "Timer duration must be positive." }
        return SubtaskTimerState(
            mode = mode,
            remainingSeconds = seconds,
            announcement = "${mode.label} timer started.",
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
                    announcement = "${currentMode.label} timer finished.",
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
            announcement = "${currentMode.label} timer stopped.",
        )
    }
}

internal data class SubtaskTimerTick(
    val state: SubtaskTimerState,
    val finished: Boolean,
)
