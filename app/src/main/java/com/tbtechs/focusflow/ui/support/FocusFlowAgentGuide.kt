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
  optional subtasks (with optional steps and picture slots) when I request them, plus
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
- Group related parts of one session into one task with subtasks when that fits
  my request. For example, put the exercises in one workout session into one
  task; do not create a separate task per exercise.
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

## `.focusflow` file formats

A `.focusflow` file is plain UTF-8 JSON. Always make plain JSON with no picture
data inside it. Only if I give you real image files do you need the ZIP bundle
described in the "Advanced" section near the end.

JSON files must stay at or below 8 MiB, with no more than 20,000 tasks, JSON
nesting depth at or below 12, and no more than 2,000,000 JSON nodes. Files
containing subtasks use version 2; task-only files use version 1.

Create one JSON object with:

- `kind`: exactly `"FocusFlowBackupV1"`
- `version`: integer `1` for task-only files, or `2` when subtasks are included
- `settings`: `{}`
- `tasks`: an array of task objects
- `exportedAt`: the file-generation time as an ISO-8601 UTC instant
- `platform`: `{"os":"android"}`
- optional `assets`: only for image files I give you (see "Advanced"); otherwise leave it out

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
- `subtasks`: optional array for the parts of a session; see the rules below

Do not add unknown fields other than those documented here, duplicate JSON keys,
comments, trailing commas, or non-JSON syntax. Validate the complete JSON and
every task against these rules.

## Subtasks (optional)

Use subtasks when one task is a session made of several parts that I tick off one
by one—for example, a workout where each exercise is a subtask. Put the parts of
one session into ONE task. Do not use subtasks for a single-activity task. Omit
`subtasks` (or use `[]`) when not needed.

Each subtask object:
- `id`: unique within the task, 1–64 characters of letters, digits, `_` or `-`
- `title`: required, at most 200 characters
- `subtitle`: optional, at most 120 characters
- `goal`: optional; `kind` is one of:
  - `"sets"`: `sets` (1–99) plus `reps` (1–9999) or `seconds` (1–86400);
    optional `repsMax` (1–9999) is allowed only with `reps` and must be at least `reps`
    optional `restSeconds` (0–3600) and `weightKg` (0–1000)
  - `"reps"`: `reps` (1–9999), optionally with `repsMax` (1–9999) at least as large as `reps`
  - `"duration"`: `seconds` (1–86400)
  - `"distance"`: `meters` (1–1000000)
  - `"text"`: `label` (at most 80 characters)
  Any goal may also have a short `label`, such as "per side".
- `layout`: optional `"steps"` or `"overview"`. If omitted, steps are used when
  `steps` exist; otherwise the overview layout is used.
- `explanation`: optional plain text, at most 4,000 characters. Blank lines
  start paragraphs; lines beginning with `- ` are bullets. No Markdown or HTML.
- `steps`: optional array of at most 30 objects; each has required `text` (at
  most 1,500 characters), optional `title` (at most 120), and an optional
  `photo` (see "Pictures")
- `tips`, `avoid`: optional arrays of at most 10 strings each, at most 300
  characters per string
- `photo`: optional picture request for the whole subtask; see "Pictures" below
- `image`: leave it out unless I gave you real image files (see "Advanced")
- Do not set `done` or `setsDone`; leave progress to the app. Do not add other
  subtask fields.

A subtask may have no steps.

Subtask limits: at most 50 subtasks per task, and all subtasks of one task together
must stay under ${com.tbtechs.focusflow.data.backup.BackupJsonLimits.formatByteLimit(com.tbtechs.focusflow.data.backup.BackupJsonLimits.MAX_SUBTASK_JSON_BYTES_PER_TASK)}
of JSON. Long explanations and links add up.

Valid subtask example:
${FOCUSFLOW_SUBTASK_EXAMPLE}

## Pictures: describe them, FocusFlow attaches them

You cannot reliably make or attach good pictures, so do not try. Never put image
data in the file. Instead, add a `photo` object where a picture would help. In
FocusFlow that item then shows "Photo needed", and I add the picture later, or
let the app download it from your link.

A `photo` object has these fields (all plain text):

- `brief`: ONE sentence that says what the picture should show: the subject, the
  angle and the key detail. Aim for under 160 characters; never more than 300.
- `url`: optional. A direct `https://` link to a JPEG, PNG or WebP picture file.
  Follow the link rules below.
- `alt`: optional, at most 200 characters. A short description of the picture.

Always include `brief`, even when you add `url`, because the brief is what I see
if the link does not work. This is how a `photo` looks:

"photo": {
  "brief": "Side view of a push-up at the bottom, chest just above the floor.",
  "url": "https://images.example.com/pushup-side.jpg",
  "alt": "Push-up, side view"
}

The link above is a made-up placeholder. Never reuse it.

Where to put `photo`:

- A subtask with steps (an exercise, a recipe, a set-up) needs a DIFFERENT picture
  for each step that looks different, in order, so I can follow along: the start,
  the middle of the movement, the finish. Put a `photo` on every step whose
  position, view or stage differs from the step before it. Give each one its own
  `brief` that describes that exact moment, and its own `url` when you found one.
  You may add up to 8 step photos per subtask.
- Never paste the same `url` into several steps just to fill them. One picture
  repeated for five different steps teaches nothing. Reuse a link on more than one
  step only when that one picture really shows what each of those steps says (for
  example two steps that are the same position), and mention it in your report.
- If you cannot find a different picture for a step, leave `url` out of that step
  and keep its `brief`. An empty slot is better than a wrong or repeated picture.
  Never copy a neighbouring step's link to avoid an empty slot.
- Find the pictures one step at a time. Search for each step's moment separately
  (for example "push-up bottom position side view"), then open the result to
  confirm it shows that moment. Pictures of one exercise from the same source and
  angle read best.
- The subtask's own `photo` is one overview picture (the finished position or the
  whole set-up). Add it only when it is a different picture from every step
  picture, or when the subtask has no step photos.
- `photo` goes only on subtasks and steps. Never put it on a task.
- If the same item appears in many tasks, repeat the same `brief` and the same
  `url` every time. FocusFlow downloads each different link only once.

A good `brief`: "Side view of a push-up at the bottom, chest just above the floor."
A bad `brief`: "push-up", "a nice picture", or a whole paragraph.

Rules for `url`. When in doubt, leave `url` out and keep the `brief`. Leaving it
out is always fine:

1. Only use a link you opened yourself, or that a search result showed as a
   picture. Never invent, guess or rebuild a link from memory, and never edit one.
   If you cannot browse the web, leave out every `url`.
2. The link must lead straight to the picture file, not to a web page that shows
   it. Good: a direct file link such as `https://upload.wikimedia.org/...jpg`.
3. Prefer sites that allow reuse: Wikimedia Commons, Unsplash, Pexels, Pixabay.
   Do not use Google Images, Pinterest, Instagram, Facebook or other social sites.
4. Do not use links that expire or need a sign-in (for example ones containing
   `token=`, `Expires=` or `Signature=`), shortened links, or links that bounce
   through several other sites.
5. Plain `https://` only: no `http://`, no user name or password, no `#` part, no
   port number, no IP address, no `localhost`, no spaces, at most 2,048 characters.

Large original files are refused, so when a page offers a ready-made smaller size
of the same picture (Wikimedia Commons lists "Other resolutions"; around 1280
pixels wide is ideal), use that link instead of the full-size original. Use only a
link the page itself shows; never build one yourself.

FocusFlow downloads a link only after I tap Download. It then checks that the
file is really a JPEG, PNG or WebP picture of at most 6 MiB, stores its own
copy privately on my phone, and shows only that copy. It never opens a link in
a browser. Do not claim that a link works unless you opened it.

Before you deliver, check that:

- every `photo` has a `brief` of at most 300 characters;
- every `url` starts with `https://`, has no spaces, and is a link you opened;
- inside each subtask, count the distinct `url` values of its step photos. Steps
  that show different moments must have different links; any steps that share a
  link really show the same picture;
- every step photo has its own `brief` for that step's moment, not a copy of the
  subtask's `brief`;
- there is no `photo` on a task, and no `image` or `assets` unless I gave you
  image files;
- the file is valid JSON and `version` is 2.

## Advanced: only if I give you real image files

Skip this whole section unless I attached actual image files and asked you to put
them in the file. Use `image` (not `photo`) for those pictures. Never invent
image data. ${FOCUSFLOW_ASSETS_NOTE}

For JSON, resize each image to at most 1280 pixels on the long edge, save it as
WebP or JPEG, base64-encode it without a `data:` prefix, and add it under
top-level `assets` as `"<asset id>": {"mime":"image/webp","data":"<base64>"}`.
Reuse an asset ID for repeated use of the same image.

For a ZIP bundle:

${FOCUSFLOW_BUNDLE_RECIPE}

${FOCUSFLOW_BUNDLE_GUIDANCE}

The manifest `assets` entries use `file` paths for images inside the ZIP:
${FOCUSFLOW_BUNDLE_MANIFEST_EXAMPLE}

## Deliver the file

Create and attach a downloadable file named `schedule.focusflow`. If you cannot
create files, return the complete raw JSON so I can save it with that extension;
keep any explanation outside the JSON.

After the file, briefly report the task count, covered dates, interpreted time
zone, how many picture slots you added and how many of them have a `url` (and
which links you opened yourself), plus, for each subtask with step photos, how
many steps got their own link and which steps share one or have no link, and any assumptions or limitations. FocusFlow
shows an import review
before applying anything. Do not claim that the file was imported or tested in
FocusFlow unless that actually happened.

## My task-plan request

[Describe the tasks, dates, time zone, durations, breaks, Focus-mode needs,
per-task app access if relevant, and constraints here. You can describe them
naturally.]
""".trimIndent()
