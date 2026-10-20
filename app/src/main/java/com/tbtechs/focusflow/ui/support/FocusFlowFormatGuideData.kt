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
    "Merge keeps existing tasks and adds tasks with new IDs. A same-ID task is skipped, " +
        "except that the review screen can opt in to adding subtasks to matching tasks that have none. " +
        "Existing subtasks are never overwritten."

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
        "It can group one session into a task with subtasks, without changing FocusFlow's other features or global settings."

internal const val FOCUSFLOW_ASSETS_NOTE =
    "Images are stored in a top-level assets map and referenced by asset ID. FocusFlow validates, " +
        "resizes, and stores imported images privately on this device. Remote image links are not supported; " +
        "keep the complete file under 8 MiB."

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
    FocusFlowGuideItem("subtasks", "Optional array for the parts of one session; see Subtasks & images."),
)

internal val FOCUSFLOW_SUBTASK_FIELDS = listOf(
    FocusFlowGuideItem("id / title", "Each subtask needs a unique ID (1–64 letters, digits, _ or -) and a title (1–200 characters)."),
    FocusFlowGuideItem("subtitle", "Optional short supporting text, at most 120 characters."),
    FocusFlowGuideItem("layout", "Optional steps or overview; omitted layout uses steps when steps exist, otherwise overview."),
    FocusFlowGuideItem("explanation", "Optional plain text, at most 4,000 characters. Blank lines start paragraphs; lines beginning with - are bullets."),
    FocusFlowGuideItem("steps", "Optional list of up to 30 steps. Each step needs text (up to 1,500 characters); title and image are optional."),
    FocusFlowGuideItem("tips / avoid", "Optional arrays of up to 10 strings each, up to 300 characters per string."),
    FocusFlowGuideItem("image", "Optional overview image object with an asset ID and optional alt text."),
    FocusFlowGuideItem("progress", "Do not include done or setsDone in generated files; progress belongs to the app."),
)

internal val FOCUSFLOW_GOAL_KINDS = listOf(
    FocusFlowGuideItem("sets", "sets (1–99) plus reps (1–9,999) or seconds (1–86,400); optional restSeconds (0–3,600), weightKg (0–1,000), and label."),
    FocusFlowGuideItem("reps / duration", "reps needs reps (1–9,999); duration needs seconds (1–86,400)."),
    FocusFlowGuideItem("distance", "meters from 1 to 1,000,000."),
    FocusFlowGuideItem("text", "label is required and can be at most 80 characters."),
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
  "assets": { ... },                         // optional; image data used by subtasks
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

internal const val FOCUSFLOW_SUBTASK_EXAMPLE = """
{
  "kind": "FocusFlowBackupV1",
  "version": 1,
  "exportedAt": "2026-10-20T10:00:00.000Z",
  "platform": { "os": "android" },
  "settings": {},
  "tasks": [{
    "id": "upper-body-demo",
    "title": "Upper body A",
    "description": "",
    "startTime": "2026-10-21T10:00:00.000Z",
    "endTime": "2026-10-21T11:00:00.000Z",
    "durationMinutes": 60,
    "status": "completed",
    "priority": "medium",
    "tags": ["gym"],
    "reminders": [],
    "color": "#22c55e",
    "focusMode": false,
    "createdAt": "2026-10-20T10:00:00.000Z",
    "updatedAt": "2026-10-21T11:00:00.000Z",
    "subtasks": [
      {
        "id": "pushup",
        "title": "Push-ups",
        "goal": { "kind": "sets", "sets": 3, "reps": 12, "restSeconds": 60 },
        "layout": "steps",
        "steps": [
          { "title": "Start position", "text": "Hands just wider than shoulders; brace your core." },
          { "title": "Lower and press", "text": "Lower with control, then press back to the start." }
        ],
        "tips": ["Keep a straight line from head to heels"]
      },
      {
        "id": "plank",
        "title": "Plank",
        "goal": { "kind": "duration", "seconds": 45 },
        "layout": "overview",
        "explanation": "Keep your body in a straight line.\n\n- Breathe steadily\n- Keep shoulders above elbows"
      }
    ]
  }]
}
"""
