# FocusFlow Migration — Stage 2 (Replit, runs GPT-5.6 Luna)
## Pipeline position: Gemini (done) → Replit (you, now) → Claude → GPT Terra → Claude (final review)

This stage is split into **7 prompts**. Paste them one at a time, in order, into the
same Replit conversation — don't skip ahead. Wait for each one's report before moving
to the next. Prompt 1 carries the full context and rules; prompts 2 onward assume
that's already live in the conversation and only give you the next batch of work.

Keeping each prompt small isn't just about volume here — it's specifically to avoid
asking you to hold too much in view at once across a long session. Re-reading a
dependency file right before using it, rather than trusting what you read several
files ago, matters more for reliability on this stage than raw task difficulty does.

---

## PROMPT 1 of 7 — Read this in full, then convert SharedPrefsModule.kt

### What this app is, and why fidelity matters here
FocusFlow is a published Android app that helps people break compulsive phone and app
use, through enforced focus sessions, app blocking, a daily allowance system,
VPN-based network blocking, and a PIN gate on settings. It has real users depending
on the blocking actually holding. You are stage 2 of a 5-stage migration relay. The
stage after you (Claude) builds ViewModels that call the repository classes you're
about to write — if a method is missing, silently changed in behavior, or a
permission guard gets dropped along the way, that becomes a real bug in a real
person's phone-blocking app, discovered several stages later than it was introduced.

### Before you do anything
Read `focusflowkotlin/ARCHITECTURE.md`, sections 3.5, 3.6, 3.7, 3.12, and 6, in full.

### What already exists in your workspace — read these for real, not hypothetically
Gemini has already completed Stage 1. These files are real, at these exact paths.
Wherever your work calls into one of them, open and read the actual file — the
architecture doc describes intent, the file is ground truth:
```
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/enforcement/AversiveActionsManager.kt
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/enforcement/BlockOverlayActivity.kt
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/enforcement/ForegroundTaskService.kt
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/enforcement/LauncherActivity.kt
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/enforcement/receivers/FocusDayDeviceAdminReceiver.kt
```
`VpnPolicyCoordinator.kt` and `NetworkBlockerVpnService.kt` also exist in this folder
but aren't yours to wrap — Gemini handles the module that calls into those directly.
If any of the five files above are missing when you go looking, stop and report it.

### Not in scope — do not port these
`TaskAlarmModule.kt`, `UsageStatsModule.kt`, and `NetworkBlockModule.kt` are being
converted by Gemini, not you. If you see references to `AlarmRepository`,
`UsageStatsRepository`, or `VpnRepository` elsewhere, treat them as existing — don't
rebuild them. Also skip `SessionPinModule.kt` (handled separately, security-sensitive),
`FocusDayBridgeModule.kt` (deleted — replaced by StateFlow in the ViewModel layer),
and `FocusDayPackage.kt` (RN registration boilerplate, not needed in pure Kotlin).

### The read-first gate — mandatory, every file, every prompt in this stage
Before writing any target file: open and fully read (a) the source you're converting,
and (b) any enforcement/ file it calls into. Write two sentences: what the original
bridge module did, and what native Android object your new repository needs a
reference to. Only then start writing.

### Working-memory discipline — apply for the rest of this stage
Don't rely on something read earlier in this session once it's more than a couple of
files back. Before writing each repository, re-open the specific dependency file it
needs right then, even if you already read it once earlier in this conversation.

### Rules — apply for the rest of this stage
- Convert Promise-returning bridge methods to `suspend fun`. No `Thread()`,
  `Handler()`, or `AsyncTask`.
- Every public method that existed on the old bridge module needs a corresponding
  method on your Kotlin class — list them side by side in your report.
- If something looks like a bug in the source: flag it, don't fix it.
- If you're not sure a signature is right: say so, don't invent a plausible one.

### Target
```
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/data/repository/
```

### This prompt's file
| Source | Target | Notes |
|---|---|---|
| `SharedPrefsModule.kt` | `data/repository/SettingsRepository.kt` | Extract every SharedPreferences key name used here and cross-check it against key names referenced inside the enforcement/ files above. A key name that doesn't match anywhere is a real inconsistency — flag it. This file matters most of the ten you'll convert: every other repository's persistence assumptions trace back to the key names you establish here. **Also port `publishFocusSnapshotInternal(...)`** — a PIN-free variant of `publishFocusSnapshot` added during the bug-fix pass, used by authorized system paths (task completion, skip, orphaned-session cleanup) that must not be blocked by a session PIN. Port both methods, don't merge them into one. |

### Report for Prompt 1
```
| Source file | Target file | Public methods (old → new signature) | Flags |
|---|---|---|---|
```

---

## PROMPT 2 of 7 — ForegroundServiceModule.kt and ForegroundLaunchModule.kt

Same rules, read-first gate, and working-memory discipline as Prompt 1.

| Source | Target | Notes |
|---|---|---|
| `ForegroundServiceModule.kt` | `data/repository/ForegroundServiceController.kt` | Thin wrapper — sends Intents to the real `ForegroundTaskService`. Read its `ACTION_*` constants directly from the file, don't guess them. **Also port `stopServiceInternal()`** — a PIN-free variant of `stopService` added during the bug-fix pass, for the same authorized-path reason as `publishFocusSnapshotInternal` above. Port both methods. |
| `ForegroundLaunchModule.kt` | fold into `data/repository/LauncherController.kt` | Controls the real `LauncherActivity`. |

### Report for Prompt 2
```
| Source file | Target file | Public methods (old → new signature) | Flags |
|---|---|---|---|
```

---

## PROMPT 3 of 7 — BlockOverlayModule.kt, AversionsModule.kt, NuclearModeModule.kt

Same rules as Prompt 1. All three of these wrap files Gemini already relocated —
re-read the specific one each depends on before writing its controller.

| Source | Target | Notes |
|---|---|---|
| `BlockOverlayModule.kt` | `data/repository/BlockOverlayController.kt` | Check `Settings.canDrawOverlays()` before launching, exactly as the source does. Don't drop this check. |
| `AversionsModule.kt` | `data/repository/AversionsController.kt` | Calls into the real `AversiveActionsManager.kt`. |
| `NuclearModeModule.kt` | `data/repository/NuclearModeRepository.kt` | Calls into the real `FocusDayDeviceAdminReceiver.kt`. |

### Report for Prompt 3
```
| Source file | Target file | Public methods (old → new signature) | Flags |
|---|---|---|---|
```

---

## PROMPT 4 of 7 — InstalledAppsModule.kt, GreyoutModule.kt, NativeImagePickerModule.kt, NativeFilePickerModule.kt

Same rules as Prompt 1. None of these four call into a Gemini-relocated file.

| Source | Target | Notes |
|---|---|---|
| `InstalledAppsModule.kt` | `data/repository/InstalledAppsRepository.kt` | Return `Drawable`/`Bitmap` directly from `PackageManager` instead of base64-encoding icons — that encoding only existed because of the JS bridge and isn't needed now. Note this simplification explicitly in your report; it's deliberate, not scope creep. |
| `GreyoutModule.kt` | `data/repository/GreyoutRepository.kt` | Writes SharedPreferences keys that `AppBlockerAccessibilityService` reads — get the key names from Prompt 1's `SettingsRepository` work, don't invent new ones. **This module also owns the temptation/blocking log now** — port `getTemptationLog()`, `clearTemptationLog()`, and `getWeeklySummary()` alongside the greyout-schedule methods. This is not optional: the entire analytics engine (Track D, Stage 3) reads blocking data through `GreyoutRepository.getTemptationLog()` — if these three methods are missing, the analytics engine cannot compile. |
| `NativeImagePickerModule.kt` | no repository file — write a short note describing the `rememberLauncherForActivityResult` contract to use instead | |
| `NativeFilePickerModule.kt` | same as above | |

### Report for Prompt 4
```
| Source file | Target file (or "n/a — noted pattern") | Public methods (old → new signature) | Flags |
|---|---|---|---|
```

---

## PROMPT 5 of 7 — schedulerEngine.ts and backgroundTasks.ts

Same rules as Prompt 1, but this pair is different in kind — pure business logic, not
Android bridge modules. No `@ReactMethod`/`Promise` scaffolding to strip.

### Source
```
artifacts/focusflow/src/services/schedulerEngine.ts
artifacts/focusflow/src/tasks/backgroundTasks.ts
```

| Source | Target | Notes |
|---|---|---|
| `schedulerEngine.ts` | `domain/SchedulerEngine.kt` | Pure algorithm (task conflict detection/rebalancing), zero Android dependency. Direct, literal port — syntax translation only. Keep it unit-testable in isolation exactly as it is today. |
| `backgroundTasks.ts` | `background/BackgroundFetchWorker.kt` | Only the `BACKGROUND_FETCH` job survives, as a WorkManager `PeriodicWorkRequest` with a 15-minute interval (Android's own OS-enforced floor). `OVERRUN_CHECK` and `NOTIFICATION_BG` are dead code here — already duplicated by `TaskEndAlarmReceiver`/`TaskAlarmActivity` and `NotificationActionReceiver`, both real files from Gemini's stage. Don't port them; note in your report that you deliberately dropped them and why. |

### Report for Prompt 5
```
| Source file | Target file | Notes on what was ported / deliberately dropped | Flags |
|---|---|---|---|
```

---

## PROMPT 6 of 7 — notificationService.ts and backupService.ts

Same rules as Prompt 1.

### Source
```
artifacts/focusflow/src/services/notificationService.ts
artifacts/focusflow/src/services/backupService.ts
```

| Source | Target | Notes |
|---|---|---|
| `notificationService.ts` | `notifications/NotificationChannels.kt` + `notifications/NotificationRepository.kt` | Three channels: task-reminders (HIGH), morning-digest (DEFAULT), weekly-report (DEFAULT). |
| `backupService.ts` | `data/repository/BackupManager.kt` | Use Storage Access Framework (`ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`). The exported JSON **shape must stay identical** to what this file currently produces — existing users have backup files from the old app in Drive or Downloads, and those need to still import correctly after this migration. This is a backward-compatibility requirement, not a style preference. |

### Report for Prompt 6
```
| Source file | Target file(s) | Public methods (old → new signature) | Flags |
|---|---|---|---|
```

---

## PROMPT 7 of 7 — One consolidated final report

Pull together everything from Prompts 1–6 into one table, so this stage hands off a
single clean document instead of six scattered ones.

```
| Source file | Target file(s) | Public methods (old → new signature) | Flags |
|---|---|---|---|
```
Include all ten modules, the two supporting-logic files, and the two documented
picker patterns. List every flag raised across all six prompts underneath, even ones
already mentioned — this is the version that gets handed to Claude for Stage 3, and
it should be complete on its own.
