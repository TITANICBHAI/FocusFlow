package com.tbtechs.focusflow.ui.support

internal data class FocusFlowGuideItem(
    val title: String,
    val body: String,
)

internal const val FOCUSFLOW_FORMAT_GUIDE_INTRO =
    "A .focusflow file is a plain UTF-8 JSON document, not a ZIP archive. " +
        "It carries portable task and settings data that FocusFlow can validate and restore."

internal const val FOCUSFLOW_RUNTIME_STATE_NOTE =
    "A backup is not a snapshot of the live device. Active Focus sessions, current " +
        "enforcement state, PIN/device security state, and live alarms are not restored."

internal const val FOCUSFLOW_VERSION_NOTE =
    "Include version 1 when creating a file. The current importer requires the version " +
        "field and rejects a missing version or any value other than 1."

internal const val FOCUSFLOW_SUMMARY_NOTE =
    "Informational metadata only. The importer recalculates taskCount from tasks; the " +
        "settings and tasks payloads remain the source of restored data."

internal const val FOCUSFLOW_PRESET_SECTIONS_NOTE =
    "Descriptive inventory only. It does not activate blocking, timers, or other live behavior."

internal const val FOCUSFLOW_REMINDERS_NOTE =
    "Reminders are objects, not plain integer offsets. The app preserves this array " +
        "opaquely; Android notification slots are derived from task times instead."

internal const val FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE =
    "Missing or null means use the global allow-list. An explicit empty array means " +
        "all apps are allowed for this task. A non-empty array names the task-specific apps."

internal const val FOCUSFLOW_SETTINGS_BOUNDARY_NOTE =
    "Only portable settings are restored. Live toggles, onboarding/privacy state, PIN " +
        "state, and device-only resources stay local; omitted fields do not reset local values."

internal const val FOCUSFLOW_MERGE_NOTE =
    "Merge keeps existing tasks and adds tasks with new IDs. An identical same-ID task " +
        "is treated as a duplicate; different content with an existing ID is a conflict, not an overwrite."

internal const val FOCUSFLOW_REPLACE_NOTE =
    "Replace tasks replaces task rows only; it does not mean replace everything. The app " +
        "refuses task replacement while a Focus session is active."

internal const val FOCUSFLOW_SETTINGS_IMPORT_NOTE =
    "The Merge/Replace choice applies to tasks. Portable settings follow field-specific " +
        "rules; omitted settings and device-local values stay local."

internal const val FOCUSFLOW_EXTERNAL_IMPORT_NOTE =
    "Opening a .focusflow file from Files or another app follows the same flow: " +
        "validate, review, confirm, then restore."

internal const val FOCUSFLOW_GENERATION_NOTE =
    "Copy the AI task-backup prompt above and describe the tasks, dates, time zone, and constraints. " +
        "It creates a task-only .focusflow file with task records, without changing FocusFlow's other features or global settings."

internal val FOCUSFLOW_FILE_FACTS = listOf(
    FocusFlowGuideItem("Extension", ".focusflow"),
    FocusFlowGuideItem("Kind", "FocusFlowBackupV1"),
    FocusFlowGuideItem("Version", "1 for newly generated files"),
    FocusFlowGuideItem("Encoding", "UTF-8 JSON"),
    FocusFlowGuideItem("MIME type", "application/octet-stream"),
)

internal val FOCUSFLOW_TASK_FIELDS = listOf(
    FocusFlowGuideItem("id", "Required, unique string identifier."),
    FocusFlowGuideItem("title", "Required; cannot be blank."),
    FocusFlowGuideItem("description", "Optional text; use an empty string if none was requested."),
    FocusFlowGuideItem("startTime / endTime", "ISO-8601 instants with an offset; make endTime later than startTime."),
    FocusFlowGuideItem("durationMinutes", "Integer number of minutes."),
    FocusFlowGuideItem("status", "scheduled, active, completed, skipped, or overdue."),
    FocusFlowGuideItem("priority", "low, medium, high, or critical."),
    FocusFlowGuideItem("tags", "Array of strings; an empty array is valid."),
    FocusFlowGuideItem("reminders", FOCUSFLOW_REMINDERS_NOTE),
    FocusFlowGuideItem("color", "Required string; use #6366f1 if none was requested."),
    FocusFlowGuideItem("focusMode", "Required boolean; use false unless Focus mode was requested."),
    FocusFlowGuideItem("focusAllowedPackages", FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE),
    FocusFlowGuideItem("createdAt / updatedAt", "ISO-8601 instants; canonical generated files use UTC Z."),
)

internal val FOCUSFLOW_PORTABLE_SETTING_GROUPS = listOf(
    FocusFlowGuideItem(
        "Focus preferences",
        "darkMode, defaultDuration, pomodoroDuration, pomodoroBreak, " +
            "keepFocusActiveUntilTaskEnd, autoRescheduleEnabled.",
    ),
    FocusFlowGuideItem(
        "App lists",
        "allowedInFocus, alwaysOnPackages, alwaysOnVpnPackages, focusToolPackages, " +
            "launcherHiddenPackages, launcherDockPackages.",
    ),
    FocusFlowGuideItem(
        "Blocking and schedules",
        "blockedWords, greyoutSchedule, recurringBlockSchedules, dailyAllowanceEntries.",
    ),
    FocusFlowGuideItem(
        "Presets and profile",
        "allowedAppPresets, blockPresets, userProfile, launcherTheme.",
    ),
    FocusFlowGuideItem(
        "Protection",
        "protectionMode and focusMirrorVpnEnabled.",
    ),
)

internal val FOCUSFLOW_FORMAT_RULES = listOf(
    FocusFlowGuideItem(
        "Time values",
        "Use ISO-8601 instants with an explicit offset. Generated files use UTC Z timestamps.",
    ),
    FocusFlowGuideItem(
        "Package names",
        "Use exact Android package IDs, such as com.example.app—not app labels or display names.",
    ),
    FocusFlowGuideItem(
        "focusAllowedPackages",
        FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE,
    ),
    FocusFlowGuideItem(
        "Schedules",
        "Keep greyoutSchedule windows separate from recurringBlockSchedules; they are different data.",
    ),
    FocusFlowGuideItem(
        "summary",
        FOCUSFLOW_SUMMARY_NOTE,
    ),
    FocusFlowGuideItem(
        "presetSections",
        FOCUSFLOW_PRESET_SECTIONS_NOTE,
    ),
    FocusFlowGuideItem(
        "Protected apps",
        "A backup cannot override FocusFlow's system-protected package list.",
    ),
)

internal val FOCUSFLOW_IMPORT_GUIDANCE = listOf(
    FocusFlowGuideItem("Merge", FOCUSFLOW_MERGE_NOTE),
    FocusFlowGuideItem("Replace tasks", FOCUSFLOW_REPLACE_NOTE),
    FocusFlowGuideItem("Settings", FOCUSFLOW_SETTINGS_IMPORT_NOTE),
    FocusFlowGuideItem("External file", FOCUSFLOW_EXTERNAL_IMPORT_NOTE),
)

internal const val FOCUSFLOW_ENVELOPE_EXAMPLE = """
{
  "kind": "FocusFlowBackupV1",
  "version": 1,
  "exportedAt": "2025-01-15T10:00:00.000Z",
  "platform": { "os": "android" },
  "settings": { ... },
  "tasks": [ ... ],
  "presetSections": [ ... ],
  "summary": { ... }
}
"""

internal const val FOCUSFLOW_REMINDER_EXAMPLE = """
"reminders": [{
  "id": "reminder-001",
  "taskId": "task-001",
  "offsetMinutes": -5,
  "type": "pre-start"
}]
"""

internal const val FOCUSFLOW_VALID_BACKUP_EXAMPLE = """
{
  "kind": "FocusFlowBackupV1",
  "version": 1,
  "exportedAt": "2025-01-15T10:00:00.000Z",
  "platform": { "os": "android" },
  "settings": {},
  "tasks": [{
    "id": "weekly-review",
    "title": "Weekly review",
    "description": "",
    "startTime": "2025-01-15T09:00:00.000Z",
    "endTime": "2025-01-15T09:50:00.000Z",
    "durationMinutes": 50,
    "status": "completed",
    "priority": "medium",
    "tags": ["planning"],
    "reminders": [],
    "color": "#6366f1",
    "focusMode": false,
    "createdAt": "2025-01-14T10:00:00.000Z",
    "updatedAt": "2025-01-15T10:00:00.000Z"
  }]
}
"""
