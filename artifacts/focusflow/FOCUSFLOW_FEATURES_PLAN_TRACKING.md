# FocusFlow Features & Fixes Plan — Tracking

Authoritative source: `FOCUSFLOW_FEATURES_PLAN_1788805485351.md`

This file is the mutable execution and audit record. The source plan is kept
unchanged. A checked box means the current checkout contains the work and the
implementation has been reviewed against the actual code paths; it does not
mean Android device validation has passed.

## Status legend

- `[ ]` Not started or not yet evidenced
- `[-]` Partially implemented, or implementation differs from the source plan
- `[x]` Implemented and source-reviewed
- `[!]` Blocked or needs a product/architecture decision

## Current audit

The plan was compared with the FocusFlow artifact on 2026-09-08. This is a
source audit only; no generated `android/` project, Gradle build, APK, or
physical Android device is available in this checkout.

### Highest-priority finding: `.focusflow` opening was only partial

The backup format, export flow, picker import flow, parser, and restore
service already existed. The OS-open path is now implemented at source level:

- `FileImportHost` listens for cold-start and foreground `Linking` events.
- It now reads provider-backed URIs with native `ContentResolver`/file-stream
  handling rather than `expo-file-system`.
- `NativeFilePickerModule.kt` and its TypeScript wrapper now expose `readUri`.
- The config plugin, manifest reference, and installer fallback now cover the
  narrow octet-stream and provider wildcard MIME variants. The durable native
  source/config-plugin path, rather than a generated `android/` tree, remains
  the source of truth.
- `app/import-confirm.tsx` now gives the user a settings/task summary and
  merge/replace choice. The full JSON stays behind an in-memory opaque
  pending-import ID instead of going through Router params.
- File association must preserve/read URI grants supplied by Android and must
  validate the parsed `FocusFlowBackupV1` envelope before showing import
  controls. Broad matching is acceptable only with that content validation;
  this still needs real provider/device validation.

The implementation checklist below distinguishes source completion from
device/emulator validation.

## Execution checklist

### 1. Dark mode

- [x] `TaskCard.tsx` obtains theme colors inline rather than hard-coded light
  colors in renderable color positions.
- [x] `AppPickerSheet.tsx` obtains theme colors inline, including the modal
  safe-area/background gap and search controls.
- [x] Focus tab active-task tint is reduced in dark mode and moved to the
  panel border.
- Notes: renderable backgrounds/text now consume `useTheme()` values; semantic
  accent colors remain shared constants.

### 2. `.focusflow` file association and import

- [x] Backup envelope/parser/restore service exists.
- [x] Settings export and picker-based import exist.
- [x] Cold-start and warm-start link listeners exist in `FileImportHost`.
- [x] Add native `readUri` through `ContentResolver` with explicit failure
  reporting.
- [x] Add the TypeScript `readUri` bridge and a focused pending-import handoff
  test for content ownership.
- [x] Make the association filters complete in the durable native source:
  config plugin, `manifest_additions.xml` documentation, and
  `install.sh` fallback patch. Cover provider MIME variation without
  claiming arbitrary files without parse validation.
- [x] Replace direct Expo FileSystem reads for incoming provider URIs with the
  native bridge. Keep `file://` handling deliberate rather than assuming all
  file URIs are readable.
- [x] Add a real import confirmation route/screen. Do not pass full backup JSON
  through Router params; use a bounded pending-import handoff.
- [x] Offer merge vs replace, show task/settings impact, block replacement
  during an active focus session, and reuse the existing restore callback
  semantics.
- [-] Add tests for invalid JSON, wrong envelope kind, MIME variation, cold
  start, warm resume, duplicate URI delivery, cancellation, and native read
  failure. Parser and pending-handoff coverage exists; Android intent/native
  failure coverage remains blocked on the native/device boundary.
- [ ] Device-validate opening a `.focusflow` file from Downloads, Files,
  Drive/cloud storage, and the Settings picker on supported API levels.

### 3. Group schedule VPN

- [x] `RecurringBlockSchedule` and `GreyoutWindow` have optional VPN fields.
- [x] The schedule-level VPN toggle is present, including edit-state hydration.
- [x] Enabled schedules preserve `vpnEnabled` when converted to greyout windows.
- [x] The schedule VPN SharedPrefs bridge, AppContext sync, and write
  deduplication are present.
- [x] The coordinator reads schedule VPN packages as an independent policy
  source.
- [-] Source-isolation and schedule-boundary coverage exists at contract/unit
  level, but still needs the full native/device validation boundary.
- Notes: active schedule VPN resolution now honors non-empty `vpnPackages` and
  falls back to `packages`, including overnight windows and VPN-only schedules.

### 4. Standalone block VPN

- [x] The fifth `vpnPackages` argument is wired through Kotlin, the TypeScript
  bridge, and `setStandaloneBlockAndAllowance`.
- [x] Active and inactive snapshots write separate standalone VPN source state,
  isolated from explicit and schedule VPN sources.
- [-] Contract coverage now checks the bridge/source shape, but start, expiry,
  manual stop, overlap, and empty-list behavior still need broader coverage.

### 5. Auto-generated reports

- [x] The pure on-device insight engine with thresholded detectors exists.
- [x] Bounded report-note persistence and pruning exist.
- [x] The day/week report screen and optional note fields exist.
- [x] Stats has passive report entry points and data-availability gating without
  a review-writing requirement.
- [x] The Stats summary component remains separate from report narrative
  generation.
- [-] Deterministic detector, ranking, empty-data, week-boundary, report-order,
  and note-retention coverage exists, but the full planned matrix is not yet
  complete.
- Notes: report weeks now use `settings.weekStartDay`, and report narrative is
  rendered before the summary card.

### 6. Calendar-anchored week stats

- [x] Add `weekStartDay: 0` to settings/defaults.
- [x] Add the shared week-start/week-end utility.
- [x] Replace the rolling `weeklyDays` calculation with the configured
  calendar anchor and add the date-range/just-started messaging.
- [x] Confirm the existing bounded historical fetch covers the anchor without
  introducing a full-table query.

### 7. Focus-session lifecycle and recovery

- [x] Add one shared timeout helper and bound every native stop call.
- [x] Close the DB focus row before native calls that may hang.
- [x] Persist an explicit stop so auto-start cannot immediately restart the
  same task occurrence.
- [x] Keep Stop Focus available when the session's task is orphaned.
- [x] Add 30-second reconciliation with a five-minute end-time grace period.
- [x] Cap a single open session's contribution to today's focus total.
- [-] Add tests for hangs, rejected native calls, orphaned tasks, completed or
  skipped linked tasks, expiry, and repeated stop calls. Source contracts now
  cover orphan stop access, dismissal persistence, and the defensive cap, but
  the complete behavior matrix is still open.

## Guardrails for implementation

1. Read the current source before applying each checklist item. This document
   records confirmed gaps, not permission to assume the source plan's line
   numbers or architecture are still true.
2. Keep `android-native/` and the config plugin authoritative; never hand-edit
   a generated `android/` tree as the lasting fix.
3. Preserve FocusFlow's explicit backup privacy boundary. Android automatic
   backup remains disabled; `.focusflow` export/import is user-controlled.
4. Do not broaden file association without content validation. A file manager
   may report `application/octet-stream`, `*/*`, or another provider-specific
   MIME type, so the app must reject non-FocusFlow content clearly.
5. Prefer bounded, deterministic, on-device behavior. Do not add a network/LLM
   dependency for reports or upload backup contents.
6. Separate source implementation from device validation. A passing TypeScript
   or contract check cannot claim that Android intent delivery, URI grants,
   Accessibility, VPN, or service lifecycle works on a device.

## Recommended order

1. `.focusflow` URI reading and import confirmation, because the reported
   user-facing failure is reproducible in the current implementation.
2. Focus-session lifecycle recovery, because a stuck session can block normal
   use and corrupt daily focus totals.
3. Dark mode and calendar week anchoring.
4. Standalone VPN, then group schedule VPN.
5. Reports and their bounded persistence/tests.

## Validation gates

- [ ] FocusFlow JavaScript/unit/contract checks pass for the completed slice
  (currently blocked because the workspace install is stopped by the package
  firewall on an unrelated `orval` tarball).
- [ ] Generated Android source is produced through Expo prebuild plus the
  documented native installation/config-plugin path.
- [ ] Native/Kotlin source compiles in a generated Android project.
- [ ] `.focusflow` cold-start and warm-resume flows are device-tested against
  real `content://` providers.
- [ ] No checklist item is marked `[x]` solely because a similarly named file
  or partial implementation exists.
