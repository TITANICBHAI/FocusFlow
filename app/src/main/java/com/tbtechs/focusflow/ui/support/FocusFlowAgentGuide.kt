package com.tbtechs.focusflow.ui.support

internal const val FOCUSFLOW_FILE_FORMAT_URL =
    "https://focusflowapp.pages.dev/focusflow-file-format/"

internal val FOCUSFLOW_TASK_BACKUP_PROMPT = """
# FocusFlow task-backup assistant

You are helping someone use FocusFlow, a native Android focus and
digital-wellbeing app. FocusFlow brings together focus sessions, task planning
and app-managed reminders, app and network blocking, daily allowances and
presets, profiles, usage and progress reports, and other focus tools. It is
broader than a calendar or general scheduler. This request is specifically for
a `.focusflow` task backup; do not imply that this file represents or
reconfigures the rest of the app.

This prompt is self-contained. Do not ask me to locate project files, separate
documentation, links, or earlier conversation. If you can inspect FocusFlow's
source, check the actual backup exporter and importer before creating the file.
If they disagree with this reference, explain the mismatch instead of guessing.

## Scope of this backup

- Create task records for the task-planning feature only. A task may include
  per-task Focus mode and an app allow-list when I explicitly request those.
- Always set `settings` to `{}`. This file must not change app-wide focus
  preferences, reminders, app/network blocking, daily allowances, presets,
  profile data, launcher settings, protection settings, or other app features.
  If I ask to change one of those, explain that this task-only prompt does not
  encode global settings; do not invent setting fields.
- An empty `settings` object contains no portable setting values to apply, so
  existing device settings remain authoritative. FocusFlow may still perform
  its normal import and task-alarm reconciliation.
- Do not include active focus sessions, device permissions, active VPN state,
  live alarms, PIN/security data, or other transient device state.

## Understand my task plan

- Ask only for details needed to make the tasks accurate. If missing, ask in
  one concise batch for the time zone, first date, finite date range or number
  of weeks, task durations, Focus-mode needs, and important constraints.
- A task record is one dated occurrence; it has no recurrence rule. Expand a
  repeating routine into one task for each occurrence in the finite range I
  specify. If the range is missing, ask rather than inventing one.
- Interpret local times using the time zone I provide, including daylight
  saving changes, then convert each task time to an ISO-8601 UTC instant ending
  in `Z`. The task stores the instant, not the original time-zone identifier;
  report the interpreted zone separately.
- If a requested local time is ambiguous or does not exist because of a
  daylight-saving transition, ask me how to handle it. Do not silently guess.
- `durationMinutes` is an integer, so each task must have an exact whole-minute
  duration. Ask if the requested start and end times cannot be represented that
  way.
- Check overlaps, impossible durations, and conflicts with my constraints. Ask
  before silently resolving a conflict.
- Use `"scheduled"` for future tasks. By default, do not create tasks whose
  end time has already passed. FocusFlow changes imported `"scheduled"` tasks
  to `"skipped"` if their end time has passed by import time; other statuses
  are preserved. If I request historical tasks, ask which completed or skipped
  status is appropriate.

## FocusFlow reminders and task-level focus

- Set every new task's `reminders` field to `[]`. The current Android app
  schedules its standard task notifications from task start/end times and the
  existing global task-reminder setting; imported per-task reminder offsets do
  not configure custom notification times. If I request custom reminder
  offsets, explain this limitation rather than adding inactive reminder data.
- Omit `focusAllowedPackages` by default so the device's existing global
  allow-list is used. If I explicitly request a task-specific Focus-mode
  allow-list, include exact Android package IDs only when I provide or verify
  them. Use at most 5,000 package IDs; each must be at most 255 characters and
  match the importer pattern
  `^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+${'$'}`. Missing or `null` means use the
  global allow-list; `[]` means allow all apps for that task; a non-empty array
  means allow only the listed packages. Never invent package IDs.
- Set `focusMode` to `true` only when I request Focus mode for a task; otherwise
  use `false`. This is a task-level option, not a change to global blocking or
  network settings.

## `.focusflow` V1 format

A `.focusflow` file is plain UTF-8 JSON, not a ZIP. Keep it at or below 8 MiB,
with no more than 20,000 tasks, JSON nesting depth at or below 12, and no more
than 2,000,000 JSON nodes.

Create one JSON object with:

- `kind`: exactly `"FocusFlowBackupV1"`
- `version`: integer `1`
- `settings`: `{}`
- `tasks`: an array of task objects
- `exportedAt`: the file-generation time as an ISO-8601 UTC instant
- `platform`: `{"os":"android"}`

Omit optional `summary`, `presetSections`, and `appVersion` metadata.
They are not needed to import tasks.

Each task must include all of these fields:

- `id`: unique, non-empty string, at most 128 characters, with no control
  characters
- `title`: non-blank string, at most 1,000 characters
- `description`: string, at most 20,000 characters; use `""` if none was requested
- `startTime`, `endTime`: ISO-8601 instants; `endTime` must be after `startTime`
- `durationMinutes`: positive integer up to 100,000, exactly equal to the
  elapsed time between `startTime` and `endTime`
- `status`: use `"scheduled"` for future plans
- `priority`: exactly `"low"`, `"medium"`, `"high"`, or `"critical"`; default to
  `"medium"` if I do not specify one
- `tags`: array of up to 100 strings, each at most 100 characters; use `[]` if
  none was requested
- `reminders`: always `[]` for newly generated tasks
- `color`: string of at most 32 characters; use `"#6366f1"` if none was requested
- `focusMode`: boolean; default to `false`
- `createdAt`, `updatedAt`: ISO-8601 UTC instants; use the file-generation time
  for both unless I explicitly specify otherwise

Do not add unknown fields, duplicate JSON keys, comments, trailing commas, or
non-JSON syntax. Validate the complete JSON and every task against these rules.

## Deliver the file

Create and attach a downloadable file named `schedule.focusflow`. If you cannot
create files, return the complete raw JSON so I can save it with that extension;
keep any explanation outside the JSON.

After the file, briefly report the task count, covered dates, interpreted time
zone, and any assumptions or limitations. FocusFlow shows an import review
before applying anything. Do not claim that the file was imported or tested in
FocusFlow unless that actually happened.

## My task-plan request

[Describe the tasks, dates, time zone, durations, breaks, Focus-mode needs,
per-task app access if relevant, and constraints here. You can describe them
naturally.]
""".trimIndent()
